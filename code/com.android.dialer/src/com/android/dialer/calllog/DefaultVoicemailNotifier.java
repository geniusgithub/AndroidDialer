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

import static android.Manifest.permission.READ_CALL_LOG;
import static android.Manifest.permission.READ_CONTACTS;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.PhoneLookup;
import android.text.TextUtils;
import android.util.Log;

import com.android.common.io.MoreCloseables;
import com.android.contacts.common.util.PermissionsUtil;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.calllog.PhoneAccountUtils;
import com.android.dialer.list.ListsFragment;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * VoicemailNotifier that shows a notification in the status bar.
 */
public class DefaultVoicemailNotifier {
    public static final String TAG = "DefaultVoicemailNotifier";

    /** The tag used to identify notifications from this class. */
    private static final String NOTIFICATION_TAG = "DefaultVoicemailNotifier";
    /** The identifier of the notification of new voicemails. */
    private static final int NOTIFICATION_ID = 1;

    /** The singleton instance of {@link DefaultVoicemailNotifier}. */
    private static DefaultVoicemailNotifier sInstance;

    private final Context mContext;
    private final NotificationManager mNotificationManager;
    private final NewCallsQuery mNewCallsQuery;
    private final NameLookupQuery mNameLookupQuery;

    /** Returns the singleton instance of the {@link DefaultVoicemailNotifier}. */
    public static synchronized DefaultVoicemailNotifier getInstance(Context context) {
        if (sInstance == null) {
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            ContentResolver contentResolver = context.getContentResolver();
            sInstance = new DefaultVoicemailNotifier(context, notificationManager,
                    createNewCallsQuery(context, contentResolver),
                    createNameLookupQuery(context, contentResolver));
        }
        return sInstance;
    }

