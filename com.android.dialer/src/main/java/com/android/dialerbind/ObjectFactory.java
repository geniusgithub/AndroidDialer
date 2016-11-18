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

package com.android.dialerbind;

import static com.android.dialer.calllog.CallLogAdapter.CallFetcher;

import android.content.Context;
import android.support.annotation.Nullable;

import com.android.dialer.calllog.CallLogAdapter;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.list.RegularSearchFragment;
import com.android.dialer.logging.Logger;
import com.android.dialer.service.CachedNumberLookupService;
import com.android.dialer.service.ExtendedBlockingButtonRenderer;
import com.android.dialer.voicemail.VoicemailPlaybackPresenter;

/**
 * Default static binding for various objects.
 */
public class ObjectFactory {

    public static CachedNumberLookupService newCachedNumberLookupService() {
        // no-op
        return null;
    }

    public static String getFilteredNumberProviderAuthority() {
        return "com.android.dialer.database.filterednumberprovider";
    }

    public static String getVoicemailArchiveProviderAuthority() {
        return "com.android.dialer.database.voicemailarchiveprovider";
    }

    public static boolean isVoicemailArchiveEnabled(Context context) {
        return false;
    }

    public static boolean isVoicemailShareEnabled(Context context) {
        return false;
    }

    public static boolean isNewBlockingEnabled(Context context) {
        return true;
    }

    @Nullable
    public static ExtendedBlockingButtonRenderer newExtendedBlockingButtonRenderer(
            Context context, ExtendedBlockingButtonRenderer.Listener listener) {
        return null;
    }

    /**
     * Create a new instance of the call log adapter.
     * @param context The context to use.
     * @param callFetcher Instance of call fetcher to use.
     * @param contactInfoHelper Instance of contact info helper class to use.
     * @return Instance of CallLogAdapter.
     */
    public static CallLogAdapter newCallLogAdapter(
            Context context,
            CallFetcher callFetcher,
            ContactInfoHelper contactInfoHelper,
            VoicemailPlaybackPresenter voicemailPlaybackPresenter,
            int activityType) {
        return new CallLogAdapter(
                context,
                callFetcher,
                contactInfoHelper,
                voicemailPlaybackPresenter,
                activityType);
    }

    public static Logger getLoggerInstance() {
        // no-op
        return null;
    }

    public static RegularSearchFragment newRegularSearchFragment() {
        return new RegularSearchFragment();
    }
}
