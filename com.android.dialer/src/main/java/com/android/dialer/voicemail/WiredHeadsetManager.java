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
package com.android.dialer.voicemail;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.util.Log;

/** Listens for and caches headset state. */
class WiredHeadsetManager {
    private static final String TAG = WiredHeadsetManager.class.getSimpleName();

    interface Listener {
        void onWiredHeadsetPluggedInChanged(boolean oldIsPluggedIn, boolean newIsPluggedIn);
    }

    /** Receiver for wired headset plugged and unplugged events. */
    private class WiredHeadsetBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_HEADSET_PLUG.equals(intent.getAction())) {
                boolean isPluggedIn = intent.getIntExtra("state", 0) == 1;
                Log.v(TAG, "ACTION_HEADSET_PLUG event, plugged in: " + isPluggedIn);
                onHeadsetPluggedInChanged(isPluggedIn);
            }
        }
    }

    private final WiredHeadsetBroadcastReceiver mReceiver;
    private boolean mIsPluggedIn;
    private Listener mListener;
    private Context mContext;

    WiredHeadsetManager(Context context) {
        mContext = context;
        mReceiver = new WiredHeadsetBroadcastReceiver();

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mIsPluggedIn = audioManager.isWiredHeadsetOn();

    }

    void setListener(Listener listener) {
        mListener = listener;
    }

    boolean isPluggedIn() {
        return mIsPluggedIn;
    }

    void registerReceiver() {
        // Register for misc other intent broadcasts.
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        mContext.registerReceiver(mReceiver, intentFilter);
    }

    void unregisterReceiver() {
        mContext.unregisterReceiver(mReceiver);
    }

    private void onHeadsetPluggedInChanged(boolean isPluggedIn) {
        if (mIsPluggedIn != isPluggedIn) {
            Log.v(TAG, "onHeadsetPluggedInChanged, mIsPluggedIn: " + mIsPluggedIn + " -> "
                    + isPluggedIn);
            boolean oldIsPluggedIn = mIsPluggedIn;
            mIsPluggedIn = isPluggedIn;
            if (mListener != null) {
                mListener.onWiredHeadsetPluggedInChanged(oldIsPluggedIn, mIsPluggedIn);
            }
        }
    }
}