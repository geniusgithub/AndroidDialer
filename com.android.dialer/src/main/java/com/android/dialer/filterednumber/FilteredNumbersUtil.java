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
package com.android.dialer.filterednumber;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.widget.Toast;

import com.android.contacts.common.testing.NeededForTesting;
import com.android.dialer.R;
import com.android.dialer.compat.FilteredNumberCompat;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler.OnHasBlockedNumbersListener;
import com.android.dialer.database.FilteredNumberContract.FilteredNumber;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberColumns;
import com.android.dialer.logging.InteractionEvent;
import com.android.dialer.logging.Logger;

import java.util.concurrent.TimeUnit;

/**
 * Utility to help with tasks related to filtered numbers.
 */
public class FilteredNumbersUtil {

    // Disable incoming call blocking if there was a call within the past 2 days.
    private static final long RECENT_EMERGENCY_CALL_THRESHOLD_MS = 1000 * 60 * 60 * 24 * 2;

    // Pref key for storing the time of end of the last emergency call in milliseconds after epoch.
    protected static final String LAST_EMERGENCY_CALL_MS_PREF_KEY = "last_emergency_call_ms";

    // Pref key for storing whether a notification has been dispatched to notify the user that call
    // blocking has been disabled because of a recent emergency call.
    protected static final String NOTIFIED_CALL_BLOCKING_DISABLED_BY_EMERGENCY_CALL_PREF_KEY =
            "notified_call_blocking_disabled_by_emergency_call";

    public static final String CALL_BLOCKING_NOTIFICATION_TAG = "call_blocking";
    public static final int CALL_BLOCKING_DISABLED_BY_EMERGENCY_CALL_NOTIFICATION_ID = 10;

    /**
     * Used for testing to specify that a custom threshold should be used instead of the default.
     * This custom threshold will only be used when setting this log tag to VERBOSE:
     *
     *     adb shell setprop log.tag.DebugEmergencyCall VERBOSE
     *
     */
    @NeededForTesting
    private static final String DEBUG_EMERGENCY_CALL_TAG = "DebugEmergencyCall";

    /**
     * Used for testing to specify the custom threshold value, in milliseconds for whether an
     * emergency call is "recent". The default value will be used if this custom threshold is less
     * than zero. For example, to set this threshold to 60 seconds:
     *
     *     adb shell settings put system dialer_emergency_call_threshold_ms 60000
     *
     */
    @NeededForTesting
    private static final String RECENT_EMERGENCY_CALL_THRESHOLD_SETTINGS_KEY =
            "dialer_emergency_call_threshold_ms";

    public interface CheckForSendToVoicemailContactListener {
        public void onComplete(boolean hasSendToVoicemailContact);
    }

    public interface ImportSendToVoicemailContactsListener {
        public void onImportComplete();
    }

    private static class ContactsQuery {
        static final String[] PROJECTION = {
            Contacts._ID
        };

        static final String SELECT_SEND_TO_VOICEMAIL_TRUE = Contacts.SEND_TO_VOICEMAIL + "=1";

        static final int ID_COLUMN_INDEX = 0;
    }

    public static class PhoneQuery {
        static final String[] PROJECTION = {
            Contacts._ID,
            Phone.NORMALIZED_NUMBER,
            Phone.NUMBER
        };

        static final int ID_COLUMN_INDEX = 0;
        static final int NORMALIZED_NUMBER_COLUMN_INDEX = 1;
        static final int NUMBER_COLUMN_INDEX = 2;

        static final String SELECT_SEND_TO_VOICEMAIL_TRUE = Contacts.SEND_TO_VOICEMAIL + "=1";
    }

    /**
     * Checks if there exists a contact with {@code Contacts.SEND_TO_VOICEMAIL} set to true.
     */
    public static void checkForSendToVoicemailContact(
            final Context context, final CheckForSendToVoicemailContactListener listener) {
        final AsyncTask task = new AsyncTask<Object, Void, Boolean>() {
            @Override
            public Boolean doInBackground(Object[]  params) {
                if (context == null) {
                    return false;
                }

                final Cursor cursor = context.getContentResolver().query(
                        Contacts.CONTENT_URI,
                        ContactsQuery.PROJECTION,
                        ContactsQuery.SELECT_SEND_TO_VOICEMAIL_TRUE,
                        null,
                        null);

                boolean hasSendToVoicemailContacts = false;
                if (cursor != null) {
                    try {
                        hasSendToVoicemailContacts = cursor.getCount() > 0;
                    } finally {
                        cursor.close();
                    }
                }

                return hasSendToVoicemailContacts;
            }

            @Override
            public void onPostExecute(Boolean hasSendToVoicemailContact) {
                if (listener != null) {
                    listener.onComplete(hasSendToVoicemailContact);
                }
            }
        };
        task.execute();
    }

