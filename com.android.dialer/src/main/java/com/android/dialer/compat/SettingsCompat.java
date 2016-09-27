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
package com.android.dialer.compat;

import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.provider.Settings;

import com.android.contacts.common.compat.SdkVersionOverride;

/**
 * Compatibility class for {@link android.provider.Settings}
 */
public class SettingsCompat {

    public static class System {

        /**
         * Compatibility version of {@link android.provider.Settings.System#canWrite(Context)}
         *
         * Note: Since checking preferences at runtime started in M, this method always returns
         * {@code true} for SDK versions prior to 23. In those versions, the app wouldn't be
         * installed if it didn't have the proper permission
         */
        public static boolean canWrite(Context context) {
            if (SdkVersionOverride.getSdkVersion(VERSION_CODES.LOLLIPOP) >= Build.VERSION_CODES.M) {
                return Settings.System.canWrite(context);
            }
            return true;
        }
    }

}
