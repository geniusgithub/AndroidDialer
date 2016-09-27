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

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.telecom.CallAudioState;
import android.util.Log;

import java.util.concurrent.RejectedExecutionException;

/**
 * This class manages all audio changes for voicemail playback.
 */
final class VoicemailAudioManager implements OnAudioFocusChangeListener,
        WiredHeadsetManager.Listener {
    private static final String TAG = VoicemailAudioManager.class.getSimpleName();

    public static final int PLAYBACK_STREAM = AudioManager.STREAM_VOICE_CALL;

    private AudioManager mAudioManager;
    private VoicemailPlaybackPresenter mVoicemailPlaybackPresenter;
    private WiredHeadsetManager mWiredHeadsetManager;
    private boolean mWasSpeakerOn;
    private CallAudioState mCallAudioState;

    public VoicemailAudioManager(Context context,
            VoicemailPlaybackPresenter voicemailPlaybackPresenter) {
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mVoicemailPlaybackPresenter = voicemailPlaybackPresenter;
        mWiredHeadsetManager = new WiredHeadsetManager(context);
        mWiredHeadsetManager.setListener(this);

        mCallAudioState = getInitialAudioState();
        Log.i(TAG, "Initial audioState = " + mCallAudioState);
    }

    public void requestAudioFocus() {
        int result = mAudioManager.requestAudioFocus(
                this,
                PLAYBACK_STREAM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            throw new RejectedExecutionException("Could not capture audio focus.");
        }
    }

    public void abandonAudioFocus() {
        mAudioManager.abandonAudioFocus(this);
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        Log.d(TAG, "onAudioFocusChange: focusChange=" + focusChange);
        mVoicemailPlaybackPresenter.onAudioFocusChange(focusChange == AudioManager.AUDIOFOCUS_GAIN);
    }

    @Override
    public void onWiredHeadsetPluggedInChanged(boolean oldIsPluggedIn, boolean newIsPluggedIn) {
        Log.i(TAG, "wired headset was plugged in changed: " + oldIsPluggedIn
                + " -> "+ newIsPluggedIn);

        if (oldIsPluggedIn == newIsPluggedIn) {
            return;
        }

        int newRoute = mCallAudioState.getRoute();  // start out with existing route
        if (newIsPluggedIn) {
            newRoute = CallAudioState.ROUTE_WIRED_HEADSET;
        } else {
            if (mWasSpeakerOn) {
                newRoute = CallAudioState.ROUTE_SPEAKER;
            } else {
                newRoute = CallAudioState.ROUTE_EARPIECE;
            }
        }

        mVoicemailPlaybackPresenter.setSpeakerphoneOn(newRoute == CallAudioState.ROUTE_SPEAKER);

        // We need to call this every time even if we do not change the route because the supported
        // routes changed either to include or not include WIRED_HEADSET.
        setSystemAudioState(
                new CallAudioState(false /* muted */, newRoute, calculateSupportedRoutes()));
    }

    public void setSpeakerphoneOn(boolean on) {
        setAudioRoute(on ? CallAudioState.ROUTE_SPEAKER : CallAudioState.ROUTE_WIRED_OR_EARPIECE);
    }

    public boolean isWiredHeadsetPluggedIn() {
        return mWiredHeadsetManager.isPluggedIn();
    }

    public void registerReceivers() {
        // Receivers is plural because we expect to add bluetooth support.
        mWiredHeadsetManager.registerReceiver();
    }

    public void unregisterReceivers() {
        mWiredHeadsetManager.unregisterReceiver();
    }

    /**
     * Change the audio route, for example from earpiece to speakerphone.
     *
     * @param route The new audio route to use. See {@link CallAudioState}.
     */
    void setAudioRoute(int route) {
        Log.v(TAG, "setAudioRoute, route: " + CallAudioState.audioRouteToString(route));

        // Change ROUTE_WIRED_OR_EARPIECE to a single entry.
        int newRoute = selectWiredOrEarpiece(route, mCallAudioState.getSupportedRouteMask());

        // If route is unsupported, do nothing.
        if ((mCallAudioState.getSupportedRouteMask() | newRoute) == 0) {
            Log.w(TAG, "Asking to set to a route that is unsupported: " + newRoute);
            return;
        }

        if (mCallAudioState.getRoute() != newRoute) {
            // Remember the new speaker state so it can be restored when the user plugs and unplugs
            // a headset.
            mWasSpeakerOn = newRoute == CallAudioState.ROUTE_SPEAKER;
            setSystemAudioState(new CallAudioState(false /* muted */, newRoute,
                    mCallAudioState.getSupportedRouteMask()));
        }
    }

    private CallAudioState getInitialAudioState() {
        int supportedRouteMask = calculateSupportedRoutes();
        int route = selectWiredOrEarpiece(CallAudioState.ROUTE_WIRED_OR_EARPIECE,
                supportedRouteMask);
        return new CallAudioState(false /* muted */, route, supportedRouteMask);
    }

    private int calculateSupportedRoutes() {
        int routeMask = CallAudioState.ROUTE_SPEAKER;
        if (mWiredHeadsetManager.isPluggedIn()) {
            routeMask |= CallAudioState.ROUTE_WIRED_HEADSET;
        } else {
            routeMask |= CallAudioState.ROUTE_EARPIECE;
        }
        return routeMask;
    }

    private int selectWiredOrEarpiece(int route, int supportedRouteMask) {
        // Since they are mutually exclusive and one is ALWAYS valid, we allow a special input of
        // ROUTE_WIRED_OR_EARPIECE so that callers don't have to make a call to check which is
        // supported before calling setAudioRoute.
        if (route == CallAudioState.ROUTE_WIRED_OR_EARPIECE) {
            route = CallAudioState.ROUTE_WIRED_OR_EARPIECE & supportedRouteMask;
            if (route == 0) {
                Log.wtf(TAG, "One of wired headset or earpiece should always be valid.");
                // assume earpiece in this case.
                route = CallAudioState.ROUTE_EARPIECE;
            }
        }
        return route;
    }

    private void setSystemAudioState(CallAudioState callAudioState) {
        CallAudioState oldAudioState = mCallAudioState;
        mCallAudioState = callAudioState;

        Log.i(TAG, "setSystemAudioState: changing from " + oldAudioState + " to "
                + mCallAudioState);

        // Audio route.
        if (mCallAudioState.getRoute() == CallAudioState.ROUTE_SPEAKER) {
            turnOnSpeaker(true);
        } else if (mCallAudioState.getRoute() == CallAudioState.ROUTE_EARPIECE ||
                mCallAudioState.getRoute() == CallAudioState.ROUTE_WIRED_HEADSET) {
            // Just handle turning off the speaker, the system will handle switching between wired
            // headset and earpiece.
            turnOnSpeaker(false);
        }
    }

    private void turnOnSpeaker(boolean on) {
        if (mAudioManager.isSpeakerphoneOn() != on) {
            Log.i(TAG, "turning speaker phone on: " + on);
            mAudioManager.setSpeakerphoneOn(on);
        }
    }
}
