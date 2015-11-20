/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;

import com.android.contacts.common.CallUtil;
import com.android.phone.common.PhoneConstants;

/**
 * Utilities for creation of intents in Dialer, such as {@link Intent#ACTION_CALL}.
 */
public class IntentUtil {

    public static final String CALL_ACTION = Intent.ACTION_CALL;
    private static final String SMS_URI_PREFIX = "sms:";
    private static final int NO_PHONE_TYPE = -1;

    /**
     * Return an Intent for making a phone call. Scheme (e.g. tel, sip) will be determined
     * automatically.
     */
    public static Intent getCallIntent(String number) {
        return getCallIntent(number, null, null);
    }

    /**
     * Return an Intent for making a phone call. A given Uri will be used as is (without any
     * sanity check).
     */
    public static Intent getCallIntent(Uri uri) {
        return getCallIntent(uri, null, null);
    }

    /**
     * A variant of {@link #getCallIntent(String)} but also accept a call origin.
     * For more information about call origin, see comments in Phone package (PhoneApp).
     */
    public static Intent getCallIntent(String number, String callOrigin) {
        return getCallIntent(CallUtil.getCallUri(number), callOrigin, null);
    }

    /**
     * A variant of {@link #getCallIntent(String)} but also include {@code Account}.
     */
    public static Intent getCallIntent(String number, PhoneAccountHandle accountHandle) {
        return getCallIntent(number, null, accountHandle);
    }

    /**
     * A variant of {@link #getCallIntent(android.net.Uri)} but also include {@code Account}.
     */
    public static Intent getCallIntent(Uri uri, PhoneAccountHandle accountHandle) {
        return getCallIntent(uri, null, accountHandle);
    }

    /**
     * A variant of {@link #getCallIntent(String, String)} but also include {@code Account}.
     */
    public static Intent getCallIntent(
            String number, String callOrigin, PhoneAccountHandle accountHandle) {
        return getCallIntent(CallUtil.getCallUri(number), callOrigin, accountHandle);
    }

    /**
     * A variant of {@link #getCallIntent(android.net.Uri)} but also accept a call
     * origin and {@code Account}.
     * For more information about call origin, see comments in Phone package (PhoneApp).
     */
    public static Intent getCallIntent(
            Uri uri, String callOrigin, PhoneAccountHandle accountHandle) {
        return getCallIntent(uri, callOrigin, accountHandle,
                VideoProfile.STATE_AUDIO_ONLY);
    }

    /**
     * A variant of {@link #getCallIntent(String, String)} for starting a video call.
     */
    public static Intent getVideoCallIntent(String number, String callOrigin) {
        return getCallIntent(CallUtil.getCallUri(number), callOrigin, null,
                VideoProfile.STATE_BIDIRECTIONAL);
    }

    /**
     * A variant of {@link #getCallIntent(String, String, android.telecom.PhoneAccountHandle)} for
     * starting a video call.
     */
    public static Intent getVideoCallIntent(
            String number, String callOrigin, PhoneAccountHandle accountHandle) {
        return getCallIntent(CallUtil.getCallUri(number), callOrigin, accountHandle,
                VideoProfile.STATE_BIDIRECTIONAL);
    }

    /**
     * A variant of {@link #getCallIntent(String, String, android.telecom.PhoneAccountHandle)} for
     * starting a video call.
     */
    public static Intent getVideoCallIntent(String number, PhoneAccountHandle accountHandle) {
        return getVideoCallIntent(number, null, accountHandle);
    }

    /**
     * A variant of {@link #getCallIntent(android.net.Uri)} for calling Voicemail.
     */
    public static Intent getVoicemailIntent() {
        return getCallIntent(Uri.fromParts(PhoneAccount.SCHEME_VOICEMAIL, "", null));
    }

    /**
     * A variant of {@link #getCallIntent(android.net.Uri)} but also accept a call
     * origin and {@code Account} and {@code VideoCallProfile} state.
     * For more information about call origin, see comments in Phone package (PhoneApp).
     */
    public static Intent getCallIntent(
            Uri uri, String callOrigin, PhoneAccountHandle accountHandle, int videoState) {
        final Intent intent = new Intent(CALL_ACTION, uri);
        intent.putExtra(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, videoState);
        if (callOrigin != null) {
            intent.putExtra(PhoneConstants.EXTRA_CALL_ORIGIN, callOrigin);
        }
        if (accountHandle != null) {
            intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle);
        }

        return intent;
    }

    public static Intent getSendSmsIntent(CharSequence phoneNumber) {
        return new Intent(Intent.ACTION_SENDTO, Uri.parse(SMS_URI_PREFIX + phoneNumber));
    }

    public static Intent getNewContactIntent() {
        return new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI);
    }

    public static Intent getNewContactIntent(CharSequence phoneNumber) {
        return getNewContactIntent(
                null /* name */,
                phoneNumber /* phoneNumber */,
                NO_PHONE_TYPE);
    }

    public static Intent getNewContactIntent(
            CharSequence name, CharSequence phoneNumber, int phoneNumberType) {
        Intent intent = getNewContactIntent();
        populateContactIntent(intent, name, phoneNumber, phoneNumberType);
        return intent;
    }

    public static Intent getAddToExistingContactIntent() {
        Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
        return intent;
    }

    public static Intent getAddToExistingContactIntent(CharSequence phoneNumber) {
        return getAddToExistingContactIntent(
                null /* name */,
                phoneNumber /* phoneNumber */,
                NO_PHONE_TYPE);
    }

    public static Intent getAddToExistingContactIntent(
            CharSequence name, CharSequence phoneNumber, int phoneNumberType) {
        Intent intent = getAddToExistingContactIntent();
        populateContactIntent(intent, name, phoneNumber, phoneNumberType);
        return intent;
    }

    private static void populateContactIntent(
            Intent intent, CharSequence name, CharSequence phoneNumber, int phoneNumberType) {
        if (phoneNumber != null) {
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber);
        }
        if (name != null) {
            intent.putExtra(ContactsContract.Intents.Insert.NAME, name);
        }
        if (phoneNumberType != NO_PHONE_TYPE) {
            intent.putExtra(ContactsContract.Intents.Insert.PHONE_TYPE, phoneNumberType);
        }
    }
}
