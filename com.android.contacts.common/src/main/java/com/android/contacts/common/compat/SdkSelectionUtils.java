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

/**
 * Provides information for the SDK the app is built against.
 * Specifically, information that change when the TARGET_N_SDK build flag is set in the makefile.
 * This is not related to the targetSdkVersion value in AndroidManifest.xml.
 *
 * Usage case will be branching test code in src/, instead of swapping between src-N and src-pre-N.
 */
public class SdkSelectionUtils {

    /**
     * Whether the app is build against N SDK.
     *
     * Since Build.VERSION.SDK_INT remains 23 on N SDK for now, this is currently the only way to
     * check if we are building with N SDK or other.
     */
    public static final boolean TARGET_N_SDK = true;
}

