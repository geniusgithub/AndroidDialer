/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.base.Strings;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.PhoneLookup;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.util.PermissionsUtil;
import com.android.dialer.R;
import com.android.dialer.util.TelecomUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class operating on call log notifications.
 */
public class CallLogNotificationsHelper {
    private static final String TAG = "CallLogNotifHelper";
    private static CallLogNotificationsHelper sInstance;

    /** Returns the singleton instance of the {@link CallLogNotificationsHelper}. */
    public static CallLogNotificationsHelper getInstance(Context context) {
        if (sInstance == null) {
            ContentResolver contentResolver = context.getContentResolver();
            String countryIso = GeoUtil.getCurrentCountryIso(context);
            sInstance = new CallLogNotificationsHelper(context,
                    createNewCallsQuery(context, contentResolver),
                    createNameLookupQuery(context, contentResolver),
                    new ContactInfoHelper(context, countryIso),
                    countryIso);
        }
        return sInstance;
    }

    private final Context mContext;
    private final NewCallsQuery mNewCallsQuery;
    private final NameLookupQuery mNameLookupQuery;
    private final ContactInfoHelper mContactInfoHelper;
    private final String mCurrentCountryIso;

    CallLogNotificationsHelper(Context context, NewCallsQuery newCallsQuery,
            NameLookupQuery nameLookupQuery, ContactInfoHelper contactInfoHelper,
            String countryIso) {
        mContext = context;
        mNewCallsQuery = newCallsQuery;
        mNameLookupQuery = nameLookupQuery;
        mContactInfoHelper = contactInfoHelper;
        mCurrentCountryIso = countryIso;
    }

    /**
     * Get all voicemails with the "new" flag set to 1.
     *
     * @return A list of NewCall objects where each object represents a new voicemail.
     */
    @Nullable
    public List<NewCall> getNewVoicemails() {
        return mNewCallsQuery.query(Calls.VOICEMAIL_TYPE);
    }

    /**
     * Get all missed calls with the "new" flag set to 1.
     *
     * @return A list of NewCall objects where each object represents a new missed call.
     */
    @Nullable
    public List<NewCall> getNewMissedCalls() {
        return mNewCallsQuery.query(Calls.MISSED_TYPE);
    }

    /**
     * Given a number and number information (presentation and country ISO), get the best name
     * for display. If the name is empty but we have a special presentation, display that.
     * Otherwise attempt to look it up in the database or the cache.
     * If that fails, fall back to displaying the number.
     */
    public String getName(@Nullable String number, int numberPresentation,
                          @Nullable String countryIso) {
        return getContactInfo(number, numberPresentation, countryIso).name;
    }

    /**
     * Given a number and number information (presentation and country ISO), get
     * {@link ContactInfo}. If the name is empty but we have a special presentation, display that.
     * Otherwise attempt to look it up in the cache.
     * If that fails, fall back to displaying the number.
     */
    public ContactInfo getContactInfo(@Nullable String number, int numberPresentation,
                          @Nullable String countryIso) {
        if (countryIso == null) {
            countryIso = mCurrentCountryIso;
        }

        number = Strings.nullToEmpty(number);
        ContactInfo contactInfo = new ContactInfo();
        contactInfo.number = number;
        contactInfo.formattedNumber = PhoneNumberUtils.formatNumber(number, countryIso);
        // contactInfo.normalizedNumber is not PhoneNumberUtils.normalizeNumber. Read ContactInfo.
        contactInfo.normalizedNumber = PhoneNumberUtils.formatNumberToE164(number, countryIso);

        // 1. Special number representation.
        contactInfo.name = PhoneNumberDisplayUtil.getDisplayName(
                mContext,
                number,
                numberPresentation,
                false).toString();
        if (!TextUtils.isEmpty(contactInfo.name)) {
            return contactInfo;
        }

        // 2. Look it up in the cache.
        ContactInfo cachedContactInfo = mContactInfoHelper.lookupNumber(number, countryIso);

        if (cachedContactInfo != null && !TextUtils.isEmpty(cachedContactInfo.name)) {
            return cachedContactInfo;
        }

        if (!TextUtils.isEmpty(contactInfo.formattedNumber)) {
            // 3. If we cannot lookup the contact, use the formatted number instead.
            contactInfo.name = contactInfo.formattedNumber;
        } else if (!TextUtils.isEmpty(number)) {
            // 4. If number can't be formatted, use number.
            contactInfo.name = number;
        } else {
            // 5. Otherwise, it's unknown number.
            contactInfo.name = mContext.getResources().getString(R.string.unknown);
        }
        return contactInfo;
    }

