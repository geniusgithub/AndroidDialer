/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.contacts.common.compat;

import android.telecom.Call;

public class CallSdkCompat {
    public static class Details {
        public static final int PROPERTY_ENTERPRISE_CALL = Call.Details.PROPERTY_ENTERPRISE_CALL;
        public static final int CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO =
                Call.Details.CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO;
    }
}
