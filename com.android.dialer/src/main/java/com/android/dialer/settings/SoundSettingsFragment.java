/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.dialer.settings;

import android.content.Context;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import com.android.contacts.common.compat.SdkVersionOverride;
import com.android.dialer.R;
import com.android.dialer.compat.SettingsCompat;
import com.android.phone.common.util.SettingsUtil;

public class SoundSettingsFragment extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final int NO_DTMF_TONE = 0;
    private static final int PLAY_DTMF_TONE = 1;

    private static final int NO_VIBRATION_FOR_CALLS = 0;
    private static final int DO_VIBRATION_FOR_CALLS = 1;


    private static final int DTMF_TONE_TYPE_NORMAL = 0;

    private static final int SHOW_CARRIER_SETTINGS = 0;
    private static final int HIDE_CARRIER_SETTINGS = 1;

    private static final int MSG_UPDATE_RINGTONE_SUMMARY = 1;

    private Preference mRingtonePreference;
    private CheckBoxPreference mVibrateWhenRinging;
    private CheckBoxPreference mPlayDtmfTone;
    private ListPreference mDtmfToneLength;

    private final Runnable mRingtoneLookupRunnable = new Runnable() {
        @Override
        public void run() {
            updateRingtonePreferenceSummary();
        }
    };

    private final Handler mRingtoneLookupComplete = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_RINGTONE_SUMMARY:
                    mRingtonePreference.setSummary((CharSequence) msg.obj);
                    break;
            }
        }
    };

    @Override
    public Context getContext() {
        return getActivity();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.sound_settings);

        Context context = getActivity();

        mRingtonePreference = findPreference(context.getString(R.string.ringtone_preference_key));
        mVibrateWhenRinging = (CheckBoxPreference) findPreference(
                context.getString(R.string.vibrate_on_preference_key));
        mPlayDtmfTone = (CheckBoxPreference) findPreference(
                context.getString(R.string.play_dtmf_preference_key));
        mDtmfToneLength = (ListPreference) findPreference(
                context.getString(R.string.dtmf_tone_length_preference_key));

        if (hasVibrator()) {
            mVibrateWhenRinging.setOnPreferenceChangeListener(this);
        } else {
            getPreferenceScreen().removePreference(mVibrateWhenRinging);
            mVibrateWhenRinging = null;
        }

        mPlayDtmfTone.setOnPreferenceChangeListener(this);
        mPlayDtmfTone.setChecked(shouldPlayDtmfTone());

        TelephonyManager telephonyManager =
                (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
        if (SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.M) >= Build.VERSION_CODES.M
                && telephonyManager.canChangeDtmfToneLength()
                && (telephonyManager.isWorldPhone() || !shouldHideCarrierSettings())) {
            mDtmfToneLength.setOnPreferenceChangeListener(this);
            mDtmfToneLength.setValueIndex(
                    Settings.System.getInt(context.getContentResolver(),
                        Settings.System.DTMF_TONE_TYPE_WHEN_DIALING,
                        DTMF_TONE_TYPE_NORMAL));
        } else {
            getPreferenceScreen().removePreference(mDtmfToneLength);
            mDtmfToneLength = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!SettingsCompat.System.canWrite(getContext())) {
            // If the user launches this setting fragment, then toggles the WRITE_SYSTEM_SETTINGS
            // AppOp, then close the fragment since there is nothing useful to do.
            getActivity().onBackPressed();
            return;
        }

        if (mVibrateWhenRinging != null) {
            mVibrateWhenRinging.setChecked(shouldVibrateWhenRinging());
        }

        // Lookup the ringtone name asynchronously.
        new Thread(mRingtoneLookupRunnable).start();
    }

    /**
     * Supports onPreferenceChangeListener to look for preference changes.
     *
     * @param preference The preference to be changed
     * @param objValue The value of the selection, NOT its localized display value.
     */
    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (!SettingsCompat.System.canWrite(getContext())) {
            // A user shouldn't be able to get here, but this protects against monkey crashes.
            Toast.makeText(
                    getContext(),
                    getResources().getString(R.string.toast_cannot_write_system_settings),
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        if (preference == mVibrateWhenRinging) {
            boolean doVibrate = (Boolean) objValue;
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.VIBRATE_WHEN_RINGING,
                    doVibrate ? DO_VIBRATION_FOR_CALLS : NO_VIBRATION_FOR_CALLS);
        } else if (preference == mDtmfToneLength) {
            int index = mDtmfToneLength.findIndexOfValue((String) objValue);
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.DTMF_TONE_TYPE_WHEN_DIALING, index);
        }
        return true;
    }

    /**
     * Click listener for toggle events.
     */
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (!SettingsCompat.System.canWrite(getContext())) {
            Toast.makeText(
                    getContext(),
                    getResources().getString(R.string.toast_cannot_write_system_settings),
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        if (preference == mPlayDtmfTone) {
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.DTMF_TONE_WHEN_DIALING,
                    mPlayDtmfTone.isChecked() ? PLAY_DTMF_TONE : NO_DTMF_TONE);
        }
        return true;
    }

    /**
     * Updates the summary text on the ringtone preference with the name of the ringtone.
     */
    private void updateRingtonePreferenceSummary() {
        SettingsUtil.updateRingtoneName(
                getActivity(),
                mRingtoneLookupComplete,
                RingtoneManager.TYPE_RINGTONE,
                mRingtonePreference.getKey(),
                MSG_UPDATE_RINGTONE_SUMMARY);
    }

    /**
     * Obtain the value for "vibrate when ringing" setting. The default value is false.
     *
     * Watch out: if the setting is missing in the device, this will try obtaining the old
     * "vibrate on ring" setting from AudioManager, and save the previous setting to the new one.
     */
    private boolean shouldVibrateWhenRinging() {
        int vibrateWhenRingingSetting = Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.VIBRATE_WHEN_RINGING,
                NO_VIBRATION_FOR_CALLS);
        return hasVibrator() && (vibrateWhenRingingSetting == DO_VIBRATION_FOR_CALLS);
    }

    /**
     * Obtains the value for dialpad/DTMF tones. The default value is true.
     */
    private boolean shouldPlayDtmfTone() {
        int dtmfToneSetting = Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.DTMF_TONE_WHEN_DIALING,
                PLAY_DTMF_TONE);
        return dtmfToneSetting == PLAY_DTMF_TONE;
    }

    /**
     * Whether the device hardware has a vibrator.
     */
    private boolean hasVibrator() {
        Vibrator vibrator = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
        return vibrator != null && vibrator.hasVibrator();
    }

    private boolean shouldHideCarrierSettings() {
        CarrierConfigManager configManager = (CarrierConfigManager) getActivity().getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        return configManager.getConfig().getBoolean(
                CarrierConfigManager.KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL);
    }
}
