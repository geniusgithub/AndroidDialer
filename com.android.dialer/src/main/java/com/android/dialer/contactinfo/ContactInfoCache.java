/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dialer.contactinfo;

import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;

import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.util.ExpirableCache;
import com.google.common.annotations.VisibleForTesting;

import java.util.LinkedList;

/**
 * This is a cache of contact details for the phone numbers in the c all log. The key is the
 * phone number with the country in which teh call was placed or received. The content of the
 * cache is expired (but not purged) whenever the application comes to the foreground.
 *
 * This cache queues request for information and queries for information on a background thread,
 * so {@code start()} and {@code stop()} must be called to initiate or halt that thread's exeuction
 * as needed.
 *
 * TODO: Explore whether there is a pattern to remove external dependencies for starting and
 * stopping the query thread.
 */
public class ContactInfoCache {
    public interface OnContactInfoChangedListener {
        public void onContactInfoChanged();
    }

    /*
     * Handles requests for contact name and number type.
     */
    private class QueryThread extends Thread {
        private volatile boolean mDone = false;

        public QueryThread() {
            super("ContactInfoCache.QueryThread");
        }

        public void stopProcessing() {
            mDone = true;
        }

        @Override
        public void run() {
            boolean needRedraw = false;
            while (true) {
                // Check if thread is finished, and if so return immediately.
                if (mDone) return;

                // Obtain next request, if any is available.
                // Keep synchronized section small.
                ContactInfoRequest req = null;
                synchronized (mRequests) {
                    if (!mRequests.isEmpty()) {
                        req = mRequests.removeFirst();
                    }
                }

                if (req != null) {
                    // Process the request. If the lookup succeeds, schedule a redraw.
                    needRedraw |= queryContactInfo(req.number, req.countryIso, req.callLogInfo);
                } else {
                    // Throttle redraw rate by only sending them when there are
                    // more requests.
                    if (needRedraw) {
                        needRedraw = false;
                        mHandler.sendEmptyMessage(REDRAW);
                    }

                    // Wait until another request is available, or until this
                    // thread is no longer needed (as indicated by being
                    // interrupted).
                    try {
                        synchronized (mRequests) {
                            mRequests.wait(1000);
                        }
                    } catch (InterruptedException ie) {
                        // Ignore, and attempt to continue processing requests.
                    }
                }
            }
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REDRAW:
                    mOnContactInfoChangedListener.onContactInfoChanged();
                    break;
                case START_THREAD:
                    startRequestProcessing();
                    break;
            }
        }
    };

    private static final int REDRAW = 1;
    private static final int START_THREAD = 2;

    private static final int CONTACT_INFO_CACHE_SIZE = 100;
    private static final int START_PROCESSING_REQUESTS_DELAY_MS = 1000;


    /**
     * List of requests to update contact details. Each request contains a phone number to look up,
     * and the contact info currently stored in the call log for this number.
     *
     * The requests are added when displaying contacts and are processed by a background thread.
     */
    private final LinkedList<ContactInfoRequest> mRequests;

    private ExpirableCache<NumberWithCountryIso, ContactInfo> mCache;

    private ContactInfoHelper mContactInfoHelper;
    private QueryThread mContactInfoQueryThread;
    private OnContactInfoChangedListener mOnContactInfoChangedListener;

    public ContactInfoCache(ContactInfoHelper contactInfoHelper,
            OnContactInfoChangedListener onContactInfoChangedListener) {
        mContactInfoHelper = contactInfoHelper;
        mOnContactInfoChangedListener = onContactInfoChangedListener;

        mRequests = new LinkedList<ContactInfoRequest>();
        mCache = ExpirableCache.create(CONTACT_INFO_CACHE_SIZE);
    }

    public ContactInfo getValue(String number, String countryIso, ContactInfo cachedContactInfo) {
        NumberWithCountryIso numberCountryIso = new NumberWithCountryIso(number, countryIso);
        ExpirableCache.CachedValue<ContactInfo> cachedInfo =
                mCache.getCachedValue(numberCountryIso);
        ContactInfo info = cachedInfo == null ? null : cachedInfo.getValue();
        if (cachedInfo == null) {
            mCache.put(numberCountryIso, ContactInfo.EMPTY);
            // Use the cached contact info from the call log.
            info = cachedContactInfo;
            // The db request should happen on a non-UI thread.
            // Request the contact details immediately since they are currently missing.
            enqueueRequest(number, countryIso, cachedContactInfo, true);
            // We will format the phone number when we make the background request.
        } else {
            if (cachedInfo.isExpired()) {
                // The contact info is no longer up to date, we should request it. However, we
                // do not need to request them immediately.
                enqueueRequest(number, countryIso, cachedContactInfo, false);
            } else if (!callLogInfoMatches(cachedContactInfo, info)) {
                // The call log information does not match the one we have, look it up again.
                // We could simply update the call log directly, but that needs to be done in a
                // background thread, so it is easier to simply request a new lookup, which will, as
                // a side-effect, update the call log.
                enqueueRequest(number, countryIso, cachedContactInfo, false);
            }

            if (info == ContactInfo.EMPTY) {
                // Use the cached contact info from the call log.
                info = cachedContactInfo;
            }
        }
        return info;
    }

    /**
     * Queries the appropriate content provider for the contact associated with the number.
     *
     * Upon completion it also updates the cache in the call log, if it is different from
     * {@code callLogInfo}.
     *
     * The number might be either a SIP address or a phone number.
     *
     * It returns true if it updated the content of the cache and we should therefore tell the
     * view to update its content.
     */
    private boolean queryContactInfo(String number, String countryIso, ContactInfo callLogInfo) {
        final ContactInfo info = mContactInfoHelper.lookupNumber(number, countryIso);

        if (info == null) {
            // The lookup failed, just return without requesting to update the view.
            return false;
        }

        // Check the existing entry in the cache: only if it has changed we should update the
        // view.
        NumberWithCountryIso numberCountryIso = new NumberWithCountryIso(number, countryIso);
        ContactInfo existingInfo = mCache.getPossiblyExpired(numberCountryIso);

        final boolean isRemoteSource = info.sourceType != 0;

        // Don't force redraw if existing info in the cache is equal to {@link ContactInfo#EMPTY}
        // to avoid updating the data set for every new row that is scrolled into view.
        // see (https://googleplex-android-review.git.corp.google.com/#/c/166680/)

        // Exception: Photo uris for contacts from remote sources are not cached in the call log
        // cache, so we have to force a redraw for these contacts regardless.
        boolean updated = (existingInfo != ContactInfo.EMPTY || isRemoteSource) &&
                !info.equals(existingInfo);

        // Store the data in the cache so that the UI thread can use to display it. Store it
        // even if it has not changed so that it is marked as not expired.
        mCache.put(numberCountryIso, info);

        // Update the call log even if the cache it is up-to-date: it is possible that the cache
        // contains the value from a different call log entry.
        mContactInfoHelper.updateCallLogContactInfo(number, countryIso, info, callLogInfo);
        return updated;
    }

    /**
     * After a delay, start the thread to begin processing requests. We perform lookups on a
     * background thread, but this must be called to indicate the thread should be running.
     */
    public void start() {
        // Schedule a thread-creation message if the thread hasn't been created yet, as an
        // optimization to queue fewer messages.
        if (mContactInfoQueryThread == null) {
            // TODO: Check whether this delay before starting to process is necessary.
            mHandler.sendEmptyMessageDelayed(START_THREAD, START_PROCESSING_REQUESTS_DELAY_MS);
        }
    }

    /**
     * Stops the thread and clears the queue of messages to process. This cleans up the thread
     * for lookups so that it is not perpetually running.
     */
    public void stop() {
        stopRequestProcessing();
    }

    /**
     * Starts a background thread to process contact-lookup requests, unless one
     * has already been started.
     */
    private synchronized void startRequestProcessing() {
        // For unit-testing.
        if (mRequestProcessingDisabled) return;

        // If a thread is already started, don't start another.
        if (mContactInfoQueryThread != null) {
            return;
        }

        mContactInfoQueryThread = new QueryThread();
        mContactInfoQueryThread.setPriority(Thread.MIN_PRIORITY);
        mContactInfoQueryThread.start();
    }

    public void invalidate() {
        mCache.expireAll();
        stopRequestProcessing();
    }

    /**
     * Stops the background thread that processes updates and cancels any
     * pending requests to start it.
     */
    private synchronized void stopRequestProcessing() {
        // Remove any pending requests to start the processing thread.
        mHandler.removeMessages(START_THREAD);
        if (mContactInfoQueryThread != null) {
            // Stop the thread; we are finished with it.
            mContactInfoQueryThread.stopProcessing();
            mContactInfoQueryThread.interrupt();
            mContactInfoQueryThread = null;
        }
    }

    /**
     * Enqueues a request to look up the contact details for the given phone number.
     * <p>
     * It also provides the current contact info stored in the call log for this number.
     * <p>
     * If the {@code immediate} parameter is true, it will start immediately the thread that looks
     * up the contact information (if it has not been already started). Otherwise, it will be
     * started with a delay. See {@link #START_PROCESSING_REQUESTS_DELAY_MILLIS}.
     */
    protected void enqueueRequest(String number, String countryIso, ContactInfo callLogInfo,
            boolean immediate) {
        ContactInfoRequest request = new ContactInfoRequest(number, countryIso, callLogInfo);
        synchronized (mRequests) {
            if (!mRequests.contains(request)) {
                mRequests.add(request);
                mRequests.notifyAll();
            }
        }
        if (immediate) {
            startRequestProcessing();
        }
    }

    /**
     * Checks whether the contact info from the call log matches the one from the contacts db.
     */
    private boolean callLogInfoMatches(ContactInfo callLogInfo, ContactInfo info) {
        // The call log only contains a subset of the fields in the contacts db. Only check those.
        return TextUtils.equals(callLogInfo.name, info.name)
                && callLogInfo.type == info.type
                && TextUtils.equals(callLogInfo.label, info.label);
    }

    private volatile boolean mRequestProcessingDisabled = false;

    /**
     * Sets whether processing of requests for contact details should be enabled.
     */
    public void disableRequestProcessing() {
        mRequestProcessingDisabled = true;
    }

    @VisibleForTesting
    public void injectContactInfoForTest(
            String number, String countryIso, ContactInfo contactInfo) {
        NumberWithCountryIso numberCountryIso = new NumberWithCountryIso(number, countryIso);
        mCache.put(numberCountryIso, contactInfo);
    }
}
