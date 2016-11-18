/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.dialer.calllog;

import com.google.common.collect.Maps;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.compat.TelephonyManagerCompat;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.calllog.CallLogNotificationsHelper.NewCall;
import com.android.dialer.filterednumber.FilteredNumbersUtil;
import com.android.dialer.list.ListsFragment;
import com.android.dialer.util.TelecomUtil;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Shows a voicemail notification in the status bar.
 */
public class DefaultVoicemailNotifier {
    public static final String TAG = "VoicemailNotifier";

    /** The tag used to identify notifications from this class. */
    private static final String NOTIFICATION_TAG = "DefaultVoicemailNotifier";
    /** The identifier of the notification of new voicemails. */
    private static final int NOTIFICATION_ID = 1;

    /** The singleton instance of {@link DefaultVoicemailNotifier}. */
    private static DefaultVoicemailNotifier sInstance;

    private final Context mContext;

    /** Returns the singleton instance of the {@link DefaultVoicemailNotifier}. */
    public static DefaultVoicemailNotifier getInstance(Context context) {
        if (sInstance == null) {
            ContentResolver contentResolver = context.getContentResolver();
            sInstance = new DefaultVoicemailNotifier(context);
        }
        return sInstance;
    }

    private DefaultVoicemailNotifier(Context context) {
        mContext = context;
    }

    /**
     * Updates the notification and notifies of the call with the given URI.
     *
     * Clears the notification if there are no new voicemails, and notifies if the given URI
     * corresponds to a new voicemail.
     *
     * It is not safe to call this method from the main thread.
     */
    public void updateNotification(Uri newCallUri) {
        // Lookup the list of new voicemails to include in the notification.
        // TODO: Move this into a service, to avoid holding the receiver up.
        final List<NewCall> newCalls =
                CallLogNotificationsHelper.getInstance(mContext).getNewVoicemails();

        if (newCalls == null) {
            // Query failed, just return.
            return;
        }

        if (newCalls.isEmpty()) {
            // No voicemails to notify about: clear the notification.
            getNotificationManager().cancel(NOTIFICATION_TAG, NOTIFICATION_ID);
            return;
        }

        Resources resources = mContext.getResources();

        // This represents a list of names to include in the notification.
        String callers = null;

        // Maps each number into a name: if a number is in the map, it has already left a more
        // recent voicemail.
        final Map<String, String> names = Maps.newHashMap();

        // Determine the call corresponding to the new voicemail we have to notify about.
        NewCall callToNotify = null;

        // Iterate over the new voicemails to determine all the information above.
        Iterator<NewCall> itr = newCalls.iterator();
        while (itr.hasNext()) {
            NewCall newCall = itr.next();

            // Skip notifying for numbers which are blocked.
            if (FilteredNumbersUtil.shouldBlockVoicemail(
                    mContext, newCall.number, newCall.countryIso, newCall.dateMs)) {
                itr.remove();

                // Delete the voicemail.
                mContext.getContentResolver().delete(newCall.voicemailUri, null, null);
                continue;
            }

            // Check if we already know the name associated with this number.
            String name = names.get(newCall.number);
            if (name == null) {
                name = CallLogNotificationsHelper.getInstance(mContext).getName(newCall.number,
                        newCall.numberPresentation, newCall.countryIso);
                names.put(newCall.number, name);
                // This is a new caller. Add it to the back of the list of callers.
                if (TextUtils.isEmpty(callers)) {
                    callers = name;
                } else {
                    callers = resources.getString(
                            R.string.notification_voicemail_callers_list, callers, name);
                }
            }
            // Check if this is the new call we need to notify about.
            if (newCallUri != null && newCall.voicemailUri != null &&
                    ContentUris.parseId(newCallUri) == ContentUris.parseId(newCall.voicemailUri)) {
                callToNotify = newCall;
            }
        }

        // All the potential new voicemails have been removed, e.g. if they were spam.
        if (newCalls.isEmpty()) {
            return;
        }

        // If there is only one voicemail, set its transcription as the "long text".
        String transcription = null;
        if (newCalls.size() == 1) {
            transcription = newCalls.get(0).transcription;
        }

        if (newCallUri != null && callToNotify == null) {
            Log.e(TAG, "The new call could not be found in the call log: " + newCallUri);
        }

        // Determine the title of the notification and the icon for it.
        final String title = resources.getQuantityString(
                R.plurals.notification_voicemail_title, newCalls.size(), newCalls.size());
        // TODO: Use the photo of contact if all calls are from the same person.
        final int icon = android.R.drawable.stat_notify_voicemail;

        Pair<Uri, Integer> info = getNotificationInfo(callToNotify);

        Notification.Builder notificationBuilder = new Notification.Builder(mContext)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(callers)
                .setStyle(new Notification.BigTextStyle().bigText(transcription))
                .setColor(resources.getColor(R.color.dialer_theme_color))
                .setSound(info.first)
                .setDefaults(info.second)
                .setDeleteIntent(createMarkNewVoicemailsAsOldIntent())
                .setAutoCancel(true);

        // Determine the intent to fire when the notification is clicked on.
        final Intent contentIntent;
        // Open the call log.
        contentIntent = new Intent(mContext, DialtactsActivity.class);
        contentIntent.putExtra(DialtactsActivity.EXTRA_SHOW_TAB, ListsFragment.TAB_INDEX_VOICEMAIL);
        notificationBuilder.setContentIntent(PendingIntent.getActivity(
                mContext, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT));

        // The text to show in the ticker, describing the new event.
        if (callToNotify != null) {
            CharSequence msg = ContactDisplayUtils.getTtsSpannedPhoneNumber(
                    resources,
                    R.string.notification_new_voicemail_ticker,
                    names.get(callToNotify.number));
            notificationBuilder.setTicker(msg);
        }
        Log.i(TAG, "Creating voicemail notification");
        getNotificationManager().notify(NOTIFICATION_TAG, NOTIFICATION_ID,
                notificationBuilder.build());
    }

