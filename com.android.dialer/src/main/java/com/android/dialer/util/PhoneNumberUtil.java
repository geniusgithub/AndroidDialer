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
 * limitations under the License
 */

package com.android.dialer.util;

import android.content.Context;
import android.provider.CallLog;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.android.contacts.common.util.PhoneNumberHelper;
import com.android.contacts.common.util.TelephonyManagerUtils;
import com.google.common.collect.Sets;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.Phonenumber;
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PhoneNumberUtil {
    private static final String TAG = "PhoneNumberUtil";
    private static final Set<String> LEGACY_UNKNOWN_NUMBERS = Sets.newHashSet("-1", "-2", "-3");

    /** Returns true if it is possible to place a call to the given number. */
    public static boolean canPlaceCallsTo(CharSequence number, int presentation) {
        return presentation == CallLog.Calls.PRESENTATION_ALLOWED
            && !TextUtils.isEmpty(number) && !isLegacyUnknownNumbers(number);
    }

    /**
     * Returns true if the given number is the number of the configured voicemail. To be able to
     * mock-out this, it is not a static method.
     */
    public static boolean isVoicemailNumber(
            Context context, PhoneAccountHandle accountHandle, CharSequence number) {
        if (TextUtils.isEmpty(number)) {
            return false;
        }
        return TelecomUtil.isVoicemailNumber(context, accountHandle, number.toString());
    }

    /**
     * Returns true if the given number is a SIP address. To be able to mock-out this, it is not a
     * static method.
     */
    public static boolean isSipNumber(CharSequence number) {
        return number != null && PhoneNumberHelper.isUriNumber(number.toString());
    }

    public static boolean isUnknownNumberThatCanBeLookedUp(
            Context context,
            PhoneAccountHandle accountHandle,
            CharSequence number,
            int presentation) {
        if (presentation == CallLog.Calls.PRESENTATION_UNKNOWN) {
            return false;
        }
        if (presentation == CallLog.Calls.PRESENTATION_RESTRICTED) {
            return false;
        }
        if (presentation == CallLog.Calls.PRESENTATION_PAYPHONE) {
            return false;
        }
        if (TextUtils.isEmpty(number)) {
            return false;
        }
        if (isVoicemailNumber(context, accountHandle, number)) {
            return false;
        }
        if (isLegacyUnknownNumbers(number)) {
            return false;
        }
        return true;
    }

    public static boolean isLegacyUnknownNumbers(CharSequence number) {
        return number != null && LEGACY_UNKNOWN_NUMBERS.contains(number.toString());
    }

    /**
     * @return a geographical description string for the specified number.
     * @see com.android.i18n.phonenumbers.PhoneNumberOfflineGeocoder
     */
    public static String getGeoDescription(Context context, String number) {
        Log.v(TAG, "getGeoDescription('" + pii(number) + "')...");

        if (TextUtils.isEmpty(number)) {
            return null;
        }

        com.google.i18n.phonenumbers.PhoneNumberUtil util =
                com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
        PhoneNumberOfflineGeocoder geocoder = PhoneNumberOfflineGeocoder.getInstance();

        Locale locale = context.getResources().getConfiguration().locale;
        String countryIso = TelephonyManagerUtils.getCurrentCountryIso(context, locale);
        Phonenumber.PhoneNumber pn = null;
        try {
            Log.v(TAG, "parsing '" + pii(number)
                    + "' for countryIso '" + countryIso + "'...");
            pn = util.parse(number, countryIso);
            Log.v(TAG, "- parsed number: " + pii(pn));
        } catch (NumberParseException e) {
            Log.v(TAG, "getGeoDescription: NumberParseException for incoming number '" +
                    pii(number) + "'");
        }

        if (pn != null) {
            String description = geocoder.getDescriptionForNumber(pn, locale);
            Log.v(TAG, "- got description: '" + description + "'");
            return description;
        }

        return null;
    }

    private static String pii(Object pii) {
        return com.android.incallui.Log.pii(pii);
    }
}
