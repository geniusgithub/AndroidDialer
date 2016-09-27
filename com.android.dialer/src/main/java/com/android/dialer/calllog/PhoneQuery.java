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

import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;

import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.compat.PhoneLookupSdkCompat;
import com.android.contacts.common.ContactsUtils;

/**
 * The queries to look up the {@link ContactInfo} for a given number in the Call Log.
 */
final class PhoneQuery {

    /**
     * Projection to look up the ContactInfo. Does not include DISPLAY_NAME_ALTERNATIVE as that
     * column isn't available in ContactsCommon.PhoneLookup.
     * We should always use this projection starting from NYC onward.
     */
    private static final String[] PHONE_LOOKUP_PROJECTION = new String[] {
            PhoneLookupSdkCompat.CONTACT_ID,
            PhoneLookup.DISPLAY_NAME,
            PhoneLookup.TYPE,
            PhoneLookup.LABEL,
            PhoneLookup.NUMBER,
            PhoneLookup.NORMALIZED_NUMBER,
            PhoneLookup.PHOTO_ID,
            PhoneLookup.LOOKUP_KEY,
            PhoneLookup.PHOTO_URI
    };

    /**
     * Similar to {@link PHONE_LOOKUP_PROJECTION}. In pre-N, contact id is stored in
     * {@link PhoneLookup#_ID} in non-sip query.
     */
    private static final String[] BACKWARD_COMPATIBLE_NON_SIP_PHONE_LOOKUP_PROJECTION =
            new String[] {
                    PhoneLookup._ID,
                    PhoneLookup.DISPLAY_NAME,
                    PhoneLookup.TYPE,
                    PhoneLookup.LABEL,
                    PhoneLookup.NUMBER,
                    PhoneLookup.NORMALIZED_NUMBER,
                    PhoneLookup.PHOTO_ID,
                    PhoneLookup.LOOKUP_KEY,
                    PhoneLookup.PHOTO_URI
            };

    public static String[] getPhoneLookupProjection(Uri phoneLookupUri) {
        if (CompatUtils.isNCompatible()) {
            return PHONE_LOOKUP_PROJECTION;
        }
        // Pre-N
        boolean isSip = phoneLookupUri.getBooleanQueryParameter(
                ContactsContract.PhoneLookup.QUERY_PARAMETER_SIP_ADDRESS, false);
        return (isSip) ? PHONE_LOOKUP_PROJECTION
                : BACKWARD_COMPATIBLE_NON_SIP_PHONE_LOOKUP_PROJECTION;
    }

    public static final int PERSON_ID = 0;
    public static final int NAME = 1;
    public static final int PHONE_TYPE = 2;
    public static final int LABEL = 3;
    public static final int MATCHED_NUMBER = 4;
    public static final int NORMALIZED_NUMBER = 5;
    public static final int PHOTO_ID = 6;
    public static final int LOOKUP_KEY = 7;
    public static final int PHOTO_URI = 8;

    /**
     * Projection to look up a contact's DISPLAY_NAME_ALTERNATIVE
     */
    public static final String[] DISPLAY_NAME_ALTERNATIVE_PROJECTION = new String[] {
            Contacts.DISPLAY_NAME_ALTERNATIVE,
    };

    public static final int NAME_ALTERNATIVE = 0;
}
