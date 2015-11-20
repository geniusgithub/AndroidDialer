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

/**
 * Stores a phone number of a call with the country code where it originally occurred. This object
 * is used as a key in the {@code ContactInfoCache}.
 *
 * The country does not necessarily specify the country of the phone number itself, but rather
 * it is the country in which the user was in when the call was placed or received.
 */
public final class NumberWithCountryIso {
    public final String number;
    public final String countryIso;

    public NumberWithCountryIso(String number, String countryIso) {
        this.number = number;
        this.countryIso = countryIso;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (!(o instanceof NumberWithCountryIso)) return false;
        NumberWithCountryIso other = (NumberWithCountryIso) o;
        return TextUtils.equals(number, other.number)
                && TextUtils.equals(countryIso, other.countryIso);
    }

    @Override
    public int hashCode() {
        int numberHashCode = number == null ? 0 : number.hashCode();
        int countryHashCode = countryIso == null ? 0 : countryIso.hashCode();

        return numberHashCode ^ countryHashCode;
    }
}
