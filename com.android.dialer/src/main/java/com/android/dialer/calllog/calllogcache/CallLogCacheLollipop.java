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
 * limitations under the License
 */

package com.android.dialer.calllog.calllogcache;

import android.content.Context;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

/**
 * This is a compatibility class for the CallLogCache for versions of dialer before Lollipop Mr1
 * (the introduction of phone accounts).
 *
 * This class should not be initialized directly and instead be acquired from
 * {@link CallLogCache#getCallLogCache}.
 */
class CallLogCacheLollipop extends CallLogCache {
    private String mVoicemailNumber;

    /* package */ CallLogCacheLollipop(Context context) {
        super(context);
    }

    @Override
    public boolean isVoicemailNumber(PhoneAccountHandle accountHandle, CharSequence number) {
        if (TextUtils.isEmpty(number)) {
            return false;
        }

        String numberString = number.toString();

        if (!TextUtils.isEmpty(mVoicemailNumber)) {
            return PhoneNumberUtils.compare(numberString, mVoicemailNumber);
        }

        if (PhoneNumberUtils.isVoiceMailNumber(numberString)) {
            mVoicemailNumber = numberString;
            return true;
        }

        return false;
    }

    @Override
    public String getAccountLabel(PhoneAccountHandle accountHandle) {
        return null;
    }

    @Override
    public int getAccountColor(PhoneAccountHandle accountHandle) {
        return PhoneAccount.NO_HIGHLIGHT_COLOR;
    }

    @Override
    public boolean doesAccountSupportCallSubject(PhoneAccountHandle accountHandle) {
        return false;
    }
}
