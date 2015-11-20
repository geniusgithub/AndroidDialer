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

package com.android.dialer.calllog;

import android.content.Context;
import android.provider.CallLog;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.util.PhoneNumberHelper;
import com.android.dialer.util.PhoneNumberUtil;
import com.google.common.collect.Sets;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Keeps a cache of recently made queries to the Telecom process. The aim of this cache is to
 * reduce the number of cross-process requests to TelecomManager, which can negatively affect
 * performance.
 *
 * This is designed with the specific use case of the {@link CallLogAdapter} in mind.
 */
public class TelecomCallLogCache {
    private final Context mContext;

    // Maps from a phone-account/number pair to a boolean because multiple numbers could return true
    // for the voicemail number if those numbers are not pre-normalized.
    // TODO: Dialer should be fixed so as not to check isVoicemail() so often but at the time of
    // this writing, that was a much larger undertaking than creating this cache.
    private final Map<Pair<PhoneAccountHandle, CharSequence>, Boolean> mVoicemailQueryCache =
            new HashMap<>();
    private final Map<PhoneAccountHandle, String> mPhoneAccountLabelCache = new HashMap<>();
    private final Map<PhoneAccountHandle, Integer> mPhoneAccountColorCache = new HashMap<>();

    private boolean mHasCheckedForVideoEnabled;
    private boolean mIsVideoEnabled;

    public TelecomCallLogCache(Context context) {
        mContext = context;
    }

    public void reset() {
        mVoicemailQueryCache.clear();
        mPhoneAccountLabelCache.clear();
        mPhoneAccountColorCache.clear();

        mHasCheckedForVideoEnabled = false;
        mIsVideoEnabled = false;
    }

    /**
     * Returns true if the given number is the number of the configured voicemail. To be able to
     * mock-out this, it is not a static method.
     */
    public boolean isVoicemailNumber(PhoneAccountHandle accountHandle, CharSequence number) {
        if (TextUtils.isEmpty(number)) {
            return false;
        }

        Pair<PhoneAccountHandle, CharSequence> key = new Pair<>(accountHandle, number);
        if (mVoicemailQueryCache.containsKey(key)) {
            return mVoicemailQueryCache.get(key);
        } else {
            Boolean isVoicemail =
                    PhoneNumberUtil.isVoicemailNumber(mContext, accountHandle, number.toString());
            mVoicemailQueryCache.put(key, isVoicemail);
            return isVoicemail;
        }
    }

    /**
     * Extract account label from PhoneAccount object.
     */
    public String getAccountLabel(PhoneAccountHandle accountHandle) {
        if (mPhoneAccountLabelCache.containsKey(accountHandle)) {
            return mPhoneAccountLabelCache.get(accountHandle);
        } else {
            String label = PhoneAccountUtils.getAccountLabel(mContext, accountHandle);
            mPhoneAccountLabelCache.put(accountHandle, label);
            return label;
        }
    }

    /**
     * Extract account color from PhoneAccount object.
     */
    public int getAccountColor(PhoneAccountHandle accountHandle) {
        if (mPhoneAccountColorCache.containsKey(accountHandle)) {
            return mPhoneAccountColorCache.get(accountHandle);
        } else {
            Integer color = PhoneAccountUtils.getAccountColor(mContext, accountHandle);
            mPhoneAccountColorCache.put(accountHandle, color);
            return color;
        }
    }

    public boolean isVideoEnabled() {
        if (!mHasCheckedForVideoEnabled) {
            mIsVideoEnabled = CallUtil.isVideoEnabled(mContext);
        }
        return mIsVideoEnabled;
    }
}
