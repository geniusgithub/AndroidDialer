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

package com.android.dialer.voicemail;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import com.android.dialer.calllog.CallLogAsyncTaskUtil;
import com.android.dialer.database.VoicemailArchiveContract;
import java.io.FileNotFoundException;
import java.util.concurrent.TimeUnit;

/**
 * Similar to the {@link VoicemailPlaybackPresenter}, but for the archive voicemail tab. It checks
 * whether the voicemail file exists locally before preparing it.
 */
public class VoicemailArchivePlaybackPresenter extends VoicemailPlaybackPresenter {
    private static final String TAG = "VMPlaybackPresenter";
    private static VoicemailPlaybackPresenter sInstance;

    public VoicemailArchivePlaybackPresenter(Activity activity) {
        super(activity);
    }

    public static VoicemailPlaybackPresenter getInstance(
            Activity activity, Bundle savedInstanceState) {
        if (sInstance == null) {
            sInstance = new VoicemailArchivePlaybackPresenter(activity);
        }

        sInstance.init(activity, savedInstanceState);
        return sInstance;
    }

    @Override
    protected void checkForContent(final OnContentCheckedListener callback) {
        mAsyncTaskExecutor.submit(Tasks.CHECK_FOR_CONTENT, new AsyncTask<Void, Void, Boolean>() {
            @Override
            public Boolean doInBackground(Void... params) {
                try {
                    // Check if the _data column of the archived voicemail is valid
                    if (mVoicemailUri != null) {
                        mContext.getContentResolver().openInputStream(mVoicemailUri);
                        return true;
                    }
                } catch (FileNotFoundException e) {
                    Log.d(TAG, "Voicemail file not found for " + mVoicemailUri);
                }
                return false;
            }

            @Override
            public void onPostExecute(Boolean hasContent) {
                callback.onContentChecked(hasContent);
            }
        });
    }

    @Override
    protected void startArchiveVoicemailTask(final Uri voicemailUri, final boolean archivedByUser) {
        // If a user wants to share an archived voicemail, no need for archiving, just go straight
        // to share intent.
        if (!archivedByUser) {
            sendShareIntent(voicemailUri);
        }
    }

    @Override
    protected boolean requestContent(int code) {
        handleError(new FileNotFoundException("Voicemail archive file does not exist"));
        return false;       // No way for archive tab to request content
    }
}
