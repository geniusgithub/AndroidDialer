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

package com.android.dialer.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.compat.telecom.TelecomManagerCompat;
import com.android.dialer.compat.DialerCompatUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Performs permission checks before calling into TelecomManager. Each method is self-explanatory -
 * perform the required check and return the fallback default if the permission is missing,
 * otherwise return the value from TelecomManager.
 *
 */
public class TelecomUtil {
    private static final String TAG = "TelecomUtil";
    private static boolean sWarningLogged = false;

    public static void showInCallScreen(Context context, boolean showDialpad) {
        if (hasReadPhoneStatePermission(context)) {
            try {
                getTelecomManager(context).showInCallScreen(showDialpad);
            } catch (SecurityException e) {
                // Just in case
                Log.w(TAG, "TelecomManager.showInCallScreen called without permission.");
            }
        }
    }

    public static void silenceRinger(Context context) {
        if (hasModifyPhoneStatePermission(context)) {
            try {
                TelecomManagerCompat.silenceRinger(getTelecomManager(context));
            } catch (SecurityException e) {
                // Just in case
                Log.w(TAG, "TelecomManager.silenceRinger called without permission.");
            }
        }
    }

    public static void cancelMissedCallsNotification(Context context) {
        if (hasModifyPhoneStatePermission(context)) {
            try {
                getTelecomManager(context).cancelMissedCallsNotification();
            } catch (SecurityException e) {
                Log.w(TAG, "TelecomManager.cancelMissedCalls called without permission.");
            }
        }
    }

    public static Uri getAdnUriForPhoneAccount(Context context, PhoneAccountHandle handle) {
        if (hasModifyPhoneStatePermission(context)) {
            try {
                return TelecomManagerCompat.getAdnUriForPhoneAccount(
                        getTelecomManager(context), handle);
            } catch (SecurityException e) {
                Log.w(TAG, "TelecomManager.getAdnUriForPhoneAccount called without permission.");
            }
        }
        return null;
    }

    public static boolean handleMmi(Context context, String dialString,
            PhoneAccountHandle handle) {
        if (hasModifyPhoneStatePermission(context)) {
            try {
                return TelecomManagerCompat.handleMmi(
                        getTelecomManager(context), dialString, handle);
            } catch (SecurityException e) {
                Log.w(TAG, "TelecomManager.handleMmi called without permission.");
            }
        }
        return false;
    }

    @Nullable
    public static PhoneAccountHandle getDefaultOutgoingPhoneAccount(Context context,
            String uriScheme) {
        if (hasReadPhoneStatePermission(context)) {
            return TelecomManagerCompat.getDefaultOutgoingPhoneAccount(
                    getTelecomManager(context), uriScheme);
        }
        return null;
    }

    public static PhoneAccount getPhoneAccount(Context context, PhoneAccountHandle handle) {
        return TelecomManagerCompat.getPhoneAccount(getTelecomManager(context), handle);
    }

    public static List<PhoneAccountHandle> getCallCapablePhoneAccounts(Context context) {
        if (hasReadPhoneStatePermission(context)) {
            return TelecomManagerCompat.getCallCapablePhoneAccounts(getTelecomManager(context));
        }
        return new ArrayList<>();
    }

    public static boolean isInCall(Context context) {
        if (hasReadPhoneStatePermission(context)) {
            return getTelecomManager(context).isInCall();
        }
        return false;
    }

    public static boolean isVoicemailNumber(Context context, PhoneAccountHandle accountHandle,
            String number) {
        if (hasReadPhoneStatePermission(context)) {
            return TelecomManagerCompat.isVoiceMailNumber(getTelecomManager(context),
                    accountHandle, number);
        }
        return false;
    }

    @Nullable
    public static String getVoicemailNumber(Context context, PhoneAccountHandle accountHandle) {
        if (hasReadPhoneStatePermission(context)) {
            return TelecomManagerCompat.getVoiceMailNumber(getTelecomManager(context),
                    getTelephonyManager(context), accountHandle);
        }
        return null;
    }

    /**
     * Tries to place a call using the {@link TelecomManager}.
     *
     * @param activity a valid activity.
     * @param intent the call intent.
     *
     * @return {@code true} if we successfully attempted to place the call, {@code false} if it
     *         failed due to a permission check.
     */
    public static boolean placeCall(Activity activity, Intent intent) {
        if (hasCallPhonePermission(activity)) {
            TelecomManagerCompat.placeCall(activity, getTelecomManager(activity), intent);
            return true;
        }
        return false;
    }

    public static Uri getCallLogUri(Context context) {
        return hasReadWriteVoicemailPermissions(context) ? Calls.CONTENT_URI_WITH_VOICEMAIL
                : Calls.CONTENT_URI;
    }

    public static boolean hasReadWriteVoicemailPermissions(Context context) {
        return isDefaultDialer(context)
                || (hasPermission(context, Manifest.permission.READ_VOICEMAIL)
                        && hasPermission(context, Manifest.permission.WRITE_VOICEMAIL));
    }

    public static boolean hasModifyPhoneStatePermission(Context context) {
        return isDefaultDialer(context)
                || hasPermission(context, Manifest.permission.MODIFY_PHONE_STATE);
    }

    public static boolean hasReadPhoneStatePermission(Context context) {
        return isDefaultDialer(context)
                || hasPermission(context, Manifest.permission.READ_PHONE_STATE);
    }

    public static boolean hasCallPhonePermission(Context context) {
        return isDefaultDialer(context)
                || hasPermission(context, Manifest.permission.CALL_PHONE);
    }

    private static boolean hasPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isDefaultDialer(Context context) {
        final boolean result = TextUtils.equals(context.getPackageName(),
                TelecomManagerCompat.getDefaultDialerPackage(getTelecomManager(context)));
        if (result) {
            sWarningLogged = false;
        } else {
            if (!sWarningLogged) {
                // Log only once to prevent spam.
                Log.w(TAG, "Dialer is not currently set to be default dialer");
                sWarningLogged = true;
            }
        }
        return result;
    }

    private static TelecomManager getTelecomManager(Context context) {
        return (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
    }

    private static TelephonyManager getTelephonyManager(Context context) {
        return (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }
}
