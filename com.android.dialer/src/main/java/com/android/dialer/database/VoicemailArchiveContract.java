/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.database;

import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.CallLog;
import android.provider.OpenableColumns;

import com.android.dialerbind.ObjectFactory;

/**
 * Contains definitions for the supported URIs and columns for the voicemail archive table.
 * All the fields excluding MIME_TYPE, _DATA, ARCHIVED, SERVER_ID, mirror the fields in the
 * contract provided in {@link CallLog.Calls}.
 */
public final class VoicemailArchiveContract {

    /** The authority used by the voicemail archive provider. */
    public static final String AUTHORITY = ObjectFactory.getVoicemailArchiveProviderAuthority();

    /** A content:// style uri for the voicemail archive provider */
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    public static final class VoicemailArchive implements BaseColumns, OpenableColumns {

        public static final String VOICEMAIL_ARCHIVE_TABLE = "voicemail_archive_table";

        public static final Uri CONTENT_URI = Uri.withAppendedPath(
                AUTHORITY_URI,
                VOICEMAIL_ARCHIVE_TABLE);

        /**
         * @see android.provider.CallLog.Calls#NUMBER
         * TYPE: TEXT
         */
        public static final String NUMBER = CallLog.Calls.NUMBER;

        /**
         * @see android.provider.CallLog.Calls#DATE
         * TYPE: LONG
         */
        public static final String DATE = CallLog.Calls.DATE;

        /**
         * @see android.provider.CallLog.Calls#DURATION
         * TYPE: LONG
         */
        public static final String DURATION =  CallLog.Calls.DURATION;

        /**
         * The mime type of the archived voicemail file.
         * TYPE: TEXT
         */
        public static final String MIME_TYPE = "mime_type";

        /**
         * @see android.provider.CallLog.Calls#COUNTRY_ISO
         * TYPE: LONG
         */
        public static final String COUNTRY_ISO = CallLog.Calls.COUNTRY_ISO;

        /**
         * The path of the archived voicemail file.
         * TYPE: TEXT
         */
        public static final String _DATA = "_data";

        /**
         * @see android.provider.CallLog.Calls#GEOCODED_LOCATION
         * TYPE: TEXT
         */
        public static final String GEOCODED_LOCATION = CallLog.Calls.GEOCODED_LOCATION;

        /**
         * @see android.provider.CallLog.Calls#CACHED_NAME
         * TYPE: TEXT
         */
        public static final String CACHED_NAME = CallLog.Calls.CACHED_NAME;

        /**
         * @see android.provider.CallLog.Calls#CACHED_NUMBER_TYPE
         * TYPE: INTEGER
         */
        public static final String CACHED_NUMBER_TYPE = CallLog.Calls.CACHED_NUMBER_TYPE;

        /**
         * @see android.provider.CallLog.Calls#CACHED_NUMBER_LABEL
         * TYPE: TEXT
         */
        public static final String CACHED_NUMBER_LABEL = CallLog.Calls.CACHED_NUMBER_LABEL;

        /**
         * @see android.provider.CallLog.Calls#CACHED_LOOKUP_URI
         * TYPE: TEXT
         */
        public static final String CACHED_LOOKUP_URI = CallLog.Calls.CACHED_LOOKUP_URI;

        /**
         * @see android.provider.CallLog.Calls#CACHED_MATCHED_NUMBER
         * TYPE: TEXT
         */
        public static final String CACHED_MATCHED_NUMBER = CallLog.Calls.CACHED_MATCHED_NUMBER;

        /**
         * @see android.provider.CallLog.Calls#CACHED_NORMALIZED_NUMBER
         * TYPE: TEXT
         */
        public static final String CACHED_NORMALIZED_NUMBER =
                CallLog.Calls.CACHED_NORMALIZED_NUMBER;

        /**
         * @see android.provider.CallLog.Calls#CACHED_PHOTO_ID
         * TYPE: LONG
         */
        public static final String CACHED_PHOTO_ID = CallLog.Calls.CACHED_PHOTO_ID;

        /**
         * @see android.provider.CallLog.Calls#CACHED_FORMATTED_NUMBER
         * TYPE: TEXT
         */
        public static final String CACHED_FORMATTED_NUMBER = CallLog.Calls.CACHED_FORMATTED_NUMBER;

        /**
         * If the voicemail was archived by the user by pressing the archive button, this is set to
         * 1 (true). If the voicemail was archived for the purpose of forwarding to other
         * applications, this is set to 0 (false).
         * TYPE: INTEGER
         */
        public static final String ARCHIVED = "archived_by_user";

        /**
         * @see android.provider.CallLog.Calls#NUMBER_PRESENTATION
         * TYPE: INTEGER
         */
        public static final String NUMBER_PRESENTATION = CallLog.Calls.NUMBER_PRESENTATION;

        /**
         * @see android.provider.CallLog.Calls#PHONE_ACCOUNT_COMPONENT_NAME
         * TYPE: TEXT
         */
        public static final String ACCOUNT_COMPONENT_NAME =
                CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME;

        /**
         * @see android.provider.CallLog.Calls#PHONE_ACCOUNT_ID
         * TYPE: TEXT
         */
        public static final String ACCOUNT_ID = CallLog.Calls.PHONE_ACCOUNT_ID;

        /**
         * @see android.provider.CallLog.Calls#FEATURES
         * TYPE: INTEGER
         */
        public static final String FEATURES = CallLog.Calls.FEATURES;

        /**
         * The id of the voicemail on the server.
         * TYPE: INTEGER
         */
        public static final String SERVER_ID = "server_id";

        /**
         * @see android.provider.CallLog.Calls#TRANSCRIPTION
         * TYPE: TEXT
         */
        public static final String TRANSCRIPTION = CallLog.Calls.TRANSCRIPTION;

        /**
         * @see android.provider.CallLog.Calls#CACHED_PHOTO_URI
         * TYPE: TEXT
         */
        public static final String CACHED_PHOTO_URI = CallLog.Calls.CACHED_PHOTO_URI;

        /**
         * The MIME type of a {@link #CONTENT_URI} single voicemail.
         */
        public static final String CONTENT_ITEM_TYPE =
                "vnd.android.cursor.item/voicmail_archive_table";

        public static final Uri buildWithId(int id) {
            return Uri.withAppendedPath(CONTENT_URI, Integer.toString(id));
        }

        /** Not instantiable. */
        private VoicemailArchive() {
        }
    }
}
