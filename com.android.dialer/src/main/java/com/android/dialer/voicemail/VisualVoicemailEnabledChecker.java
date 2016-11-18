package com.android.dialer.voicemail;


import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;

import com.android.dialer.calllog.CallLogQueryHandler;

/**
 * Helper class to check whether visual voicemail is enabled.
 *
 * Call isVisualVoicemailEnabled() to retrieve the result.
 *
 * The result is cached and saved in a SharedPreferences, stored as a boolean in
 * PREF_KEY_HAS_ACTIVE_VOICEMAIL_PROVIDER. Every time a new instance is created, it will try to
 * restore the cached result from the SharedPreferences.
 *
 * Call asyncUpdate() to make a CallLogQuery to check the actual status. This is a async call so
 * isVisualVoicemailEnabled() will not be affected immediately.
 *
 * If the status has changed as a result of asyncUpdate(),
 * Callback.onVisualVoicemailEnabledStatusChanged() will be called with the new value.
 */
public class VisualVoicemailEnabledChecker implements CallLogQueryHandler.Listener {

    public static final String PREF_KEY_HAS_ACTIVE_VOICEMAIL_PROVIDER =
            "has_active_voicemail_provider";
    private SharedPreferences mPrefs;
    private boolean mHasActiveVoicemailProvider;
    private CallLogQueryHandler mCallLogQueryHandler;
    private VoicemailStatusHelper mVoicemailStatusHelper;
    private Context mContext;

    public interface Callback {

        /**
         * Callback to notify enabled status has changed to the @param newValue
         */
        void onVisualVoicemailEnabledStatusChanged(boolean newValue);
    }

    private Callback mCallback;

    public VisualVoicemailEnabledChecker(Context context, @Nullable Callback callback) {
        mContext = context;
        mCallback = callback;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mVoicemailStatusHelper = new VoicemailStatusHelperImpl();
        mHasActiveVoicemailProvider = mPrefs.getBoolean(PREF_KEY_HAS_ACTIVE_VOICEMAIL_PROVIDER,
                false);
    }

    /**
     * @return whether visual voicemail is enabled. Result is cached, call asyncUpdate() to
     * update the result.
     */
    public boolean isVisualVoicemailEnabled() {
        return mHasActiveVoicemailProvider;
    }

    /**
     * Perform an async query into the system to check the status of visual voicemail.
     * If the status has changed, Callback.onVisualVoicemailEnabledStatusChanged() will be called.
     */
    public void asyncUpdate() {
        mCallLogQueryHandler =
                new CallLogQueryHandler(mContext, mContext.getContentResolver(), this);
        mCallLogQueryHandler.fetchVoicemailStatus();
    }

    @Override
    public void onVoicemailStatusFetched(Cursor statusCursor) {
        boolean hasActiveVoicemailProvider =
                mVoicemailStatusHelper.getNumberActivityVoicemailSources(statusCursor) > 0;
        if (hasActiveVoicemailProvider != mHasActiveVoicemailProvider) {
            mHasActiveVoicemailProvider = hasActiveVoicemailProvider;
            mPrefs.edit().putBoolean(PREF_KEY_HAS_ACTIVE_VOICEMAIL_PROVIDER,
                    mHasActiveVoicemailProvider);
            if (mCallback != null) {
                mCallback.onVisualVoicemailEnabledStatusChanged(mHasActiveVoicemailProvider);
            }
        }
    }

    @Override
    public void onVoicemailUnreadCountFetched(Cursor cursor) {
        // Do nothing
    }

    @Override
    public void onMissedCallsUnreadCountFetched(Cursor cursor) {
        // Do nothing
    }

    @Override
    public boolean onCallsFetched(Cursor combinedCursor) {
        // Do nothing
        return false;
    }
}
