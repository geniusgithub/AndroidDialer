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

package com.android.dialer;

import com.android.dialer.calllog.PhoneNumberDisplayUtil;

import android.content.Context;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.telecom.PhoneAccountHandle;

/**
 * The details of a phone call to be shown in the UI.
 */
public class PhoneCallDetails {
    // The number of the other party involved in the call.
    public CharSequence number;
    // The number presenting rules set by the network, e.g., {@link Calls#PRESENTATION_ALLOWED}
    public int numberPresentation;
    // The formatted version of {@link #number}.
    public CharSequence formattedNumber;
    // The country corresponding with the phone number.
    public String countryIso;
    // The geocoded location for the phone number.
    public String geocode;

    /**
     * The type of calls, as defined in the call log table, e.g., {@link Calls#INCOMING_TYPE}.
     * <p>
     * There might be multiple types if this represents a set of entries grouped together.
     */
    public int[] callTypes;

    // The date of the call, in milliseconds since the epoch.
    public long date;
    // The duration of the call in milliseconds, or 0 for missed calls.
    public long duration;
    // The name of the contact, or the empty string.
    public CharSequence name;
    // The type of phone, e.g., {@link Phone#TYPE_HOME}, 0 if not available.
    public int numberType;
    // The custom label associated with the phone number in the contact, or the empty string.
    public CharSequence numberLabel;
    // The URI of the contact associated with this phone call.
    public Uri contactUri;

    /**
     * The photo URI of the picture of the contact that is associated with this phone call or
     * null if there is none.
     * <p>
     * This is meant to store the high-res photo only.
     */
    public Uri photoUri;

    // The source type of the contact associated with this call.
    public int sourceType;

    // The object id type of the contact associated with this call.
    public String objectId;

    // The unique identifier for the account associated with the call.
    public PhoneAccountHandle accountHandle;

    // Features applicable to this call.
    public int features;

    // Total data usage for this call.
    public Long dataUsage;

    // Voicemail transcription
    public String transcription;

    // The display string for the number.
    public String displayNumber;

    // Whether the contact number is a voicemail number.
    public boolean isVoicemail;

    /**
     * If this is a voicemail, whether the message is read. For other types of calls, this defaults
     * to {@code true}.
     */
    public boolean isRead = true;

    /**
     * Constructor with required fields for the details of a call with a number associated with a
     * contact.
     */
    public PhoneCallDetails(
            Context context,
            CharSequence number,
            int numberPresentation,
            CharSequence formattedNumber,
            boolean isVoicemail) {
        this.number = number;
        this.numberPresentation = numberPresentation;
        this.formattedNumber = formattedNumber;
        this.isVoicemail = isVoicemail;
        this.displayNumber = PhoneNumberDisplayUtil.getDisplayNumber(
                context,
                this.number,
                this.numberPresentation,
                this.formattedNumber,
                this.isVoicemail).toString();
    }
}
