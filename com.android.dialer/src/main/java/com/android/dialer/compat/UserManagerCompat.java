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
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.contacts.common.compat.CompatUtils;

/**
 * Compatibility class for {@link UserManager}.
 */
public class UserManagerCompat {
    /**
     * A user id constant to indicate the "system" user of the device. Copied from
     * {@link UserHandle}.
     */
    private static final int USER_SYSTEM = 0;
    /**
     * Range of uids allocated for a user.
     */
    private static final int PER_USER_RANGE = 100000;

    /**
     * Used to check if this process is running under the system user. The system user is the
     * initial user that is implicitly created on first boot and hosts most of the system services.
     *
     * @return whether this process is running under the system user.
     */
    public static boolean isSystemUser(UserManager userManager) {
        if (CompatUtils.isMarshmallowCompatible()) {
            return userManager.isSystemUser();
        }
        // Adapted from {@link UserManager} and {@link UserHandle}.
        return (Process.myUid() / PER_USER_RANGE) == USER_SYSTEM;
    }

    /**
     * Return whether the calling user is running in an "unlocked" state. A user
     * is unlocked only after they've entered their credentials (such as a lock
     * pattern or PIN), and credential-encrypted private app data storage is
     * available.
     *
     * TODO b/26688153
     *
     * @param context the current context
     * @return {@code true} if the user is unlocked, {@code false} otherwise
     * @throws NullPointerException if context is null
     */
    public static boolean isUserUnlocked(Context context) {
        if (CompatUtils.isNCompatible()) {
            return UserManagerSdkCompat.isUserUnlocked(context);
        }
        return true;
    }
}
