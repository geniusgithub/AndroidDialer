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

import android.content.ComponentName;
import android.content.Context;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Methods to help extract {@code PhoneAccount} information from database and Telecomm sources.
 */
public class PhoneAccountUtils {
    /**
     * Return a list of phone accounts that are subscription/SIM accounts.
     */
    public static List<PhoneAccountHandle> getSubscriptionPhoneAccounts(Context context) {
        final TelecomManager telecomManager =
                (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);

        List<PhoneAccountHandle> subscriptionAccountHandles = new ArrayList<PhoneAccountHandle>();
        List<PhoneAccountHandle> accountHandles = telecomManager.getCallCapablePhoneAccounts();
        for (PhoneAccountHandle accountHandle : accountHandles) {
            PhoneAccount account = telecomManager.getPhoneAccount(accountHandle);
            if (account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
                subscriptionAccountHandles.add(accountHandle);
            }
        }
        return subscriptionAccountHandles;
    }

    /**
     * Compose PhoneAccount object from component name and account id.
     */
    public static PhoneAccountHandle getAccount(String componentString, String accountId) {
        if (TextUtils.isEmpty(componentString) || TextUtils.isEmpty(accountId)) {
            return null;
        }
        final ComponentName componentName = ComponentName.unflattenFromString(componentString);
        return new PhoneAccountHandle(componentName, accountId);
    }

    /**
     * Extract account label from PhoneAccount object.
     */
    public static String getAccountLabel(Context context, PhoneAccountHandle accountHandle) {
        PhoneAccount account = getAccountOrNull(context, accountHandle);
        if (account != null && account.getLabel() != null) {
            return account.getLabel().toString();
        }
        return null;
    }

    /**
     * Extract account color from PhoneAccount object.
     */
    public static int getAccountColor(Context context, PhoneAccountHandle accountHandle) {
        TelecomManager telecomManager =
                (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        final PhoneAccount account = telecomManager.getPhoneAccount(accountHandle);

        // For single-sim devices the PhoneAccount will be NO_HIGHLIGHT_COLOR by default, so it is
        // safe to always use the account highlight color.
        return account == null ? PhoneAccount.NO_HIGHLIGHT_COLOR : account.getHighlightColor();
    }

    /**
     * Retrieve the account metadata, but if the account does not exist or the device has only a
     * single registered and enabled account, return null.
     */
     static PhoneAccount getAccountOrNull(Context context,
            PhoneAccountHandle accountHandle) {
        TelecomManager telecomManager =
                (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        final PhoneAccount account = telecomManager.getPhoneAccount(accountHandle);
        if (telecomManager.getCallCapablePhoneAccounts().size() <= 1) {
            return null;
        }
        return account;
    }
}
