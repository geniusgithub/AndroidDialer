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
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.VoicemailContract;
import android.util.Log;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.SeekBar;

import com.android.dialer.R;
import com.android.dialer.util.AsyncTaskExecutor;
import com.android.dialer.util.AsyncTaskExecutors;

import com.android.common.io.MoreCloseables;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

//import javax.annotation.concurrent.NotThreadSafe;
//import javax.annotation.concurrent.ThreadSafe;

/**
 * Contains the controlling logic for a voicemail playback in the call log. It is closely coupled
 * to assumptions about the behaviors and lifecycle of the call log, in particular in the
 * {@link CallLogFragment} and {@link CallLogAdapter}.
 * <p>
 * This controls a single {@link com.android.dialer.voicemail.VoicemailPlaybackLayout}. A single
 * instance can be reused for different such layouts, using {@link #setVoicemailPlaybackView}. This
 * is to facilitate reuse across different voicemail call log entries.
 * <p>
 * This class is not thread safe. The thread policy for this class is thread-confinement, all calls
 * into this class from outside must be done from the main UI thread.
 */
//@NotThreadSafe
@VisibleForTesting
public class VoicemailPlaybackPresenter
        implements OnAudioFocusChangeListener, MediaPlayer.OnPreparedListener,
                MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    private static final String TAG = VoicemailPlaybackPresenter.class.getSimpleName();

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
        void setFetchContentTimeout();
        void setIsBuffering();
        void setIsFetchingContent();
        void setPresenter(VoicemailPlaybackPresenter presenter, Uri voicemailUri);
    }

    public interface OnVoicemailDeletedListener {
        void onVoicemailDeleted(Uri uri);
    }

    /** The enumeration of {@link AsyncTask} objects we use in this class. */
    public enum Tasks {
        CHECK_FOR_CONTENT,
        CHECK_CONTENT_AFTER_CHANGE,
    }

    private static final String[] HAS_CONTENT_PROJECTION = new String[] {
        VoicemailContract.Voicemails.HAS_CONTENT,
    };

    public static final int PLAYBACK_STREAM = AudioManager.STREAM_VOICE_CALL;
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

    /**
     * The most recently cached duration. We cache this since we don't want to keep requesting it
     * from the player, as this can easily lead to throwing {@link IllegalStateException} (any time
     * the player is released, it's illegal to ask for the duration).
     */
    private final AtomicInteger mDuration = new AtomicInteger(0);

    private static VoicemailPlaybackPresenter sInstance;

    private Activity mActivity;
    private Context mContext;
    private PlaybackView mView;
    private Uri mVoicemailUri;

    private MediaPlayer mMediaPlayer;
    private int mPosition;
    private boolean mIsPlaying;
    // MediaPlayer crashes on some method calls if not prepared but does not have a method which
    // exposes its prepared state. Store this locally, so we can check and prevent crashes.
    private boolean mIsPrepared;

    private boolean mShouldResumePlaybackAfterSeeking;
    private int mInitialOrientation;

    // Used to run async tasks that need to interact with the UI.
    private AsyncTaskExecutor mAsyncTaskExecutor;
    private static ScheduledExecutorService mScheduledExecutorService;
    /**
     * Used to handle the result of a successful or time-out fetch result.
     * <p>
     * This variable is thread-contained, accessed only on the ui thread.
     */
    private FetchResultHandler mFetchResultHandler;
    private Handler mHandler = new Handler();
    private PowerManager.WakeLock mProximityWakeLock;
    private AudioManager mAudioManager;

    private OnVoicemailDeletedListener mOnVoicemailDeletedListener;

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
    private VoicemailPlaybackPresenter(Activity activity) {
        Context context = activity.getApplicationContext();
        mAsyncTaskExecutor = AsyncTaskExecutors.createAsyncTaskExecutor();
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

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
    private void init(Activity activity, Bundle savedInstanceState) {
        mActivity = activity;
        mContext = activity;

        mInitialOrientation = mContext.getResources().getConfiguration().orientation;
        mActivity.setVolumeControlStream(VoicemailPlaybackPresenter.PLAYBACK_STREAM);

        if (savedInstanceState != null) {
            // Restores playback state when activity is recreated, such as after rotation.
            mVoicemailUri = (Uri) savedInstanceState.getParcelable(VOICEMAIL_URI_KEY);
            mIsPrepared = savedInstanceState.getBoolean(IS_PREPARED_KEY);
            mPosition = savedInstanceState.getInt(CLIP_POSITION_KEY, 0);
            mIsPlaying = savedInstanceState.getBoolean(IS_PLAYING_STATE_KEY, false);
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
        }
    }

    /**
     * Specify the view which this presenter controls and the voicemail to prepare to play.
     */
    public void setPlaybackView(
            PlaybackView view, Uri voicemailUri, boolean startPlayingImmediately) {
        mView = view;
        mView.setPresenter(this, voicemailUri);

        if (mMediaPlayer != null && voicemailUri.equals(mVoicemailUri)) {
            // Handles case where MediaPlayer was retained after an orientation change.
            onPrepared(mMediaPlayer);
            mView.onSpeakerphoneOn(isSpeakerphoneOn());
        } else {
            if (!voicemailUri.equals(mVoicemailUri)) {
                mPosition = 0;
            }

            mVoicemailUri = voicemailUri;
            mDuration.set(0);

            if (startPlayingImmediately) {
                // Since setPlaybackView can get called during the view binding process, we don't
                // want to reset mIsPlaying to false if the user is currently playing the
                // voicemail and the view is rebound.
                mIsPlaying = startPlayingImmediately;
                checkForContent();
            }

            // Default to earpiece.
            mView.onSpeakerphoneOn(false);
        }
    }

    /**
     * Reset the presenter for playback back to its original state.
     */
    public void resetAll() {
        reset();

        mView = null;
        mVoicemailUri = null;
    }

    /**
     * Reset the presenter such that it is as if the voicemail has not been played.
     */
    public void reset() {
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }

        disableProximitySensor(false /* waitForFarState */);

        mIsPrepared = false;
        mIsPlaying = false;
        mPosition = 0;
        mDuration.set(0);

        if (mView != null) {
            mView.onPlaybackStopped();
            mView.setClipPosition(0, mDuration.get());
        }
    }

    /**
     * Must be invoked when the parent activity is paused.
     */
    public void onPause() {
        if (mContext != null && mIsPrepared
                && mInitialOrientation != mContext.getResources().getConfiguration().orientation) {
            // If an orientation change triggers the pause, retain the MediaPlayer.
            Log.d(TAG, "onPause: Orientation changed.");
            return;
        }

        // Release the media player, otherwise there may be failures.
        reset();

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

        if (mFetchResultHandler != null) {
            mFetchResultHandler.destroy();
            mFetchResultHandler = null;
        }
    }

    /**
     * Checks to see if we have content available for this voicemail.
     * <p>
     * This method will be called once, after the fragment has been created, before we know if the
     * voicemail we've been asked to play has any content available.
     * <p>
     * Notify the user that we are fetching the content, then check to see if the content field in
     * the DB is set. If set, we proceed to {@link #prepareContent()} method. If not set, make
     * a request to fetch the content asynchronously via {@link #requestContent()}.
     */
    private void checkForContent() {
        mView.setIsFetchingContent();
        mAsyncTaskExecutor.submit(Tasks.CHECK_FOR_CONTENT, new AsyncTask<Void, Void, Boolean>() {
            @Override
            public Boolean doInBackground(Void... params) {
                return queryHasContent(mVoicemailUri);
            }

            @Override
            public void onPostExecute(Boolean hasContent) {
                if (hasContent) {
                    prepareContent();
                } else {
                    requestContent();
                }
            }
        });
    }

    private boolean queryHasContent(Uri voicemailUri) {
        if (voicemailUri == null || mContext == null) {
            return false;
        }

        ContentResolver contentResolver = mContext.getContentResolver();
        Cursor cursor = contentResolver.query(
                voicemailUri, HAS_CONTENT_PROJECTION, null, null, null);
        try {
            if (cursor != null && cursor.moveToNext()) {
                return cursor.getInt(cursor.getColumnIndexOrThrow(
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
     */
    private void requestContent() {
        if (mFetchResultHandler != null) {
            mFetchResultHandler.destroy();
        }

        mFetchResultHandler = new FetchResultHandler(new Handler(), mVoicemailUri);

        // Send voicemail fetch request.
        Intent intent = new Intent(VoicemailContract.ACTION_FETCH_VOICEMAIL, mVoicemailUri);
        mContext.sendBroadcast(intent);
    }

//    @ThreadSafe
    private class FetchResultHandler extends ContentObserver implements Runnable {
        private AtomicBoolean mIsWaitingForResult = new AtomicBoolean(true);
        private final Handler mFetchResultHandler;

        public FetchResultHandler(Handler handler, Uri voicemailUri) {
            super(handler);
            mFetchResultHandler = handler;

            if (mContext != null) {
                mContext.getContentResolver().registerContentObserver(
                        voicemailUri, false, this);
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
    private void prepareContent() {
        if (mView == null) {
            return;
        }
        Log.d(TAG, "prepareContent");

        // Release the previous media player, otherwise there may be failures.
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }

        mView.setIsBuffering();
        mIsPrepared = false;

        try {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setOnPreparedListener(this);
            mMediaPlayer.setOnErrorListener(this);
            mMediaPlayer.setOnCompletionListener(this);

            mMediaPlayer.reset();
            mMediaPlayer.setDataSource(mContext, mVoicemailUri);
            mMediaPlayer.setAudioStreamType(PLAYBACK_STREAM);
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

        mDuration.set(mMediaPlayer.getDuration());
        mPosition = mMediaPlayer.getCurrentPosition();

        mView.enableUiElements();
        Log.d(TAG, "onPrepared: mPosition=" + mPosition);
        mView.setClipPosition(mPosition, mDuration.get());
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

    private void handleError(Exception e) {
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

    @Override
    public void onAudioFocusChange(int focusChange) {
        Log.d(TAG, "onAudioFocusChange: focusChange=" + focusChange);
        boolean lostFocus = focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                || focusChange == AudioManager.AUDIOFOCUS_LOSS;
        if (mIsPlaying && focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            pausePlayback();
        } else if (!mIsPlaying && focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            resumePlayback();
        }
    }

    /**
     * Resumes voicemail playback at the clip position stored by the presenter. Null-op if already
     * playing.
     */
    public void resumePlayback() {
        if (!mIsPrepared) {
            // If we haven't downloaded the voicemail yet, attempt to download it.
            checkForContent();
            mIsPlaying = true;

            return;
        }

        mIsPlaying = true;

        if (!mMediaPlayer.isPlaying()) {
            // Clamp the start position between 0 and the duration.
            mPosition = Math.max(0, Math.min(mPosition, mDuration.get()));
            mMediaPlayer.seekTo(mPosition);

            try {
                // Grab audio focus.
                int result = mAudioManager.requestAudioFocus(
                        this,
                        PLAYBACK_STREAM,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
                if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    throw new RejectedExecutionException("Could not capture audio focus.");
                }

                // Can throw RejectedExecutionException.
                mMediaPlayer.start();
            } catch (RejectedExecutionException e) {
                handleError(e);
            }
        }

        Log.d(TAG, "Resumed playback at " + mPosition + ".");
        mView.onPlaybackStarted(mDuration.get(), getScheduledExecutorServiceInstance());
        if (isSpeakerphoneOn()) {
            mActivity.getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            enableProximitySensor();
        }
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
        mAudioManager.abandonAudioFocus(this);

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

    private void enableProximitySensor() {
        if (mProximityWakeLock == null || isSpeakerphoneOn() || !mIsPrepared
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

    public void setSpeakerphoneOn(boolean on) {
        mAudioManager.setSpeakerphoneOn(on);

        if (on) {
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

    public boolean isSpeakerphoneOn() {
        return mAudioManager.isSpeakerphoneOn();
    }

    public void setOnVoicemailDeletedListener(OnVoicemailDeletedListener listener) {
        mOnVoicemailDeletedListener = listener;
    }

    public int getMediaPlayerPosition() {
        return mIsPrepared && mMediaPlayer != null ? mMediaPlayer.getCurrentPosition() : 0;
    }

    /* package */ void onVoicemailDeleted() {
        // Trampoline the event notification to the interested listener
        if (mOnVoicemailDeletedListener != null) {
            mOnVoicemailDeletedListener.onVoicemailDeleted(mVoicemailUri);
        }
    }

    private static synchronized ScheduledExecutorService getScheduledExecutorServiceInstance() {
        if (mScheduledExecutorService == null) {
            mScheduledExecutorService = Executors.newScheduledThreadPool(NUMBER_OF_THREADS_IN_POOL);
        }
        return mScheduledExecutorService;
    }

    @VisibleForTesting
    public boolean isPlaying() {
        return mIsPlaying;
    }
}
