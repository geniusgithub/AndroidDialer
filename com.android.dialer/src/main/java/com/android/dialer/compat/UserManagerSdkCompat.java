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
package com.android.dialer.compat;

import android.content.Context;

/**
 * UserManagerCompat respecting Sdk requirements
 */
public class UserManagerSdkCompat {

    /**
     * Return whether the calling user is running in an "unlocked" state. A user
     * is unlocked only after they've entered their credentials (such as a lock
     * pattern or PIN), and credential-encrypted private app data storage is
     * available.
     *
     * @param context the current context
     * @return {@code true} if the user is unlocked or context is null, {@code false} otherwise
     * @throws NullPointerException if context is null
     */
    public static boolean isUserUnlocked(Context context) {
        return android.support.v4.os.UserManagerCompat.isUserUnlocked(context);
    }

}
