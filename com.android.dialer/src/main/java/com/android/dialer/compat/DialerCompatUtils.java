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

import com.android.contacts.common.compat.CompatUtils;

public final class DialerCompatUtils {
    /**
     * Determines if this version has access to the
     * {@link android.provider.CallLog.Calls.CACHED_PHOTO_URI} column
     *
     * @return {@code true} if {@link android.provider.CallLog.Calls.CACHED_PHOTO_URI} is available,
     * {@code false} otherwise
     */
    public static boolean isCallsCachedPhotoUriCompatible() {
        return CompatUtils.isMarshmallowCompatible();
    }
}