    private DefaultVoicemailNotifier(Context context,
            NotificationManager notificationManager, NewCallsQuery newCallsQuery,
            NameLookupQuery nameLookupQuery) {
        mContext = context;
        mNotificationManager = notificationManager;
        mNewCallsQuery = newCallsQuery;
        mNameLookupQuery = nameLookupQuery;
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
        final NewCall[] newCalls = mNewCallsQuery.query();

        if (newCalls == null) {
            // Query failed, just return.
            return;
        }

        if (newCalls.length == 0) {
            // No voicemails to notify about: clear the notification.
            clearNotification();
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
        for (NewCall newCall : newCalls) {
            // Check if we already know the name associated with this number.
            String name = names.get(newCall.number);
            if (name == null) {
                name = PhoneNumberDisplayUtil.getDisplayName(
                        mContext,
                        newCall.number,
                        newCall.numberPresentation,
                        /* isVoicemail */ false).toString();
                // If we cannot lookup the contact, use the number instead.
                if (TextUtils.isEmpty(name)) {
                    // Look it up in the database.
                    name = mNameLookupQuery.query(newCall.number);
                    if (TextUtils.isEmpty(name)) {
                        name = newCall.number;
                    }
                }
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

        // If there is only one voicemail, set its transcription as the "long text".
        String transcription = null;
        if (newCalls.length == 1) {
            transcription = newCalls[0].transcription;
        }

        if (newCallUri != null && callToNotify == null) {
            Log.e(TAG, "The new call could not be found in the call log: " + newCallUri);
        }

        // Determine the title of the notification and the icon for it.
        final String title = resources.getQuantityString(
                R.plurals.notification_voicemail_title, newCalls.length, newCalls.length);
        // TODO: Use the photo of contact if all calls are from the same person.
        final int icon = android.R.drawable.stat_notify_voicemail;

        Notification.Builder notificationBuilder = new Notification.Builder(mContext)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(callers)
                .setStyle(new Notification.BigTextStyle().bigText(transcription))
                .setColor(resources.getColor(R.color.dialer_theme_color))
                .setDefaults(callToNotify != null ? Notification.DEFAULT_ALL : 0)
                .setDeleteIntent(createMarkNewVoicemailsAsOldIntent())
                .setAutoCancel(true);

        // Determine the intent to fire when the notification is clicked on.
        final Intent contentIntent;
        // Open the call log.
        // TODO: Send to recents tab in Dialer instead.
        contentIntent = new Intent(mContext, DialtactsActivity.class);
        contentIntent.putExtra(DialtactsActivity.EXTRA_SHOW_TAB, ListsFragment.TAB_INDEX_VOICEMAIL);
        notificationBuilder.setContentIntent(PendingIntent.getActivity(
                mContext, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT));

        // The text to show in the ticker, describing the new event.
        if (callToNotify != null) {
            notificationBuilder.setTicker(resources.getString(
                    R.string.notification_new_voicemail_ticker, names.get(callToNotify.number)));
        }

        mNotificationManager.notify(NOTIFICATION_TAG, NOTIFICATION_ID, notificationBuilder.build());
    }

    /** Creates a pending intent that marks all new voicemails as old. */
    private PendingIntent createMarkNewVoicemailsAsOldIntent() {
        Intent intent = new Intent(mContext, CallLogNotificationsService.class);
        intent.setAction(CallLogNotificationsService.ACTION_MARK_NEW_VOICEMAILS_AS_OLD);
        return PendingIntent.getService(mContext, 0, intent, 0);
    }

    public void clearNotification() {
        mNotificationManager.cancel(NOTIFICATION_TAG, NOTIFICATION_ID);
    }

    /** Information about a new voicemail. */
    private static final class NewCall {
        public final Uri callsUri;
        public final Uri voicemailUri;
        public final String number;
        public final int numberPresentation;
        public final String accountComponentName;
        public final String accountId;
        public final String transcription;

        public NewCall(
                Uri callsUri,
                Uri voicemailUri,
                String number,
                int numberPresentation,
                String accountComponentName,
                String accountId,
                String transcription) {
            this.callsUri = callsUri;
            this.voicemailUri = voicemailUri;
            this.number = number;
            this.numberPresentation = numberPresentation;
            this.accountComponentName = accountComponentName;
            this.accountId = accountId;
            this.transcription = transcription;
        }
    }

    /** Allows determining the new calls for which a notification should be generated. */
    public interface NewCallsQuery {
        /**
         * Returns the new calls for which a notification should be generated.
         */
        public NewCall[] query();
    }

    /** Create a new instance of {@link NewCallsQuery}. */
    public static NewCallsQuery createNewCallsQuery(Context context,
            ContentResolver contentResolver) {
        return new DefaultNewCallsQuery(context.getApplicationContext(), contentResolver);
    }

    /**
     * Default implementation of {@link NewCallsQuery} that looks up the list of new calls to
     * notify about in the call log.
     */
    private static final class DefaultNewCallsQuery implements NewCallsQuery {
        private static final String[] PROJECTION = {
            Calls._ID,
            Calls.NUMBER,
            Calls.VOICEMAIL_URI,
            Calls.NUMBER_PRESENTATION,
            Calls.PHONE_ACCOUNT_COMPONENT_NAME,
            Calls.PHONE_ACCOUNT_ID,
            Calls.TRANSCRIPTION
        };
        private static final int ID_COLUMN_INDEX = 0;
        private static final int NUMBER_COLUMN_INDEX = 1;
        private static final int VOICEMAIL_URI_COLUMN_INDEX = 2;
        private static final int NUMBER_PRESENTATION_COLUMN_INDEX = 3;
        private static final int PHONE_ACCOUNT_COMPONENT_NAME_COLUMN_INDEX = 4;
        private static final int PHONE_ACCOUNT_ID_COLUMN_INDEX = 5;
        private static final int TRANSCRIPTION_COLUMN_INDEX = 6;

        private final ContentResolver mContentResolver;
        private final Context mContext;

        private DefaultNewCallsQuery(Context context, ContentResolver contentResolver) {
            mContext = context;
            mContentResolver = contentResolver;
        }

        @Override
        public NewCall[] query() {
            if (!PermissionsUtil.hasPermission(mContext, READ_CALL_LOG)) {
                Log.w(TAG, "No READ_CALL_LOG permission, returning null for calls lookup.");
                return null;
            }
            final String selection = String.format("%s = 1 AND %s = ?", Calls.NEW, Calls.TYPE);
            final String[] selectionArgs = new String[]{ Integer.toString(Calls.VOICEMAIL_TYPE) };
            Cursor cursor = null;
            try {
                cursor = mContentResolver.query(Calls.CONTENT_URI_WITH_VOICEMAIL, PROJECTION,
                        selection, selectionArgs, Calls.DEFAULT_SORT_ORDER);
                if (cursor == null) {
                    return null;
                }
                NewCall[] newCalls = new NewCall[cursor.getCount()];
                while (cursor.moveToNext()) {
                    newCalls[cursor.getPosition()] = createNewCallsFromCursor(cursor);
                }
                return newCalls;
            } catch (RuntimeException e) {
                Log.w(TAG, "Exception when querying Contacts Provider for calls lookup");
                return null;
            } finally {
                MoreCloseables.closeQuietly(cursor);
            }
        }

        /** Returns an instance of {@link NewCall} created by using the values of the cursor. */
        private NewCall createNewCallsFromCursor(Cursor cursor) {
            String voicemailUriString = cursor.getString(VOICEMAIL_URI_COLUMN_INDEX);
            Uri callsUri = ContentUris.withAppendedId(
                    Calls.CONTENT_URI_WITH_VOICEMAIL, cursor.getLong(ID_COLUMN_INDEX));
            Uri voicemailUri = voicemailUriString == null ? null : Uri.parse(voicemailUriString);
            return new NewCall(
                    callsUri,
                    voicemailUri,
                    cursor.getString(NUMBER_COLUMN_INDEX),
                    cursor.getInt(NUMBER_PRESENTATION_COLUMN_INDEX),
                    cursor.getString(PHONE_ACCOUNT_COMPONENT_NAME_COLUMN_INDEX),
                    cursor.getString(PHONE_ACCOUNT_ID_COLUMN_INDEX),
                    cursor.getString(TRANSCRIPTION_COLUMN_INDEX));
        }
    }

    /** Allows determining the name associated with a given phone number. */
    public interface NameLookupQuery {
        /**
         * Returns the name associated with the given number in the contacts database, or null if
         * the number does not correspond to any of the contacts.
         * <p>
         * If there are multiple contacts with the same phone number, it will return the name of one
         * of the matching contacts.
         */
        public String query(String number);
    }

    /** Create a new instance of {@link NameLookupQuery}. */
    public static NameLookupQuery createNameLookupQuery(Context context,
            ContentResolver contentResolver) {
        return new DefaultNameLookupQuery(context.getApplicationContext(), contentResolver);
    }

    /**
     * Default implementation of {@link NameLookupQuery} that looks up the name of a contact in the
     * contacts database.
     */
    private static final class DefaultNameLookupQuery implements NameLookupQuery {
        private static final String[] PROJECTION = { PhoneLookup.DISPLAY_NAME };
        private static final int DISPLAY_NAME_COLUMN_INDEX = 0;

        private final ContentResolver mContentResolver;
        private final Context mContext;

        private DefaultNameLookupQuery(Context context, ContentResolver contentResolver) {
            mContext = context;
            mContentResolver = contentResolver;
        }

        @Override
        public String query(String number) {
            if (!PermissionsUtil.hasPermission(mContext, READ_CONTACTS)) {
                Log.w(TAG, "No READ_CONTACTS permission, returning null for name lookup.");
                return null;
            }
            Cursor cursor = null;
            try {
                cursor = mContentResolver.query(
                        Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                        PROJECTION, null, null, null);
                if (cursor == null || !cursor.moveToFirst()) return null;
                return cursor.getString(DISPLAY_NAME_COLUMN_INDEX);
            } catch (RuntimeException e) {
                Log.w(TAG, "Exception when querying Contacts Provider for name lookup");
                return null;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
    }
}