    /**
     * Blocks all the phone numbers of any contacts marked as SEND_TO_VOICEMAIL, then clears the
     * SEND_TO_VOICEMAIL flag on those contacts.
     */
    public static void importSendToVoicemailContacts(
            final Context context, final ImportSendToVoicemailContactsListener listener) {
        Logger.logInteraction(InteractionEvent.IMPORT_SEND_TO_VOICEMAIL);
        final FilteredNumberAsyncQueryHandler mFilteredNumberAsyncQueryHandler =
                new FilteredNumberAsyncQueryHandler(context.getContentResolver());

        final AsyncTask<Object, Void, Boolean> task = new AsyncTask<Object, Void, Boolean>() {
            @Override
            public Boolean doInBackground(Object[] params) {
                if (context == null) {
                    return false;
                }

                // Get the phone number of contacts marked as SEND_TO_VOICEMAIL.
                final Cursor phoneCursor = context.getContentResolver().query(
                        Phone.CONTENT_URI,
                        PhoneQuery.PROJECTION,
                        PhoneQuery.SELECT_SEND_TO_VOICEMAIL_TRUE,
                        null,
                        null);

                if (phoneCursor == null) {
                    return false;
                }

                try {
                    while (phoneCursor.moveToNext()) {
                        final String normalizedNumber = phoneCursor.getString(
                                PhoneQuery.NORMALIZED_NUMBER_COLUMN_INDEX);
                        final String number = phoneCursor.getString(
                                PhoneQuery.NUMBER_COLUMN_INDEX);
                        if (normalizedNumber != null) {
                            // Block the phone number of the contact.
                            mFilteredNumberAsyncQueryHandler.blockNumber(
                                    null, normalizedNumber, number, null);
                        }
                    }
                } finally {
                    phoneCursor.close();
                }

                // Clear SEND_TO_VOICEMAIL on all contacts. The setting has been imported to Dialer.
                ContentValues newValues = new ContentValues();
                newValues.put(Contacts.SEND_TO_VOICEMAIL, 0);
                context.getContentResolver().update(
                        Contacts.CONTENT_URI,
                        newValues,
                        ContactsQuery.SELECT_SEND_TO_VOICEMAIL_TRUE,
                        null);

                return true;
            }

            @Override
            public void onPostExecute(Boolean success) {
                if (success) {
                    if (listener != null) {
                        listener.onImportComplete();
                    }
                } else if (context != null) {
                    String toastStr = context.getString(R.string.send_to_voicemail_import_failed);
                    Toast.makeText(context, toastStr, Toast.LENGTH_SHORT).show();
                }
            }
        };
        task.execute();
    }

     /**
     * WARNING: This method should NOT be executed on the UI thread.
     * Use {@code FilteredNumberAsyncQueryHandler} to asynchronously check if a number is blocked.
     */
    public static boolean shouldBlockVoicemail(
            Context context, String number, String countryIso, long voicemailDateMs) {
        final String normalizedNumber = PhoneNumberUtils.formatNumberToE164(number, countryIso);
        if (TextUtils.isEmpty(normalizedNumber)) {
            return false;
        }

        if (hasRecentEmergencyCall(context)) {
            return false;
        }

        final Cursor cursor = context.getContentResolver().query(
                FilteredNumber.CONTENT_URI,
                new String[] {
                    FilteredNumberColumns.CREATION_TIME
                },
                FilteredNumberColumns.NORMALIZED_NUMBER + "=?",
                new String[] { normalizedNumber },
                null);
        if (cursor == null) {
            return false;
        }
        try {
                /*
                 * Block if number is found and it was added before this voicemail was received.
                 * The VVM's date is reported with precision to the minute, even though its
                 * magnitude is in milliseconds, so we perform the comparison in minutes.
                 */
                return cursor.moveToFirst() &&
                        TimeUnit.MINUTES.convert(voicemailDateMs, TimeUnit.MILLISECONDS) >=
                                TimeUnit.MINUTES.convert(cursor.getLong(0), TimeUnit.MILLISECONDS);
        } finally {
            cursor.close();
        }
    }

