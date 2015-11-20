/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.app.Fragment;
import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.VoicemailContract;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.android.common.io.MoreCloseables;
import com.android.dialer.R;
import com.android.dialer.calllog.CallLogAsyncTaskUtil;

import com.google.common.base.Preconditions;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;

//import javax.annotation.concurrent.GuardedBy;
//import javax.annotation.concurrent.NotThreadSafe;
//import javax.annotation.concurrent.ThreadSafe;

/**
 * Displays and plays a single voicemail. See {@link VoicemailPlaybackPresenter} for
 * details on the voicemail playback implementation.
 *
 * This class is not thread-safe, it is thread-confined. All calls to all public
 * methods on this class are expected to come from the main ui thread.
 */
//@NotThreadSafe
public class VoicemailPlaybackLayout extends LinearLayout
        implements VoicemailPlaybackPresenter.PlaybackView {
    private static final String TAG = VoicemailPlaybackLayout.class.getSimpleName();

    /**
     * Controls the animation of the playback slider.
     */
//    @ThreadSafe
    private final class PositionUpdater implements Runnable {

        /** Update rate for the slider, 30fps. */
        private static final int SLIDER_UPDATE_PERIOD_MILLIS = 1000 / 30;

        private int mDurationMs;
        private final ScheduledExecutorService mExecutorService;
        private final Object mLock = new Object();
//        @GuardedBy("mLock") private ScheduledFuture<?> mScheduledFuture;
        private ScheduledFuture<?> mScheduledFuture;
        private Runnable mUpdateClipPositionRunnable = new Runnable() {
            @Override
            public void run() {
                int currentPositionMs = 0;
                synchronized (mLock) {
                    if (mScheduledFuture == null || mPresenter == null) {
                        // This task has been canceled. Just stop now.
                        return;
                    }
                    currentPositionMs = mPresenter.getMediaPlayerPosition();
                }
                setClipPosition(currentPositionMs, mDurationMs);
            }
        };

        public PositionUpdater(int durationMs, ScheduledExecutorService executorService) {
            mDurationMs = durationMs;
            mExecutorService = executorService;
        }

        @Override
        public void run() {
            post(mUpdateClipPositionRunnable);
        }

        public void startUpdating() {
            synchronized (mLock) {
                cancelPendingRunnables();
                mScheduledFuture = mExecutorService.scheduleAtFixedRate(
                        this, 0, SLIDER_UPDATE_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
            }
        }

        public void stopUpdating() {
            synchronized (mLock) {
                cancelPendingRunnables();
            }
        }

        private void cancelPendingRunnables() {
            if (mScheduledFuture != null) {
                mScheduledFuture.cancel(true);
                mScheduledFuture = null;
            }
            removeCallbacks(mUpdateClipPositionRunnable);
        }
    }

    /**
     * Handle state changes when the user manipulates the seek bar.
     */
    private final OnSeekBarChangeListener mSeekBarChangeListener = new OnSeekBarChangeListener() {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            if (mPresenter != null) {
                mPresenter.pausePlaybackForSeeking();
            }
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            if (mPresenter != null) {
                mPresenter.resumePlaybackAfterSeeking(seekBar.getProgress());
            }
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            setClipPosition(progress, seekBar.getMax());
        }
    };

    /**
     * Click listener to toggle speakerphone.
     */
    private final View.OnClickListener mSpeakerphoneListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mPresenter != null) {
                onSpeakerphoneOn(!mPresenter.isSpeakerphoneOn());
            }
        }
    };

    /**
     * Click listener to play or pause voicemail playback.
     */
    private final View.OnClickListener mStartStopButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mPresenter == null) {
                return;
            }

            if (mIsPlaying) {
                mPresenter.pausePlayback();
            } else {
                mPresenter.resumePlayback();
            }
        }
    };

    private final View.OnClickListener mDeleteButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View view ) {
            if (mPresenter == null) {
                return;
            }
            mPresenter.pausePlayback();
            CallLogAsyncTaskUtil.deleteVoicemail(mContext, mVoicemailUri, null);
            mPresenter.onVoicemailDeleted();
        }
    };

    private Context mContext;
    private VoicemailPlaybackPresenter mPresenter;
    private Uri mVoicemailUri;

    private boolean mIsPlaying = false;

    private SeekBar mPlaybackSeek;
    private ImageButton mStartStopButton;
    private ImageButton mPlaybackSpeakerphone;
    private ImageButton mDeleteButton;
    private TextView mStateText;
    private TextView mPositionText;
    private TextView mTotalDurationText;

    private PositionUpdater mPositionUpdater;

    public VoicemailPlaybackLayout(Context context) {
        this(context, null);
    }

    public VoicemailPlaybackLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;
        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.voicemail_playback_layout, this);
    }

    @Override
    public void setPresenter(VoicemailPlaybackPresenter presenter, Uri voicemailUri) {
        mPresenter = presenter;
        mVoicemailUri = voicemailUri;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mPlaybackSeek = (SeekBar) findViewById(R.id.playback_seek);
        mStartStopButton = (ImageButton) findViewById(R.id.playback_start_stop);
        mPlaybackSpeakerphone = (ImageButton) findViewById(R.id.playback_speakerphone);
        mDeleteButton = (ImageButton) findViewById(R.id.delete_voicemail);
        mStateText = (TextView) findViewById(R.id.playback_state_text);
        mPositionText = (TextView) findViewById(R.id.playback_position_text);
        mTotalDurationText = (TextView) findViewById(R.id.total_duration_text);

        mPlaybackSeek.setOnSeekBarChangeListener(mSeekBarChangeListener);
        mStartStopButton.setOnClickListener(mStartStopButtonListener);
        mPlaybackSpeakerphone.setOnClickListener(mSpeakerphoneListener);
        mDeleteButton.setOnClickListener(mDeleteButtonListener);
    }

    @Override
    public void onPlaybackStarted(int duration, ScheduledExecutorService executorService) {
        mIsPlaying = true;

        mStartStopButton.setImageResource(R.drawable.ic_pause);

        if (mPresenter != null) {
            onSpeakerphoneOn(mPresenter.isSpeakerphoneOn());
        }

        if (mPositionUpdater != null) {
            mPositionUpdater.stopUpdating();
            mPositionUpdater = null;
        }
        mPositionUpdater = new PositionUpdater(duration, executorService);
        mPositionUpdater.startUpdating();
    }

    @Override
    public void onPlaybackStopped() {
        mIsPlaying = false;

        mStartStopButton.setImageResource(R.drawable.ic_play_arrow);

        if (mPositionUpdater != null) {
            mPositionUpdater.stopUpdating();
            mPositionUpdater = null;
        }
    }

    @Override
    public void onPlaybackError() {
        if (mPositionUpdater != null) {
            mPositionUpdater.stopUpdating();
        }

        disableUiElements();
        mStateText.setText(getString(R.string.voicemail_playback_error));
    }


    public void onSpeakerphoneOn(boolean on) {
        if (mPresenter != null) {
            mPresenter.setSpeakerphoneOn(on);
        }

        if (on) {
            mPlaybackSpeakerphone.setImageResource(R.drawable.ic_volume_up_24dp);
            // Speaker is now on, tapping button will turn it off.
            mPlaybackSpeakerphone.setContentDescription(
                    mContext.getString(R.string.voicemail_speaker_off));
        } else {
            mPlaybackSpeakerphone.setImageResource(R.drawable.ic_volume_down_24dp);
            // Speaker is now off, tapping button will turn it on.
            mPlaybackSpeakerphone.setContentDescription(
                    mContext.getString(R.string.voicemail_speaker_on));
        }
    }

    @Override
    public void setClipPosition(int positionMs, int durationMs) {
        int seekBarPositionMs = Math.max(0, positionMs);
        int seekBarMax = Math.max(seekBarPositionMs, durationMs);
        if (mPlaybackSeek.getMax() != seekBarMax) {
            mPlaybackSeek.setMax(seekBarMax);
        }

        mPlaybackSeek.setProgress(seekBarPositionMs);

        mPositionText.setText(formatAsMinutesAndSeconds(seekBarPositionMs));
        mTotalDurationText.setText(formatAsMinutesAndSeconds(durationMs));
        mStateText.setText(null);
    }

    @Override
    public void setIsBuffering() {
        disableUiElements();
        mStateText.setText(getString(R.string.voicemail_buffering));
    }

    @Override
    public void setIsFetchingContent() {
        disableUiElements();
        mStateText.setText(getString(R.string.voicemail_fetching_content));
    }

    @Override
    public void setFetchContentTimeout() {
        disableUiElements();
        mStateText.setText(getString(R.string.voicemail_fetching_timout));
    }

    @Override
    public int getDesiredClipPosition() {
        return mPlaybackSeek.getProgress();
    }

    @Override
    public void disableUiElements() {
        mStartStopButton.setEnabled(false);
        mPlaybackSpeakerphone.setEnabled(false);
        mPlaybackSeek.setProgress(0);
        mPlaybackSeek.setEnabled(false);

        mPositionText.setText(formatAsMinutesAndSeconds(0));
        mTotalDurationText.setText(formatAsMinutesAndSeconds(0));
    }

    @Override
    public void enableUiElements() {
        mStartStopButton.setEnabled(true);
        mPlaybackSpeakerphone.setEnabled(true);
        mPlaybackSeek.setEnabled(true);
    }

    private String getString(int resId) {
        return mContext.getString(resId);
    }

    /**
     * Formats a number of milliseconds as something that looks like {@code 00:05}.
     * <p>
     * We always use four digits, two for minutes two for seconds.  In the very unlikely event
     * that the voicemail duration exceeds 99 minutes, the display is capped at 99 minutes.
     */
    private String formatAsMinutesAndSeconds(int millis) {
        int seconds = millis / 1000;
        int minutes = seconds / 60;
        seconds -= minutes * 60;
        if (minutes > 99) {
            minutes = 99;
        }
        return String.format("%02d:%02d", minutes, seconds);
    }
}
