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

package com.android.dialer.calllog.calllogcache;

import android.content.Context;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import android.util.Pair;

import com.android.dialer.calllog.PhoneAccountUtils;
import com.android.dialer.util.PhoneNumberUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * This is the CallLogCache for versions of dialer Lollipop Mr1 and above with support for
 * multi-SIM devices.
 *
 * This class should not be initialized directly and instead be acquired from
 * {@link CallLogCache#getCallLogCache}.
 */
class CallLogCacheLollipopMr1 extends CallLogCache {
    // Maps from a phone-account/number pair to a boolean because multiple numbers could return true
    // for the voicemail number if those numbers are not pre-normalized.
    private final Map<Pair<PhoneAccountHandle, CharSequence>, Boolean> mVoicemailQueryCache =
            new HashMap<>();
    private final Map<PhoneAccountHandle, String> mPhoneAccountLabelCache = new HashMap<>();
    private final Map<PhoneAccountHandle, Integer> mPhoneAccountColorCache = new HashMap<>();
    private final Map<PhoneAccountHandle, Boolean> mPhoneAccountCallWithNoteCache = new HashMap<>();

    /* package */ CallLogCacheLollipopMr1(Context context) {
        super(context);
    }

    @Override
    public void reset() {
        mVoicemailQueryCache.clear();
        mPhoneAccountLabelCache.clear();
        mPhoneAccountColorCache.clear();
        mPhoneAccountCallWithNoteCache.clear();

        super.reset();
    }

    @Override
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

    @Override
    public String getAccountLabel(PhoneAccountHandle accountHandle) {
        if (mPhoneAccountLabelCache.containsKey(accountHandle)) {
            return mPhoneAccountLabelCache.get(accountHandle);
        } else {
            String label = PhoneAccountUtils.getAccountLabel(mContext, accountHandle);
            mPhoneAccountLabelCache.put(accountHandle, label);
            return label;
        }
    }

    @Override
    public int getAccountColor(PhoneAccountHandle accountHandle) {
        if (mPhoneAccountColorCache.containsKey(accountHandle)) {
            return mPhoneAccountColorCache.get(accountHandle);
        } else {
            Integer color = PhoneAccountUtils.getAccountColor(mContext, accountHandle);
            mPhoneAccountColorCache.put(accountHandle, color);
            return color;
        }
    }

    @Override
    public boolean doesAccountSupportCallSubject(PhoneAccountHandle accountHandle) {
        if (mPhoneAccountCallWithNoteCache.containsKey(accountHandle)) {
            return mPhoneAccountCallWithNoteCache.get(accountHandle);
        } else {
            Boolean supportsCallWithNote =
                    PhoneAccountUtils.getAccountSupportsCallSubject(mContext, accountHandle);
            mPhoneAccountCallWithNoteCache.put(accountHandle, supportsCallWithNote);
            return supportsCallWithNote;
        }
    }
}