    /** Removes the missed call notifications. */
    public static void removeMissedCallNotifications(Context context) {
        TelecomUtil.cancelMissedCallsNotification(context);
    }

    /** Update the voice mail notifications. */
    public static void updateVoicemailNotifications(Context context) {
        CallLogNotificationsService.updateVoicemailNotifications(context, null);
    }

    /** Information about a new voicemail. */
    public static final class NewCall {
        public final Uri callsUri;
        public final Uri voicemailUri;
        public final String number;
        public final int numberPresentation;
        public final String accountComponentName;
        public final String accountId;
        public final String transcription;
        public final String countryIso;
        public final long dateMs;

        public NewCall(
                Uri callsUri,
                Uri voicemailUri,
                String number,
                int numberPresentation,
                String accountComponentName,
                String accountId,
                String transcription,
                String countryIso,
                long dateMs) {
            this.callsUri = callsUri;
            this.voicemailUri = voicemailUri;
            this.number = number;
            this.numberPresentation = numberPresentation;
            this.accountComponentName = accountComponentName;
            this.accountId = accountId;
            this.transcription = transcription;
            this.countryIso = countryIso;
            this.dateMs = dateMs;
        }
    }

    /** Allows determining the new calls for which a notification should be generated. */
    public interface NewCallsQuery {
        /**
         * Returns the new calls of a certain type for which a notification should be generated.
         */
        @Nullable
        public List<NewCall> query(int type);
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
            Calls.TRANSCRIPTION,
            Calls.COUNTRY_ISO,
            Calls.DATE
        };
        private static final int ID_COLUMN_INDEX = 0;
        private static final int NUMBER_COLUMN_INDEX = 1;
        private static final int VOICEMAIL_URI_COLUMN_INDEX = 2;
        private static final int NUMBER_PRESENTATION_COLUMN_INDEX = 3;
        private static final int PHONE_ACCOUNT_COMPONENT_NAME_COLUMN_INDEX = 4;
        private static final int PHONE_ACCOUNT_ID_COLUMN_INDEX = 5;
        private static final int TRANSCRIPTION_COLUMN_INDEX = 6;
        private static final int COUNTRY_ISO_COLUMN_INDEX = 7;
        private static final int DATE_COLUMN_INDEX = 8;

        private final ContentResolver mContentResolver;
        private final Context mContext;

        private DefaultNewCallsQuery(Context context, ContentResolver contentResolver) {
            mContext = context;
            mContentResolver = contentResolver;
        }

        @Override
        @Nullable
        public List<NewCall> query(int type) {
            if (!PermissionsUtil.hasPermission(mContext, Manifest.permission.READ_CALL_LOG)) {
                Log.w(TAG, "No READ_CALL_LOG permission, returning null for calls lookup.");
                return null;
            }
            final String selection = String.format("%s = 1 AND %s = ?", Calls.NEW, Calls.TYPE);
            final String[] selectionArgs = new String[]{ Integer.toString(type) };
            try (Cursor cursor = mContentResolver.query(Calls.CONTENT_URI_WITH_VOICEMAIL,
                    PROJECTION, selection, selectionArgs, Calls.DEFAULT_SORT_ORDER)) {
                if (cursor == null) {
                    return null;
                }
                List<NewCall> newCalls = new ArrayList<>();
                while (cursor.moveToNext()) {
                    newCalls.add(createNewCallsFromCursor(cursor));
                }
                return newCalls;
            } catch (RuntimeException e) {
                Log.w(TAG, "Exception when querying Contacts Provider for calls lookup");
                return null;
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
                    cursor.getString(TRANSCRIPTION_COLUMN_INDEX),
                    cursor.getString(COUNTRY_ISO_COLUMN_INDEX),
                    cursor.getLong(DATE_COLUMN_INDEX));
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
        @Nullable
        public String query(@Nullable String number);
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
        @Nullable
        public String query(@Nullable String number) {
            if (!PermissionsUtil.hasPermission(mContext, Manifest.permission.READ_CONTACTS)) {
                Log.w(TAG, "No READ_CONTACTS permission, returning null for name lookup.");
                return null;
            }
            try (Cursor cursor =  mContentResolver.query(
                    Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                    PROJECTION, null, null, null)) {
                if (cursor == null || !cursor.moveToFirst()) {
                    return null;
                }
                return cursor.getString(DISPLAY_NAME_COLUMN_INDEX);
            } catch (RuntimeException e) {
                Log.w(TAG, "Exception when querying Contacts Provider for name lookup");
                return null;
            }
        }
    }
}
