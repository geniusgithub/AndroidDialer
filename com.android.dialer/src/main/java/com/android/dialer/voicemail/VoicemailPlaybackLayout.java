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

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.AttributeSet;
import android.support.design.widget.Snackbar;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import com.android.common.io.MoreCloseables;
import com.android.dialer.PhoneCallDetails;
import com.android.dialer.R;
import com.android.dialer.calllog.CallLogAsyncTaskUtil;

import com.android.dialer.database.VoicemailArchiveContract;
import com.android.dialer.database.VoicemailArchiveContract.VoicemailArchive;
import com.android.dialer.util.AsyncTaskExecutor;
import com.android.dialer.util.AsyncTaskExecutors;
import com.android.dialerbind.ObjectFactory;
import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Displays and plays a single voicemail. See {@link VoicemailPlaybackPresenter} for
 * details on the voicemail playback implementation.
 *
 * This class is not thread-safe, it is thread-confined. All calls to all public
 * methods on this class are expected to come from the main ui thread.
 */
@NotThreadSafe
public class VoicemailPlaybackLayout extends LinearLayout
        implements VoicemailPlaybackPresenter.PlaybackView,
        CallLogAsyncTaskUtil.CallLogAsyncTaskListener {
    private static final String TAG = VoicemailPlaybackLayout.class.getSimpleName();
    private static final int VOICEMAIL_DELETE_DELAY_MS = 3000;
    private static final int VOICEMAIL_ARCHIVE_DELAY_MS = 3000;

    /** The enumeration of {@link AsyncTask} objects we use in this class. */
    public enum Tasks {
        QUERY_ARCHIVED_STATUS
    }

    /**
     * Controls the animation of the playback slider.
     */
    @ThreadSafe
    private final class PositionUpdater implements Runnable {

        /** Update rate for the slider, 30fps. */
        private static final int SLIDER_UPDATE_PERIOD_MILLIS = 1000 / 30;

        private int mDurationMs;
        private final ScheduledExecutorService mExecutorService;
        private final Object mLock = new Object();
        @GuardedBy("mLock") private ScheduledFuture<?> mScheduledFuture;

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
            // Update the seek position if user manually changed it. This makes sure position gets
            // updated when user use volume button to seek playback in talkback mode.
            if (fromUser) {
                mPresenter.seek(progress);
            }
        }
    };

    /**
     * Click listener to toggle speakerphone.
     */
    private final View.OnClickListener mSpeakerphoneListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mPresenter != null) {
                mPresenter.toggleSpeakerphone();
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
            mPresenter.onVoicemailDeleted();

            final Uri deleteUri = mVoicemailUri;
            final Runnable deleteCallback = new Runnable() {
                @Override
                public void run() {
                    if (Objects.equals(deleteUri, mVoicemailUri)) {
                        CallLogAsyncTaskUtil.deleteVoicemail(mContext, deleteUri,
                                VoicemailPlaybackLayout.this);
                    }
                }
            };

            final Handler handler = new Handler();
            // Add a little buffer time in case the user clicked "undo" at the end of the delay
            // window.
            handler.postDelayed(deleteCallback, VOICEMAIL_DELETE_DELAY_MS + 50);

            Snackbar.make(VoicemailPlaybackLayout.this, R.string.snackbar_voicemail_deleted,
                            Snackbar.LENGTH_LONG)
                    .setDuration(VOICEMAIL_DELETE_DELAY_MS)
                    .setAction(R.string.snackbar_voicemail_deleted_undo,
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    mPresenter.onVoicemailDeleteUndo();
                                        handler.removeCallbacks(deleteCallback);
                                }
                            })
                    .setActionTextColor(
                            mContext.getResources().getColor(
                                    R.color.dialer_snackbar_action_text_color))
                    .show();
        }
    };

    private final View.OnClickListener mArchiveButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mPresenter == null || isArchiving(mVoicemailUri)) {
                return;
            }
            mIsArchiving.add(mVoicemailUri);
            mPresenter.pausePlayback();
            updateArchiveUI(mVoicemailUri);
            disableUiElements();
            mPresenter.archiveContent(mVoicemailUri, true);
        }
    };

    private final View.OnClickListener mShareButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mPresenter == null || isArchiving(mVoicemailUri)) {
                return;
            }
            disableUiElements();
            mPresenter.archiveContent(mVoicemailUri, false);
        }
    };

    private Context mContext;
    private VoicemailPlaybackPresenter mPresenter;
    private Uri mVoicemailUri;
    private final AsyncTaskExecutor mAsyncTaskExecutor =
            AsyncTaskExecutors.createAsyncTaskExecutor();
    private boolean mIsPlaying = false;
    /**
     * Keeps track of which voicemails are currently being archived in order to update the voicemail
     * card UI every time a user opens a new card.
     */
    private static final ArrayList<Uri> mIsArchiving = new ArrayList<>();

    private SeekBar mPlaybackSeek;
    private ImageButton mStartStopButton;
    private ImageButton mPlaybackSpeakerphone;
    private ImageButton mDeleteButton;
    private ImageButton mArchiveButton;
    private ImageButton mShareButton;

    private Space mArchiveSpace;
    private Space mShareSpace;

    private TextView mStateText;
    private TextView mPositionText;
    private TextView mTotalDurationText;

    private PositionUpdater mPositionUpdater;
    private Drawable mVoicemailSeekHandleEnabled;
    private Drawable mVoicemailSeekHandleDisabled;

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
        if (ObjectFactory.isVoicemailArchiveEnabled(mContext)) {
            updateArchiveUI(mVoicemailUri);
            updateArchiveButton(mVoicemailUri);
        }

        if (ObjectFactory.isVoicemailShareEnabled(mContext)) {
            // Show share button and space before it
            mShareSpace.setVisibility(View.VISIBLE);
            mShareButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mPlaybackSeek = (SeekBar) findViewById(R.id.playback_seek);
        mStartStopButton = (ImageButton) findViewById(R.id.playback_start_stop);
        mPlaybackSpeakerphone = (ImageButton) findViewById(R.id.playback_speakerphone);
        mDeleteButton = (ImageButton) findViewById(R.id.delete_voicemail);
        mArchiveButton =(ImageButton) findViewById(R.id.archive_voicemail);
        mShareButton = (ImageButton) findViewById(R.id.share_voicemail);

        mArchiveSpace = (Space) findViewById(R.id.space_before_archive_voicemail);
        mShareSpace = (Space) findViewById(R.id.space_before_share_voicemail);

        mStateText = (TextView) findViewById(R.id.playback_state_text);
        mPositionText = (TextView) findViewById(R.id.playback_position_text);
        mTotalDurationText = (TextView) findViewById(R.id.total_duration_text);

        mPlaybackSeek.setOnSeekBarChangeListener(mSeekBarChangeListener);
        mStartStopButton.setOnClickListener(mStartStopButtonListener);
        mPlaybackSpeakerphone.setOnClickListener(mSpeakerphoneListener);
        mDeleteButton.setOnClickListener(mDeleteButtonListener);
        mArchiveButton.setOnClickListener(mArchiveButtonListener);
        mShareButton.setOnClickListener(mShareButtonListener);

        mPositionText.setText(formatAsMinutesAndSeconds(0));
        mTotalDurationText.setText(formatAsMinutesAndSeconds(0));

        mVoicemailSeekHandleEnabled = getResources().getDrawable(
                R.drawable.ic_voicemail_seek_handle, mContext.getTheme());
        mVoicemailSeekHandleDisabled = getResources().getDrawable(
                R.drawable.ic_voicemail_seek_handle_disabled, mContext.getTheme());
    }

    @Override
    public void onPlaybackStarted(int duration, ScheduledExecutorService executorService) {
        mIsPlaying = true;

        mStartStopButton.setImageResource(R.drawable.ic_pause);

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

    @Override
    public void onSpeakerphoneOn(boolean on) {
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
    }

    @Override
    public void setSuccess() {
        mStateText.setText(null);
    }

    @Override
    public void setIsFetchingContent() {
        disableUiElements();
        mStateText.setText(getString(R.string.voicemail_fetching_content));
    }

    @Override
    public void setFetchContentTimeout() {
        mStartStopButton.setEnabled(true);
        mStateText.setText(getString(R.string.voicemail_fetching_timout));
    }

    @Override
    public int getDesiredClipPosition() {
        return mPlaybackSeek.getProgress();
    }

    @Override
    public void disableUiElements() {
        mStartStopButton.setEnabled(false);
        resetSeekBar();
    }

    @Override
    public void enableUiElements() {
        mDeleteButton.setEnabled(true);
        mStartStopButton.setEnabled(true);
        mPlaybackSeek.setEnabled(true);
        mPlaybackSeek.setThumb(mVoicemailSeekHandleEnabled);
    }

    @Override
    public void resetSeekBar() {
        mPlaybackSeek.setProgress(0);
        mPlaybackSeek.setEnabled(false);
        mPlaybackSeek.setThumb(mVoicemailSeekHandleDisabled);
    }

    @Override
    public void onDeleteCall() {}

    @Override
    public void onDeleteVoicemail() {
        mPresenter.onVoicemailDeletedInDatabase();
    }

    @Override
    public void onGetCallDetails(PhoneCallDetails[] details) {}

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

    /**
     * Called when a voicemail archive succeeded. If the expanded voicemail was being
     * archived, update the card UI. Either way, display a snackbar linking user to archive.
     */
    @Override
    public void onVoicemailArchiveSucceded(Uri voicemailUri) {
        if (isArchiving(voicemailUri)) {
            mIsArchiving.remove(voicemailUri);
            if (Objects.equals(voicemailUri, mVoicemailUri)) {
                onVoicemailArchiveResult();
                hideArchiveButton();
            }
        }

        Snackbar.make(this, R.string.snackbar_voicemail_archived,
                Snackbar.LENGTH_LONG)
                .setDuration(VOICEMAIL_ARCHIVE_DELAY_MS)
                .setAction(R.string.snackbar_voicemail_archived_goto,
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                Intent intent = new Intent(mContext,
                                        VoicemailArchiveActivity.class);
                                mContext.startActivity(intent);
                            }
                        })
                .setActionTextColor(
                        mContext.getResources().getColor(R.color.dialer_snackbar_action_text_color))
                .show();
    }

    /**
     * If a voicemail archive failed, and the expanded card was being archived, update the card UI.
     * Either way, display a toast saying the voicemail archive failed.
     */
    @Override
    public void onVoicemailArchiveFailed(Uri voicemailUri) {
        if (isArchiving(voicemailUri)) {
            mIsArchiving.remove(voicemailUri);
            if (Objects.equals(voicemailUri, mVoicemailUri)) {
                onVoicemailArchiveResult();
            }
        }
        String toastStr = mContext.getString(R.string.voicemail_archive_failed);
        Toast.makeText(mContext, toastStr, Toast.LENGTH_SHORT).show();
    }

    public void hideArchiveButton() {
        mArchiveSpace.setVisibility(View.GONE);
        mArchiveButton.setVisibility(View.GONE);
        mArchiveButton.setClickable(false);
        mArchiveButton.setEnabled(false);
    }

    /**
     * Whenever a voicemail archive succeeds or fails, clear the text displayed in the voicemail
     * card.
     */
    private void onVoicemailArchiveResult() {
        enableUiElements();
        mStateText.setText(null);
        mArchiveButton.setColorFilter(null);
    }

    /**
     * Whether or not the voicemail with the given uri is being archived.
     */
    private boolean isArchiving(@Nullable Uri uri) {
        return uri != null && mIsArchiving.contains(uri);
    }

    /**
     * Show the proper text and hide the archive button if the voicemail is still being archived.
     */
    private void updateArchiveUI(@Nullable Uri voicemailUri) {
        if (!Objects.equals(voicemailUri, mVoicemailUri)) {
            return;
        }
        if (isArchiving(voicemailUri)) {
            // If expanded card was in the middle of archiving, disable buttons and display message
            disableUiElements();
            mDeleteButton.setEnabled(false);
            mArchiveButton.setColorFilter(getResources().getColor(R.color.setting_disabled_color));
            mStateText.setText(getString(R.string.voicemail_archiving_content));
        } else {
            onVoicemailArchiveResult();
        }
    }

    /**
     * Hides the archive button if the voicemail has already been archived, shows otherwise.
     * @param voicemailUri the URI of the voicemail for which the archive button needs to be updated
     */
    private void updateArchiveButton(@Nullable final Uri voicemailUri) {
        if (voicemailUri == null ||
                !Objects.equals(voicemailUri, mVoicemailUri) || isArchiving(voicemailUri) ||
                Objects.equals(voicemailUri.getAuthority(),VoicemailArchiveContract.AUTHORITY)) {
            return;
        }
        mAsyncTaskExecutor.submit(Tasks.QUERY_ARCHIVED_STATUS,
                new AsyncTask<Void, Void, Boolean>() {
            @Override
            public Boolean doInBackground(Void... params) {
                Cursor cursor = mContext.getContentResolver().query(VoicemailArchive.CONTENT_URI,
                        null, VoicemailArchive.SERVER_ID + "=" + ContentUris.parseId(mVoicemailUri)
                        + " AND " + VoicemailArchive.ARCHIVED + "= 1", null, null);
                boolean archived = cursor != null && cursor.getCount() > 0;
                cursor.close();
                return archived;
            }

            @Override
            public void onPostExecute(Boolean archived) {
                if (!Objects.equals(voicemailUri, mVoicemailUri)) {
                    return;
                }

                if (archived) {
                    hideArchiveButton();
                } else {
                    mArchiveSpace.setVisibility(View.VISIBLE);
                    mArchiveButton.setVisibility(View.VISIBLE);
                    mArchiveButton.setClickable(true);
                    mArchiveButton.setEnabled(true);
                }

            }
        });
    }

    @VisibleForTesting
    public String getStateText() {
        return mStateText.getText().toString();
    }
}
