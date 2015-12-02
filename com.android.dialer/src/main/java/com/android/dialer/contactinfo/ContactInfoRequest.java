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

package com.android.dialer.contactinfo;

import android.text.TextUtils;

import com.android.dialer.calllog.ContactInfo;
import com.google.common.base.Objects;

/**
 * A request for contact details for the given number, used by the ContactInfoCache.
 */
public final class ContactInfoRequest {
    /** The number to look-up. */
    public final String number;
    /** The country in which a call to or from this number was placed or received. */
    public final String countryIso;
    /** The cached contact information stored in the call log. */
    public final ContactInfo callLogInfo;

    public ContactInfoRequest(String number, String countryIso, ContactInfo callLogInfo) {
        this.number = number;
        this.countryIso = countryIso;
        this.callLogInfo = callLogInfo;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof ContactInfoRequest)) return false;

        ContactInfoRequest other = (ContactInfoRequest) obj;

        if (!TextUtils.equals(number, other.number)) return false;
        if (!TextUtils.equals(countryIso, other.countryIso)) return false;
        if (!Objects.equal(callLogInfo, other.callLogInfo)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((callLogInfo == null) ? 0 : callLogInfo.hashCode());
        result = prime * result + ((countryIso == null) ? 0 : countryIso.hashCode());
        result = prime * result + ((number == null) ? 0 : number.hashCode());
        return result;
    }
}
