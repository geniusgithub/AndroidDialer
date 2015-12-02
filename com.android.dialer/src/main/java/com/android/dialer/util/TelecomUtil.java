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

import com.android.contacts.common.util.PermissionsUtil;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.util.Log;

public class TelecomUtil {
    private static final String TAG = "TelecomUtil";
    private static boolean sWarningLogged = false;

    public static void silenceRinger(Context context) {
        if (hasModifyPhoneStatePermission(context)) {
            try {
                getTelecomManager(context).silenceRinger();
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
                return getTelecomManager(context).getAdnUriForPhoneAccount(handle);
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
                if (handle == null) {
                    return getTelecomManager(context).handleMmi(dialString);
                } else {
                    return getTelecomManager(context).handleMmi(dialString, handle);
                }
            } catch (SecurityException e) {
                Log.w(TAG, "TelecomManager.handleMmi called without permission.");
            }
        }
        return false;
    }

    public static Uri getCallLogUri(Context context) {
    	return Calls.CONTENT_URI;// modify by genius
//        return hasReadWriteVoicemailPermissions(context) ? Calls.CONTENT_URI_WITH_VOICEMAIL
//                : Calls.CONTENT_URI;
    }

    public static boolean hasReadWriteVoicemailPermissions(Context context) {
    	return false;
//	modify by genius    	
//        return isDefaultDialer(context)
//                || (hasPermission(context, Manifest.permission.READ_VOICEMAIL)
//                        && hasPermission(context, Manifest.permission.WRITE_VOICEMAIL));
    }

    public static boolean hasModifyPhoneStatePermission(Context context) {
        return isDefaultDialer(context)
                || hasPermission(context, Manifest.permission.MODIFY_PHONE_STATE);
    }

    private static boolean hasPermission(Context context, String permission) {
    	if (!PermissionsUtil.sIsAtLeastM){
    		return true;
    	}
        return context.checkSelfPermission(permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isDefaultDialer(Context context) {
    	return false;
// modify by genius    	
//        final boolean result = TextUtils.equals(context.getPackageName(),
//                getTelecomManager(context).getDefaultDialerPackage());
//        if (result) {
//            sWarningLogged = false;
//        } else {
//            if (!sWarningLogged) {
//                // Log only once to prevent spam.
//                Log.w(TAG, "Dialer is not currently set to be default dialer");
//                sWarningLogged = true;
//            }
//        }
//        return result;
    }

    private static TelecomManager getTelecomManager(Context context) {
        return (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
    }
}
