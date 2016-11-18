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
 * limitations under the License.
 */

package com.android.dialer.calllog;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.android.contacts.common.util.PermissionsUtil;
import com.android.dialer.util.TelecomUtil;

/**
 * Provides operations for managing call-related notifications.
 * <p>
 * It handles the following actions:
 * <ul>
 * <li>Updating voicemail notifications</li>
 * <li>Marking new voicemails as old</li>
 * <li>Updating missed call notifications</li>
 * <li>Marking new missed calls as old</li>
 * <li>Calling back from a missed call</li>
 * <li>Sending an SMS from a missed call</li>
 * </ul>
 */
public class CallLogNotificationsService extends IntentService {
    private static final String TAG = "CallLogNotificationsService";

    /** Action to mark all the new voicemails as old. */
    public static final String ACTION_MARK_NEW_VOICEMAILS_AS_OLD =
            "com.android.dialer.calllog.ACTION_MARK_NEW_VOICEMAILS_AS_OLD";

    /**
     * Action to update voicemail notifications.
     * <p>
     * May include an optional extra {@link #EXTRA_NEW_VOICEMAIL_URI}.
     */
    public static final String ACTION_UPDATE_VOICEMAIL_NOTIFICATIONS =
            "com.android.dialer.calllog.UPDATE_VOICEMAIL_NOTIFICATIONS";

    /**
     * Extra to included with {@link #ACTION_UPDATE_VOICEMAIL_NOTIFICATIONS} to identify the new
     * voicemail that triggered an update.
     * <p>
     * It must be a {@link Uri}.
     */
    public static final String EXTRA_NEW_VOICEMAIL_URI = "NEW_VOICEMAIL_URI";

    /**
     * Action to update the missed call notifications.
     * <p>
     * Includes optional extras {@link #EXTRA_MISSED_CALL_NUMBER} and
     * {@link #EXTRA_MISSED_CALL_COUNT}.
     */
    public static final String ACTION_UPDATE_MISSED_CALL_NOTIFICATIONS =
            "com.android.dialer.calllog.UPDATE_MISSED_CALL_NOTIFICATIONS";

    /** Action to mark all the new missed calls as old. */
    public static final String ACTION_MARK_NEW_MISSED_CALLS_AS_OLD =
            "com.android.dialer.calllog.ACTION_MARK_NEW_MISSED_CALLS_AS_OLD";

    /** Action to call back a missed call. */
    public static final String ACTION_CALL_BACK_FROM_MISSED_CALL_NOTIFICATION =
            "com.android.dialer.calllog.CALL_BACK_FROM_MISSED_CALL_NOTIFICATION";

    public static final String ACTION_SEND_SMS_FROM_MISSED_CALL_NOTIFICATION =
            "com.android.dialer.calllog.SEND_SMS_FROM_MISSED_CALL_NOTIFICATION";

    /**
     * Extra to be included with {@link #ACTION_UPDATE_MISSED_CALL_NOTIFICATIONS},
     * {@link #ACTION_SEND_SMS_FROM_MISSED_CALL_NOTIFICATION} and
     * {@link #ACTION_CALL_BACK_FROM_MISSED_CALL_NOTIFICATION} to identify the number to display,
     * call or text back.
     * <p>
     * It must be a {@link String}.
     */
    public static final String EXTRA_MISSED_CALL_NUMBER = "MISSED_CALL_NUMBER";

    /**
     * Extra to be included with {@link #ACTION_UPDATE_MISSED_CALL_NOTIFICATIONS} to represent the
     * number of missed calls.
     * <p>
     * It must be a {@link Integer}
     */
    public static final String EXTRA_MISSED_CALL_COUNT =
            "MISSED_CALL_COUNT";

    public static final int UNKNOWN_MISSED_CALL_COUNT = -1;

    private VoicemailQueryHandler mVoicemailQueryHandler;

    public CallLogNotificationsService() {
        super("CallLogNotificationsService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            Log.d(TAG, "onHandleIntent: could not handle null intent");
            return;
        }

        if (!PermissionsUtil.hasPermission(this, android.Manifest.permission.READ_CALL_LOG)) {
            return;
        }

        String action = intent.getAction();
        switch (action) {
            case ACTION_MARK_NEW_VOICEMAILS_AS_OLD:
                if (mVoicemailQueryHandler == null) {
                    mVoicemailQueryHandler = new VoicemailQueryHandler(this, getContentResolver());
                }
                mVoicemailQueryHandler.markNewVoicemailsAsOld();
                break;
            case ACTION_UPDATE_VOICEMAIL_NOTIFICATIONS:
                Uri voicemailUri = (Uri) intent.getParcelableExtra(EXTRA_NEW_VOICEMAIL_URI);
                DefaultVoicemailNotifier.getInstance(this).updateNotification(voicemailUri);
                break;
            case ACTION_UPDATE_MISSED_CALL_NOTIFICATIONS:
                int count = intent.getIntExtra(EXTRA_MISSED_CALL_COUNT,
                        UNKNOWN_MISSED_CALL_COUNT);
                String number = intent.getStringExtra(EXTRA_MISSED_CALL_NUMBER);
                MissedCallNotifier.getInstance(this).updateMissedCallNotification(count, number);
                break;
            case ACTION_MARK_NEW_MISSED_CALLS_AS_OLD:
                CallLogNotificationsHelper.removeMissedCallNotifications(this);
                break;
            case ACTION_CALL_BACK_FROM_MISSED_CALL_NOTIFICATION:
                MissedCallNotifier.getInstance(this).callBackFromMissedCall(
                        intent.getStringExtra(EXTRA_MISSED_CALL_NUMBER));
                break;
            case ACTION_SEND_SMS_FROM_MISSED_CALL_NOTIFICATION:
                MissedCallNotifier.getInstance(this).sendSmsFromMissedCall(
                        intent.getStringExtra(EXTRA_MISSED_CALL_NUMBER));
                break;
            default:
                Log.d(TAG, "onHandleIntent: could not handle: " + intent);
                break;
        }
    }

    /**
     * Updates notifications for any new voicemails.
     *
     * @param context a valid context.
     * @param voicemailUri The uri pointing to the voicemail to update the notification for. If
     *         {@code null}, then notifications for all new voicemails will be updated.
     */
    public static void updateVoicemailNotifications(Context context, Uri voicemailUri) {
        if (TelecomUtil.hasReadWriteVoicemailPermissions(context)) {
            Intent serviceIntent = new Intent(context, CallLogNotificationsService.class);
            serviceIntent.setAction(
                    CallLogNotificationsService.ACTION_UPDATE_VOICEMAIL_NOTIFICATIONS);
            // If voicemailUri is null, then notifications for all voicemails will be updated.
            if (voicemailUri != null) {
                serviceIntent.putExtra(
                        CallLogNotificationsService.EXTRA_NEW_VOICEMAIL_URI, voicemailUri);
            }
            context.startService(serviceIntent);
        }
    }

    /**
     * Updates notifications for any new missed calls.
     *
     * @param context A valid context.
     * @param count The number of new missed calls.
     * @param number The phone number of the newest missed call.
     */
    public static void updateMissedCallNotifications(Context context, int count,
            String number) {
        Intent serviceIntent = new Intent(context, CallLogNotificationsService.class);
        serviceIntent.setAction(
                CallLogNotificationsService.ACTION_UPDATE_MISSED_CALL_NOTIFICATIONS);
        serviceIntent.putExtra(EXTRA_MISSED_CALL_COUNT, count);
        serviceIntent.putExtra(EXTRA_MISSED_CALL_NUMBER, number);
        context.startService(serviceIntent);
    }
}