    public static boolean hasRecentEmergencyCall(Context context) {
        if (context == null) {
            return false;
        }

        Long lastEmergencyCallTime = PreferenceManager.getDefaultSharedPreferences(context)
                .getLong(LAST_EMERGENCY_CALL_MS_PREF_KEY, 0);
        if (lastEmergencyCallTime == 0) {
            return false;
        }

        return (System.currentTimeMillis() - lastEmergencyCallTime)
                < getRecentEmergencyCallThresholdMs(context);
    }

    public static void recordLastEmergencyCallTime(Context context) {
        if (context == null) {
            return;
        }

        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putLong(LAST_EMERGENCY_CALL_MS_PREF_KEY, System.currentTimeMillis())
                .putBoolean(NOTIFIED_CALL_BLOCKING_DISABLED_BY_EMERGENCY_CALL_PREF_KEY, false)
                .apply();

        maybeNotifyCallBlockingDisabled(context);
    }

    public static void maybeNotifyCallBlockingDisabled(final Context context) {
        // The Dialer is not responsible for this notification after migrating
        if (FilteredNumberCompat.useNewFiltering()) {
            return;
        }
        // Skip if the user has already received a notification for the most recent emergency call.
        if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(NOTIFIED_CALL_BLOCKING_DISABLED_BY_EMERGENCY_CALL_PREF_KEY, false)) {
            return;
        }

        // If the user has blocked numbers, notify that call blocking is temporarily disabled.
        FilteredNumberAsyncQueryHandler queryHandler =
                new FilteredNumberAsyncQueryHandler(context.getContentResolver());
        queryHandler.hasBlockedNumbers(new OnHasBlockedNumbersListener() {
            @Override
            public void onHasBlockedNumbers(boolean hasBlockedNumbers) {
                if (context == null || !hasBlockedNumbers) {
                    return;
                }

                NotificationManager notificationManager = (NotificationManager)
                        context.getSystemService(Context.NOTIFICATION_SERVICE);
                Notification.Builder builder = new Notification.Builder(context)
                        .setSmallIcon(R.drawable.ic_block_24dp)
                        .setContentTitle(context.getString(
                                R.string.call_blocking_disabled_notification_title))
                        .setContentText(context.getString(
                                R.string.call_blocking_disabled_notification_text))
                        .setAutoCancel(true);

                final Intent contentIntent =
                        new Intent(context, BlockedNumbersSettingsActivity.class);
                builder.setContentIntent(PendingIntent.getActivity(
                        context, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT));

                notificationManager.notify(
                        CALL_BLOCKING_NOTIFICATION_TAG,
                        CALL_BLOCKING_DISABLED_BY_EMERGENCY_CALL_NOTIFICATION_ID,
                        builder.build());

                // Record that the user has been notified for this emergency call.
                PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .putBoolean(NOTIFIED_CALL_BLOCKING_DISABLED_BY_EMERGENCY_CALL_PREF_KEY, true)
                    .apply();
            }
        });
    }

    public static boolean canBlockNumber(Context context, String number, String countryIso) {
        final String normalizedNumber = PhoneNumberUtils.formatNumberToE164(number, countryIso);
        return !TextUtils.isEmpty(normalizedNumber)
                && !PhoneNumberUtils.isEmergencyNumber(normalizedNumber);
    }

    private static long getRecentEmergencyCallThresholdMs(Context context) {
        if (android.util.Log.isLoggable(
                DEBUG_EMERGENCY_CALL_TAG, android.util.Log.VERBOSE)) {
            long thresholdMs = Settings.System.getLong(
                    context.getContentResolver(),
                    RECENT_EMERGENCY_CALL_THRESHOLD_SETTINGS_KEY, 0);
            return thresholdMs > 0 ? thresholdMs : RECENT_EMERGENCY_CALL_THRESHOLD_MS;
        } else {
            return RECENT_EMERGENCY_CALL_THRESHOLD_MS;
        }
    }
}
