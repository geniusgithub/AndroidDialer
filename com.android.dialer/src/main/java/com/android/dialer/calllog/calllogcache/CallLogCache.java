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
 * limitations under the License
 */

package com.android.dialer.calllog.calllogcache;

import android.content.Context;
import android.telecom.PhoneAccountHandle;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.compat.CompatUtils;
import com.android.dialer.calllog.CallLogAdapter;

/**
 * This is the base class for the CallLogCaches.
 *
 * Keeps a cache of recently made queries to the Telecom/Telephony processes. The aim of this cache
 * is to reduce the number of cross-process requests to TelecomManager, which can negatively affect
 * performance.
 *
 * This is designed with the specific use case of the {@link CallLogAdapter} in mind.
 */
public abstract class CallLogCache {
    // TODO: Dialer should be fixed so as not to check isVoicemail() so often but at the time of
    // this writing, that was a much larger undertaking than creating this cache.

    protected final Context mContext;

    private boolean mHasCheckedForVideoEnabled;
    private boolean mIsVideoEnabled;

    public CallLogCache(Context context) {
        mContext = context;
    }

    /**
     * Return the most compatible version of the TelecomCallLogCache.
     */
    public static CallLogCache getCallLogCache(Context context) {
        if (CompatUtils.isClassAvailable("android.telecom.PhoneAccountHandle")) {
            return new CallLogCacheLollipopMr1(context);
        }
        return new CallLogCacheLollipop(context);
    }

    public void reset() {
        mHasCheckedForVideoEnabled = false;
        mIsVideoEnabled = false;
    }

    /**
     * Returns true if the given number is the number of the configured voicemail. To be able to
     * mock-out this, it is not a static method.
     */
    public abstract boolean isVoicemailNumber(PhoneAccountHandle accountHandle,
            CharSequence number);

    public boolean isVideoEnabled() {
        if (!mHasCheckedForVideoEnabled) {
            mIsVideoEnabled = CallUtil.isVideoEnabled(mContext);
            mHasCheckedForVideoEnabled = true;
        }
        return mIsVideoEnabled;
    }

    /**
     * Extract account label from PhoneAccount object.
     */
    public abstract String getAccountLabel(PhoneAccountHandle accountHandle);

    /**
     * Extract account color from PhoneAccount object.
     */
    public abstract int getAccountColor(PhoneAccountHandle accountHandle);

    /**
     * Determines if the PhoneAccount supports specifying a call subject (i.e. calling with a note)
     * for outgoing calls.
     *
     * @param accountHandle The PhoneAccount handle.
     * @return {@code true} if calling with a note is supported, {@code false} otherwise.
     */
    public abstract boolean doesAccountSupportCallSubject(PhoneAccountHandle accountHandle);
}
