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
 * limitations under the License
 */

package com.android.dialer.calllog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.VoicemailContract;
import android.util.Log;

/**
 * Receiver for call log events.
 * <p>
 * It is currently used to handle {@link VoicemailContract#ACTION_NEW_VOICEMAIL} and
 * {@link Intent#ACTION_BOOT_COMPLETED}.
 */
public class CallLogReceiver extends BroadcastReceiver {
    private static final String TAG = "CallLogReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (VoicemailContract.ACTION_NEW_VOICEMAIL.equals(intent.getAction())) {
            CallLogNotificationsService.updateVoicemailNotifications(context, intent.getData());
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            CallLogNotificationsService.updateVoicemailNotifications(context, null);
        } else {
            Log.w(TAG, "onReceive: could not handle: " + intent);
        }
    }
}
