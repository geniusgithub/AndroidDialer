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

package com.android.dialer.util;

import android.net.Uri;
import android.provider.ContactsContract;

import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.compat.PhoneLookupSdkCompat;

public final class PhoneLookupUtil {
    /**
     * @return the column name that stores contact id for phone lookup query.
     */
    public static String getContactIdColumnNameForUri(Uri phoneLookupUri) {
        if (CompatUtils.isNCompatible()) {
            return PhoneLookupSdkCompat.CONTACT_ID;
        }
        // In pre-N, contact id is stored in {@link PhoneLookup#_ID} in non-sip query.
        boolean isSip = phoneLookupUri.getBooleanQueryParameter(
                ContactsContract.PhoneLookup.QUERY_PARAMETER_SIP_ADDRESS, false);
        return (isSip) ? PhoneLookupSdkCompat.CONTACT_ID : ContactsContract.PhoneLookup._ID;
    }

    private PhoneLookupUtil() {}
}
