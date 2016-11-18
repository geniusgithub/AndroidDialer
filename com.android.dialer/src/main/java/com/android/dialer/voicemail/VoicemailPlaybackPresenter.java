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

import com.google.common.annotations.VisibleForTesting;

import android.app.Activity;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.VoicemailContract;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.WindowManager.LayoutParams;

import com.android.dialer.R;
import com.android.dialer.calllog.CallLogAsyncTaskUtil;
import com.android.dialer.util.AsyncTaskExecutor;
import com.android.dialer.util.AsyncTaskExecutors;
import com.android.common.io.MoreCloseables;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Contains the controlling logic for a voicemail playback in the call log. It is closely coupled
 * to assumptions about the behaviors and lifecycle of the call log, in particular in the
 * {@link CallLogFragment} and {@link CallLogAdapter}.
 * <p>
 * This controls a single {@link com.android.dialer.voicemail.VoicemailPlaybackLayout}. A single
 * instance can be reused for different such layouts, using {@link #setPlaybackView}. This
 * is to facilitate reuse across different voicemail call log entries.
 * <p>
 * This class is not thread safe. The thread policy for this class is thread-confinement, all calls
 * into this class from outside must be done from the main UI thread.
 */
@NotThreadSafe
@VisibleForTesting
public class VoicemailPlaybackPresenter implements MediaPlayer.OnPreparedListener,
                MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    private static final String TAG = "VmPlaybackPresenter";

    /** Contract describing the behaviour we need from the ui we are controlling. */
    public interface PlaybackView {
        int getDesiredClipPosition();
        void disableUiElements();
        void enableUiElements();
        void onPlaybackError();
        void onPlaybackStarted(int duration, ScheduledExecutorService executorService);
        void onPlaybackStopped();
        void onSpeakerphoneOn(boolean on);
        void setClipPosition(int clipPositionInMillis, int clipLengthInMillis);
        void setSuccess();
        void setFetchContentTimeout();
        void setIsFetchingContent();
        void onVoicemailArchiveSucceded(Uri voicemailUri);
        void onVoicemailArchiveFailed(Uri voicemailUri);
        void setPresenter(VoicemailPlaybackPresenter presenter, Uri voicemailUri);
        void resetSeekBar();
    }

    public interface OnVoicemailDeletedListener {
        void onVoicemailDeleted(Uri uri);
        void onVoicemailDeleteUndo();
        void onVoicemailDeletedInDatabase();
    }

    /** The enumeration of {@link AsyncTask} objects we use in this class. */
    public enum Tasks {
        CHECK_FOR_CONTENT,
        CHECK_CONTENT_AFTER_CHANGE,
        ARCHIVE_VOICEMAIL
    }

    protected interface OnContentCheckedListener {
        void onContentChecked(boolean hasContent);
    }

    private static final String[] HAS_CONTENT_PROJECTION = new String[] {
        VoicemailContract.Voicemails.HAS_CONTENT,
        VoicemailContract.Voicemails.DURATION
    };

    private static final int NUMBER_OF_THREADS_IN_POOL = 2;
    // Time to wait for content to be fetched before timing out.
    private static final long FETCH_CONTENT_TIMEOUT_MS = 20000;

    private static final String VOICEMAIL_URI_KEY =
            VoicemailPlaybackPresenter.class.getName() + ".VOICEMAIL_URI";
    private static final String IS_PREPARED_KEY =
            VoicemailPlaybackPresenter.class.getName() + ".IS_PREPARED";
    // If present in the saved instance bundle, we should not resume playback on create.
    private static final String IS_PLAYING_STATE_KEY =
            VoicemailPlaybackPresenter.class.getName() + ".IS_PLAYING_STATE_KEY";
    // If present in the saved instance bundle, indicates where to set the playback slider.
    private static final String CLIP_POSITION_KEY =
            VoicemailPlaybackPresenter.class.getName() + ".CLIP_POSITION_KEY";
    private static final String IS_SPEAKERPHONE_ON_KEY =
            VoicemailPlaybackPresenter.class.getName() + ".IS_SPEAKER_PHONE_ON";
    public static final int PLAYBACK_REQUEST = 0;
    public static final int ARCHIVE_REQUEST = 1;
    public static final int SHARE_REQUEST = 2;

    /**
     * The most recently cached duration. We cache this since we don't want to keep requesting it
     * from the player, as this can easily lead to throwing {@link IllegalStateException} (any time
     * the player is released, it's illegal to ask for the duration).
     */
    private final AtomicInteger mDuration = new AtomicInteger(0);

    private static VoicemailPlaybackPresenter sInstance;

    private Activity mActivity;
    protected Context mContext;
    private PlaybackView mView;
    protected Uri mVoicemailUri;

    protected MediaPlayer mMediaPlayer;
    private int mPosition;
    private boolean mIsPlaying;
    // MediaPlayer crashes on some method calls if not prepared but does not have a method which
    // exposes its prepared state. Store this locally, so we can check and prevent crashes.
    private boolean mIsPrepared;
    private boolean mIsSpeakerphoneOn;

    private boolean mShouldResumePlaybackAfterSeeking;
    private int mInitialOrientation;

    // Used to run async tasks that need to interact with the UI.
    protected AsyncTaskExecutor mAsyncTaskExecutor;
    private static ScheduledExecutorService mScheduledExecutorService;
    /**
     * Used to handle the result of a successful or time-out fetch result.
     * <p>
     * This variable is thread-contained, accessed only on the ui thread.
     */
    private FetchResultHandler mFetchResultHandler;
    private final List<FetchResultHandler> mArchiveResultHandlers = new ArrayList<>();
    private Handler mHandler = new Handler();
    private PowerManager.WakeLock mProximityWakeLock;
    private VoicemailAudioManager mVoicemailAudioManager;

    private OnVoicemailDeletedListener mOnVoicemailDeletedListener;
    private final VoicemailAsyncTaskUtil mVoicemailAsyncTaskUtil;

    /**
     * Obtain singleton instance of this class. Use a single instance to provide a consistent
     * listener to the AudioManager when requesting and abandoning audio focus.
     *
     * Otherwise, after rotation the previous listener will still be active but a new listener
     * will be provided to calls to the AudioManager, which is bad. For example, abandoning
     * audio focus with the new listeners results in an AUDIO_FOCUS_GAIN callback to the
     * previous listener, which is the opposite of the intended behavior.
     */
    public static VoicemailPlaybackPresenter getInstance(
            Activity activity, Bundle savedInstanceState) {
        if (sInstance == null) {
            sInstance = new VoicemailPlaybackPresenter(activity);
        }

        sInstance.init(activity, savedInstanceState);
        return sInstance;
    }

    /**
     * Initialize variables which are activity-independent and state-independent.
     */
    protected VoicemailPlaybackPresenter(Activity activity) {
        Context context = activity.getApplicationContext();
        mAsyncTaskExecutor = AsyncTaskExecutors.createAsyncTaskExecutor();
        mVoicemailAudioManager = new VoicemailAudioManager(context, this);
        mVoicemailAsyncTaskUtil = new VoicemailAsyncTaskUtil(context.getContentResolver());
        PowerManager powerManager =
                (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            mProximityWakeLock = powerManager.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, TAG);
        }
    }

    /**
     * Update variables which are activity-dependent or state-dependent.
     */
    protected void init(Activity activity, Bundle savedInstanceState) {
        mActivity = activity;
        mContext = activity;

        mInitialOrientation = mContext.getResources().getConfiguration().orientation;
        mActivity.setVolumeControlStream(VoicemailAudioManager.PLAYBACK_STREAM);

        if (savedInstanceState != null) {
            // Restores playback state when activity is recreated, such as after rotation.
            mVoicemailUri = (Uri) savedInstanceState.getParcelable(VOICEMAIL_URI_KEY);
            mIsPrepared = savedInstanceState.getBoolean(IS_PREPARED_KEY);
            mPosition = savedInstanceState.getInt(CLIP_POSITION_KEY, 0);
            mIsPlaying = savedInstanceState.getBoolean(IS_PLAYING_STATE_KEY, false);
            mIsSpeakerphoneOn = savedInstanceState.getBoolean(IS_SPEAKERPHONE_ON_KEY, false);
        }

        if (mMediaPlayer == null) {
            mIsPrepared = false;
            mIsPlaying = false;
        }
    }

    /**
     * Must be invoked when the parent Activity is saving it state.
     */
    public void onSaveInstanceState(Bundle outState) {
        if (mView != null) {
            outState.putParcelable(VOICEMAIL_URI_KEY, mVoicemailUri);
            outState.putBoolean(IS_PREPARED_KEY, mIsPrepared);
            outState.putInt(CLIP_POSITION_KEY, mView.getDesiredClipPosition());
            outState.putBoolean(IS_PLAYING_STATE_KEY, mIsPlaying);
            outState.putBoolean(IS_SPEAKERPHONE_ON_KEY, mIsSpeakerphoneOn);
        }
    }

    /**
     * Specify the view which this presenter controls and the voicemail to prepare to play.
     */
    public void setPlaybackView(
            PlaybackView view, Uri voicemailUri, boolean startPlayingImmediately) {
        mView = view;
        mView.setPresenter(this, voicemailUri);

        // Handles cases where the same entry is binded again when scrolling in list, or where
        // the MediaPlayer was retained after an orientation change.
        if (mMediaPlayer != null && mIsPrepared && voicemailUri.equals(mVoicemailUri)) {
            // If the voicemail card was rebinded, we need to set the position to the appropriate
            // point. Since we retain the media player, we can just set it to the position of the
            // media player.
            mPosition = mMediaPlayer.getCurrentPosition();
            onPrepared(mMediaPlayer);
        } else {
            if (!voicemailUri.equals(mVoicemailUri)) {
                mVoicemailUri = voicemailUri;
                mPosition = 0;
                // Default to earpiece.
                setSpeakerphoneOn(false);
                mVoicemailAudioManager.setSpeakerphoneOn(false);
            } else {
                // Update the view to the current speakerphone state.
                mView.onSpeakerphoneOn(mIsSpeakerphoneOn);
            }
            /*
             * Check to see if the content field in the DB is set. If set, we proceed to
             * prepareContent() method. We get the duration of the voicemail from the query and set
             * it if the content is not available.
             */
            checkForContent(new OnContentCheckedListener() {
                @Override
                public void onContentChecked(boolean hasContent) {
                    if (hasContent) {
                        prepareContent();
                    } else if (mView != null) {
                        mView.resetSeekBar();
                        mView.setClipPosition(0, mDuration.get());
                    }
                }
            });

            if (startPlayingImmediately) {
                // Since setPlaybackView can get called during the view binding process, we don't
                // want to reset mIsPlaying to false if the user is currently playing the
                // voicemail and the view is rebound.
                mIsPlaying = startPlayingImmediately;
            }
        }
    }

    /**
     * Reset the presenter for playback back to its original state.
     */
    public void resetAll() {
        pausePresenter(true);

        mView = null;
        mVoicemailUri = null;
    }

    /**
     * When navigating away from voicemail playback, we need to release the media player,
     * pause the UI and save the position.
     *
     * @param reset {@code true} if we want to reset the position of the playback, {@code false} if
     * we want to retain the current position (in case we return to the voicemail).
     */
    public void pausePresenter(boolean reset) {
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }

        disableProximitySensor(false /* waitForFarState */);

        mIsPrepared = false;
        mIsPlaying = false;

        if (reset) {
            // We want to reset the position whether or not the view is valid.
            mPosition = 0;
        }

        if (mView != null) {
            mView.onPlaybackStopped();
            if (reset) {
                mView.setClipPosition(0, mDuration.get());
            } else {
                mPosition = mView.getDesiredClipPosition();
            }
        }
    }

    /**
     * Must be invoked when the parent activity is resumed.
     */
    public void onResume() {
        mVoicemailAudioManager.registerReceivers();
    }

    /**
     * Must be invoked when the parent activity is paused.
     */
    public void onPause() {
        mVoicemailAudioManager.unregisterReceivers();

        if (mContext != null && mIsPrepared
                && mInitialOrientation != mContext.getResources().getConfiguration().orientation) {
            // If an orientation change triggers the pause, retain the MediaPlayer.
            Log.d(TAG, "onPause: Orientation changed.");
            return;
        }

        // Release the media player, otherwise there may be failures.
        pausePresenter(false);

        if (mActivity != null) {
            mActivity.getWindow().clearFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

    }

    /**
     * Must be invoked when the parent activity is destroyed.
     */
    public void onDestroy() {
        // Clear references to avoid leaks from the singleton instance.
        mActivity = null;
        mContext = null;

        if (mScheduledExecutorService != null) {
            mScheduledExecutorService.shutdown();
            mScheduledExecutorService = null;
        }

        if (!mArchiveResultHandlers.isEmpty()) {
            for (FetchResultHandler fetchResultHandler : mArchiveResultHandlers) {
                fetchResultHandler.destroy();
            }
            mArchiveResultHandlers.clear();
        }

        if (mFetchResultHandler != null) {
            mFetchResultHandler.destroy();
            mFetchResultHandler = null;
        }
    }

    /**
     * Checks to see if we have content available for this voicemail.
     */
    protected void checkForContent(final OnContentCheckedListener callback) {
        mAsyncTaskExecutor.submit(Tasks.CHECK_FOR_CONTENT, new AsyncTask<Void, Void, Boolean>() {
            @Override
            public Boolean doInBackground(Void... params) {
                return queryHasContent(mVoicemailUri);
            }

            @Override
            public void onPostExecute(Boolean hasContent) {
                callback.onContentChecked(hasContent);
            }
        });
    }

    private boolean queryHasContent(Uri voicemailUri) {
        if (voicemailUri == null || mContext == null) {
            return false;
        }

        ContentResolver contentResolver = mContext.getContentResolver();
        Cursor cursor = contentResolver.query(
                voicemailUri, null, null, null, null);
        try {
            if (cursor != null && cursor.moveToNext()) {
                int duration = cursor.getInt(cursor.getColumnIndex(
                        VoicemailContract.Voicemails.DURATION));
                // Convert database duration (seconds) into mDuration (milliseconds)
                mDuration.set(duration > 0 ? duration * 1000 : 0);
                return cursor.getInt(cursor.getColumnIndex(
                        VoicemailContract.Voicemails.HAS_CONTENT)) == 1;
            }
        } finally {
            MoreCloseables.closeQuietly(cursor);
        }
        return false;
    }

    /**
     * Makes a broadcast request to ask that a voicemail source fetch this content.
     * <p>
     * This method <b>must be called on the ui thread</b>.
     * <p>
     * This method will be called when we realise that we don't have content for this voicemail. It
     * will trigger a broadcast to request that the content be downloaded. It will add a listener to
     * the content resolver so that it will be notified when the has_content field changes. It will
     * also set a timer. If the has_content field changes to true within the allowed time, we will
     * proceed to {@link #prepareContent()}. If the has_content field does not
     * become true within the allowed time, we will update the ui to reflect the fact that content
     * was not available.
     *
     * @return whether issued request to fetch content
     */
    protected boolean requestContent(int code) {
        if (mContext == null || mVoicemailUri == null) {
            return false;
        }

        FetchResultHandler tempFetchResultHandler =
                new FetchResultHandler(new Handler(), mVoicemailUri, code);

        switch (code) {
            case ARCHIVE_REQUEST:
                mArchiveResultHandlers.add(tempFetchResultHandler);
                break;
            default:
                if (mFetchResultHandler != null) {
                    mFetchResultHandler.destroy();
                }
                mView.setIsFetchingContent();
                mFetchResultHandler = tempFetchResultHandler;
                break;
        }

        // Send voicemail fetch request.
        Intent intent = new Intent(VoicemailContract.ACTION_FETCH_VOICEMAIL, mVoicemailUri);
        mContext.sendBroadcast(intent);
        return true;
    }

    @ThreadSafe
    private class FetchResultHandler extends ContentObserver implements Runnable {
        private AtomicBoolean mIsWaitingForResult = new AtomicBoolean(true);
        private final Handler mFetchResultHandler;
        private final Uri mVoicemailUri;
        private final int mRequestCode;

        public FetchResultHandler(Handler handler, Uri uri, int code) {
            super(handler);
            mFetchResultHandler = handler;
            mRequestCode = code;
            mVoicemailUri = uri;
            if (mContext != null) {
                mContext.getContentResolver().registerContentObserver(
                        mVoicemailUri, false, this);
                mFetchResultHandler.postDelayed(this, FETCH_CONTENT_TIMEOUT_MS);
            }
        }

        /**
         * Stop waiting for content and notify UI if {@link FETCH_CONTENT_TIMEOUT_MS} has elapsed.
         */
        @Override
        public void run() {
            if (mIsWaitingForResult.getAndSet(false) && mContext != null) {
                mContext.getContentResolver().unregisterContentObserver(this);
                if (mView != null) {
                    mView.setFetchContentTimeout();
                }
            }
        }

        public void destroy() {
            if (mIsWaitingForResult.getAndSet(false) && mContext != null) {
                mContext.getContentResolver().unregisterContentObserver(this);
                mFetchResultHandler.removeCallbacks(this);
            }
        }

        @Override
        public void onChange(boolean selfChange) {
            mAsyncTaskExecutor.submit(Tasks.CHECK_CONTENT_AFTER_CHANGE,
                    new AsyncTask<Void, Void, Boolean>() {

                @Override
                public Boolean doInBackground(Void... params) {
                    return queryHasContent(mVoicemailUri);
                }

                @Override
                public void onPostExecute(Boolean hasContent) {
                    if (hasContent && mContext != null && mIsWaitingForResult.getAndSet(false)) {
                        mContext.getContentResolver().unregisterContentObserver(
                                FetchResultHandler.this);
                        prepareContent();
                        if (mRequestCode == ARCHIVE_REQUEST) {
                            startArchiveVoicemailTask(mVoicemailUri, true /* archivedByUser */);
                        } else if (mRequestCode == SHARE_REQUEST) {
                            startArchiveVoicemailTask(mVoicemailUri, false /* archivedByUser */);
                        }
                    }
                }
            });
        }
    }

    /**
     * Prepares the voicemail content for playback.
     * <p>
     * This method will be called once we know that our voicemail has content (according to the
     * content provider). this method asynchronously tries to prepare the data source through the
     * media player. If preparation is successful, the media player will {@link #onPrepared()},
     * and it will call {@link #onError()} otherwise.
     */
    protected void prepareContent() {
        if (mView == null) {
            return;
        }
        Log.d(TAG, "prepareContent");

        // Release the previous media player, otherwise there may be failures.
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }

        mView.disableUiElements();
        mIsPrepared = false;

        try {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setOnPreparedListener(this);
            mMediaPlayer.setOnErrorListener(this);
            mMediaPlayer.setOnCompletionListener(this);

            mMediaPlayer.reset();
            mMediaPlayer.setDataSource(mContext, mVoicemailUri);
            mMediaPlayer.setAudioStreamType(VoicemailAudioManager.PLAYBACK_STREAM);
            mMediaPlayer.prepareAsync();
        } catch (IOException e) {
            handleError(e);
        }
    }

    /**
     * Once the media player is prepared, enables the UI and adopts the appropriate playback state.
     */
    @Override
    public void onPrepared(MediaPlayer mp) {
        if (mView == null) {
            return;
        }
        Log.d(TAG, "onPrepared");
        mIsPrepared = true;

        // Update the duration in the database if it was not previously retrieved
        CallLogAsyncTaskUtil.updateVoicemailDuration(mContext, mVoicemailUri,
                TimeUnit.MILLISECONDS.toSeconds(mMediaPlayer.getDuration()));

        mDuration.set(mMediaPlayer.getDuration());

        Log.d(TAG, "onPrepared: mPosition=" + mPosition);
        mView.setClipPosition(mPosition, mDuration.get());
        mView.enableUiElements();
        mView.setSuccess();
        mMediaPlayer.seekTo(mPosition);

        if (mIsPlaying) {
            resumePlayback();
        } else {
            pausePlayback();
        }
    }

    /**
     * Invoked if preparing the media player fails, for example, if file is missing or the voicemail
     * is an unknown file format that can't be played.
     */
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        handleError(new IllegalStateException("MediaPlayer error listener invoked: " + extra));
        return true;
    }

    protected void handleError(Exception e) {
        Log.d(TAG, "handleError: Could not play voicemail " + e);

        if (mIsPrepared) {
            mMediaPlayer.release();
            mMediaPlayer = null;
            mIsPrepared = false;
        }

        if (mView != null) {
            mView.onPlaybackError();
        }

        mPosition = 0;
        mIsPlaying = false;
    }

    /**
     * After done playing the voicemail clip, reset the clip position to the start.
     */
    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        pausePlayback();

        // Reset the seekbar position to the beginning.
        mPosition = 0;
        if (mView != null) {
            mView.setClipPosition(0, mDuration.get());
        }
    }

    /**
     * Only play voicemail when audio focus is granted. When it is lost (usually by another
     * application requesting focus), pause playback.
     *
     * @param gainedFocus {@code true} if the audio focus was gained, {@code} false otherwise.
     */
    public void onAudioFocusChange(boolean gainedFocus) {
        if (mIsPlaying == gainedFocus) {
            // Nothing new here, just exit.
            return;
        }

        if (!mIsPlaying) {
            resumePlayback();
        } else {
            pausePlayback();
        }
    }

    /**
     * Resumes voicemail playback at the clip position stored by the presenter. Null-op if already
     * playing.
     */
    public void resumePlayback() {
        if (mView == null) {
            return;
        }

        if (!mIsPrepared) {
            /*
             * Check content before requesting content to avoid duplicated requests. It is possible
             * that the UI doesn't know content has arrived if the fetch took too long causing a
             * timeout, but succeeded.
             */
            checkForContent(new OnContentCheckedListener() {
                @Override
                public void onContentChecked(boolean hasContent) {
                    if (!hasContent) {
                        // No local content, download from server. Queue playing if the request was
                        // issued,
                        mIsPlaying = requestContent(PLAYBACK_REQUEST);
                    } else {
                        // Queue playing once the media play loaded the content.
                        mIsPlaying = true;
                        prepareContent();
                    }
                }
            });
            return;
        }

        mIsPlaying = true;

        if (mMediaPlayer != null && !mMediaPlayer.isPlaying()) {
            // Clamp the start position between 0 and the duration.
            mPosition = Math.max(0, Math.min(mPosition, mDuration.get()));

            mMediaPlayer.seekTo(mPosition);

            try {
                // Grab audio focus.
                // Can throw RejectedExecutionException.
                mVoicemailAudioManager.requestAudioFocus();
                mMediaPlayer.start();
                setSpeakerphoneOn(mIsSpeakerphoneOn);
            } catch (RejectedExecutionException e) {
                handleError(e);
            }
        }

        Log.d(TAG, "Resumed playback at " + mPosition + ".");
        mView.onPlaybackStarted(mDuration.get(), getScheduledExecutorServiceInstance());
    }

    /**
     * Pauses voicemail playback at the current position. Null-op if already paused.
     */
    public void pausePlayback() {
        if (!mIsPrepared) {
            return;
        }

        mIsPlaying = false;

        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
        }

        mPosition = mMediaPlayer == null ? 0 : mMediaPlayer.getCurrentPosition();

        Log.d(TAG, "Paused playback at " + mPosition + ".");

        if (mView != null) {
            mView.onPlaybackStopped();
        }

        mVoicemailAudioManager.abandonAudioFocus();

        if (mActivity != null) {
            mActivity.getWindow().clearFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        disableProximitySensor(true /* waitForFarState */);
    }

    /**
     * Pauses playback when the user starts seeking the position, and notes whether the voicemail is
     * playing to know whether to resume playback once the user selects a new position.
     */
    public void pausePlaybackForSeeking() {
        if (mMediaPlayer != null) {
            mShouldResumePlaybackAfterSeeking = mMediaPlayer.isPlaying();
        }
        pausePlayback();
    }

    public void resumePlaybackAfterSeeking(int desiredPosition) {
        mPosition = desiredPosition;
        if (mShouldResumePlaybackAfterSeeking) {
            mShouldResumePlaybackAfterSeeking = false;
            resumePlayback();
        }
    }

    /**
     * Seek to position. This is called when user manually seek the playback. It could be either
     * by touch or volume button while in talkback mode.
     * @param position
     */
    public void seek(int position) {
        mPosition = position;
    }

    private void enableProximitySensor() {
        if (mProximityWakeLock == null || mIsSpeakerphoneOn || !mIsPrepared
                || mMediaPlayer == null || !mMediaPlayer.isPlaying()) {
            return;
        }

        if (!mProximityWakeLock.isHeld()) {
            Log.i(TAG, "Acquiring proximity wake lock");
            mProximityWakeLock.acquire();
        } else {
            Log.i(TAG, "Proximity wake lock already acquired");
        }
    }

    private void disableProximitySensor(boolean waitForFarState) {
        if (mProximityWakeLock == null) {
            return;
        }
        if (mProximityWakeLock.isHeld()) {
            Log.i(TAG, "Releasing proximity wake lock");
            int flags = waitForFarState ? PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY : 0;
            mProximityWakeLock.release(flags);
        } else {
            Log.i(TAG, "Proximity wake lock already released");
        }
    }

    /**
     * This is for use by UI interactions only. It simplifies UI logic.
     */
    public void toggleSpeakerphone() {
        mVoicemailAudioManager.setSpeakerphoneOn(!mIsSpeakerphoneOn);
        setSpeakerphoneOn(!mIsSpeakerphoneOn);
    }

    /**
     * This method only handles app-level changes to the speakerphone. Audio layer changes should
     * be handled separately. This is so that the VoicemailAudioManager can trigger changes to
     * the presenter without the presenter triggering the audio manager and duplicating actions.
     */
    public void setSpeakerphoneOn(boolean on) {
        if (mView == null) {
            return;
        }

        mView.onSpeakerphoneOn(on);

        mIsSpeakerphoneOn = on;

        // This should run even if speakerphone is not being toggled because we may be switching
        // from earpiece to headphone and vise versa. Also upon initial setup the default audio
        // source is the earpiece, so we want to trigger the proximity sensor.
        if (mIsPlaying) {
            if (on || mVoicemailAudioManager.isWiredHeadsetPluggedIn()) {
                disableProximitySensor(false /* waitForFarState */);
                if (mIsPrepared && mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                    mActivity.getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            } else {
                enableProximitySensor();
                if (mActivity != null) {
                    mActivity.getWindow().clearFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }
        }
    }

    public void setOnVoicemailDeletedListener(OnVoicemailDeletedListener listener) {
        mOnVoicemailDeletedListener = listener;
    }

    public int getMediaPlayerPosition() {
        return mIsPrepared && mMediaPlayer != null ? mMediaPlayer.getCurrentPosition() : 0;
    }

    public void notifyUiOfArchiveResult(Uri voicemailUri, boolean archived) {
        if (mView == null) {
            return;
        }
        if (archived) {
            mView.onVoicemailArchiveSucceded(voicemailUri);
        } else {
            mView.onVoicemailArchiveFailed(voicemailUri);
        }
    }

    /* package */ void onVoicemailDeleted() {
        // Trampoline the event notification to the interested listener.
        if (mOnVoicemailDeletedListener != null) {
            mOnVoicemailDeletedListener.onVoicemailDeleted(mVoicemailUri);
        }
    }

    /* package */ void onVoicemailDeleteUndo() {
        // Trampoline the event notification to the interested listener.
        if (mOnVoicemailDeletedListener != null) {
            mOnVoicemailDeletedListener.onVoicemailDeleteUndo();
        }
    }

    /* package */ void onVoicemailDeletedInDatabase() {
        // Trampoline the event notification to the interested listener.
        if (mOnVoicemailDeletedListener != null) {
            mOnVoicemailDeletedListener.onVoicemailDeletedInDatabase();
        }
    }

    private static synchronized ScheduledExecutorService getScheduledExecutorServiceInstance() {
        if (mScheduledExecutorService == null) {
            mScheduledExecutorService = Executors.newScheduledThreadPool(NUMBER_OF_THREADS_IN_POOL);
        }
        return mScheduledExecutorService;
    }

    /**
     * If voicemail has already been downloaded, go straight to archiving. Otherwise, request
     * the voicemail content first.
     */
    public void archiveContent(final Uri voicemailUri, final boolean archivedByUser) {
        checkForContent(new OnContentCheckedListener() {
            @Override
            public void onContentChecked(boolean hasContent) {
                if (!hasContent) {
                    requestContent(archivedByUser ? ARCHIVE_REQUEST : SHARE_REQUEST);
                } else {
                    startArchiveVoicemailTask(voicemailUri, archivedByUser);
                }
            }
        });
    }

    /**
     * Asynchronous task used to archive a voicemail given its uri.
     */
    protected void startArchiveVoicemailTask(final Uri voicemailUri, final boolean archivedByUser) {
        mVoicemailAsyncTaskUtil.archiveVoicemailContent(
                new VoicemailAsyncTaskUtil.OnArchiveVoicemailListener() {
                    @Override
                    public void onArchiveVoicemail(final Uri archivedVoicemailUri) {
                        if (archivedVoicemailUri == null) {
                            notifyUiOfArchiveResult(voicemailUri, false);
                            return;
                        }

                        if (archivedByUser) {
                            setArchivedVoicemailStatusAndUpdateUI(voicemailUri,
                                    archivedVoicemailUri, true);
                        } else {
                            sendShareIntent(archivedVoicemailUri);
                        }
                    }
                }, voicemailUri);
    }

    /**
     * Sends the intent for sharing the voicemail file.
     */
    protected void sendShareIntent(final Uri voicemailUri) {
        mVoicemailAsyncTaskUtil.getVoicemailFilePath(
                new VoicemailAsyncTaskUtil.OnGetArchivedVoicemailFilePathListener() {
                    @Override
                    public void onGetArchivedVoicemailFilePath(String filePath) {
                        mView.enableUiElements();
                        if (filePath == null) {
                            mView.setFetchContentTimeout();
                            return;
                        }
                        Uri voicemailFileUri = FileProvider.getUriForFile(
                                mContext,
                                mContext.getString(R.string.contacts_file_provider_authority),
                                new File(filePath));
                        mContext.startActivity(Intent.createChooser(
                                getShareIntent(voicemailFileUri),
                                mContext.getResources().getText(
                                        R.string.call_log_share_voicemail)));
                    }
                }, voicemailUri);
    }

    /** Sets archived_by_user field to the given boolean and updates the URI. */
    private void setArchivedVoicemailStatusAndUpdateUI(
            final Uri voicemailUri,
            final Uri archivedVoicemailUri,
            boolean status) {
        mVoicemailAsyncTaskUtil.setVoicemailArchiveStatus(
                new VoicemailAsyncTaskUtil.OnSetVoicemailArchiveStatusListener() {
                    @Override
                    public void onSetVoicemailArchiveStatus(boolean success) {
                        notifyUiOfArchiveResult(voicemailUri, success);
                    }
                }, archivedVoicemailUri, status);
    }

    private Intent getShareIntent(Uri voicemailFileUri) {
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, voicemailFileUri);
        shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        shareIntent.setType(mContext.getContentResolver()
                .getType(voicemailFileUri));
        return shareIntent;
    }

    @VisibleForTesting
    public boolean isPlaying() {
        return mIsPlaying;
    }

    @VisibleForTesting
    public boolean isSpeakerphoneOn() {
        return mIsSpeakerphoneOn;
    }

    @VisibleForTesting
    public void clearInstance() {
        sInstance = null;
    }
}