    /**
     * Determines which ringtone Uri and Notification defaults to use when updating the notification
     * for the given call.
     */
    private Pair<Uri, Integer> getNotificationInfo(@Nullable NewCall callToNotify) {
        Log.v(TAG, "getNotificationInfo");
        if (callToNotify == null) {
            Log.i(TAG, "callToNotify == null");
            return new Pair<>(null, 0);
        }
        PhoneAccountHandle accountHandle = null;
        if (callToNotify.accountComponentName == null || callToNotify.accountId == null) {
            Log.v(TAG, "accountComponentName == null || callToNotify.accountId == null");
            accountHandle = TelecomUtil
                .getDefaultOutgoingPhoneAccount(mContext, PhoneAccount.SCHEME_TEL);
            if (accountHandle == null) {
                Log.i(TAG, "No default phone account found, using default notification ringtone");
                return new Pair<>(null, Notification.DEFAULT_ALL);
            }

        } else {
            accountHandle = new PhoneAccountHandle(
                ComponentName.unflattenFromString(callToNotify.accountComponentName),
                callToNotify.accountId);
        }
        if (accountHandle.getComponentName() != null) {
            Log.v(TAG, "PhoneAccountHandle.ComponentInfo:" + accountHandle.getComponentName());
        } else {
            Log.i(TAG, "PhoneAccountHandle.ComponentInfo: null");
        }
        return new Pair<>(
                TelephonyManagerCompat.getVoicemailRingtoneUri(
                        getTelephonyManager(), accountHandle),
                getNotificationDefaults(accountHandle));
    }

    private int getNotificationDefaults(PhoneAccountHandle accountHandle) {
        if (ContactsUtils.FLAG_N_FEATURE) {
            return TelephonyManagerCompat.isVoicemailVibrationEnabled(getTelephonyManager(),
                    accountHandle) ? Notification.DEFAULT_VIBRATE : 0;
        }
        return Notification.DEFAULT_ALL;
    }

    /** Creates a pending intent that marks all new voicemails as old. */
    private PendingIntent createMarkNewVoicemailsAsOldIntent() {
        Intent intent = new Intent(mContext, CallLogNotificationsService.class);
        intent.setAction(CallLogNotificationsService.ACTION_MARK_NEW_VOICEMAILS_AS_OLD);
        return PendingIntent.getService(mContext, 0, intent, 0);
    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private TelephonyManager getTelephonyManager() {
        return (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
    }

}
