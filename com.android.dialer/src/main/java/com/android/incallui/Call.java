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

package com.android.incallui;

import android.telecom.DisconnectCause;

import com.android.contacts.common.testing.NeededForTesting;

import java.util.Locale;

/**
 * Describes a single call and its state.
 */
@NeededForTesting
public class Call {

    /**
     * Tracks any state variables that is useful for logging. There is some amount of overlap with
     * existing call member variables, but this duplication helps to ensure that none of these
     * logging variables will interface with/and affect call logic.
     */
    public static class LogState {

        // Contact lookup type constants
        // Unknown lookup result (lookup not completed yet?)
        public static final int LOOKUP_UNKNOWN = 0;
        public static final int LOOKUP_NOT_FOUND = 1;
        public static final int LOOKUP_LOCAL_CONTACT = 2;
        public static final int LOOKUP_LOCAL_CACHE = 3;
        public static final int LOOKUP_REMOTE_CONTACT = 4;
        public static final int LOOKUP_EMERGENCY = 5;
        public static final int LOOKUP_VOICEMAIL = 6;

        // Call initiation type constants
        public static final int INITIATION_UNKNOWN = 0;
        public static final int INITIATION_INCOMING = 1;
        public static final int INITIATION_DIALPAD = 2;
        public static final int INITIATION_SPEED_DIAL = 3;
        public static final int INITIATION_REMOTE_DIRECTORY = 4;
        public static final int INITIATION_SMART_DIAL = 5;
        public static final int INITIATION_REGULAR_SEARCH = 6;
        public static final int INITIATION_CALL_LOG = 7;
        public static final int INITIATION_CALL_LOG_FILTER = 8;
        public static final int INITIATION_VOICEMAIL_LOG = 9;
        public static final int INITIATION_CALL_DETAILS = 10;
        public static final int INITIATION_QUICK_CONTACTS = 11;
        public static final int INITIATION_EXTERNAL = 12;

        public DisconnectCause disconnectCause;
        public boolean isIncoming = false;
        public int contactLookupResult = LOOKUP_UNKNOWN;
        public int callInitiationMethod = INITIATION_EXTERNAL;
        // If this was a conference call, the total number of calls involved in the conference.
        public int conferencedCalls = 0;
        public long duration = 0;
        public boolean isLogged = false;

        @Override
        public String toString() {
            return String.format(Locale.US, "["
                            + "%s, " // DisconnectCause toString already describes the object type
                            + "isIncoming: %s, "
                            + "contactLookup: %s, "
                            + "callInitiation: %s, "
                            + "duration: %s"
                            + "]",
                    disconnectCause,
                    isIncoming,
                    lookupToString(contactLookupResult),
                    initiationToString(callInitiationMethod),
                    duration);
        }

        private static String lookupToString(int lookupType) {
            switch (lookupType) {
                case LOOKUP_LOCAL_CONTACT:
                    return "Local";
                case LOOKUP_LOCAL_CACHE:
                    return "Cache";
                case LOOKUP_REMOTE_CONTACT:
                    return "Remote";
                case LOOKUP_EMERGENCY:
                    return "Emergency";
                case LOOKUP_VOICEMAIL:
                    return "Voicemail";
                default:
                    return "Not found";
            }
        }

        private static String initiationToString(int initiationType) {
            switch (initiationType) {
                case INITIATION_INCOMING:
                    return "Incoming";
                case INITIATION_DIALPAD:
                    return "Dialpad";
                case INITIATION_SPEED_DIAL:
                    return "Speed Dial";
                case INITIATION_REMOTE_DIRECTORY:
                    return "Remote Directory";
                case INITIATION_SMART_DIAL:
                    return "Smart Dial";
                case INITIATION_REGULAR_SEARCH:
                    return "Regular Search";
                case INITIATION_CALL_LOG:
                    return "Call Log";
                case INITIATION_CALL_LOG_FILTER:
                    return "Call Log Filter";
                case INITIATION_VOICEMAIL_LOG:
                    return "Voicemail Log";
                case INITIATION_CALL_DETAILS:
                    return "Call Details";
                case INITIATION_QUICK_CONTACTS:
                    return "Quick Contacts";
                default:
                    return "Unknown";
            }
        }
    }

}
