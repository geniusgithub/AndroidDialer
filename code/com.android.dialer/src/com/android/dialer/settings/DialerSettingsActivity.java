package com.android.dialer.settings;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Process;
import android.os.UserManager;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.contacts.common.util.PermissionsUtil;
import com.android.dialer.R;

import java.util.List;

public class DialerSettingsActivity extends PreferenceActivity {

    protected SharedPreferences mPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        Header displayOptionsHeader = new Header();
        displayOptionsHeader.titleRes = R.string.display_options_title;
        displayOptionsHeader.fragment = DisplayOptionsSettingsFragment.class.getName();
        target.add(displayOptionsHeader);

        Header soundSettingsHeader = new Header();
        soundSettingsHeader.titleRes = R.string.sounds_and_vibration_title;
        soundSettingsHeader.fragment = SoundSettingsFragment.class.getName();
        soundSettingsHeader.id = R.id.settings_header_sounds_and_vibration;
        target.add(soundSettingsHeader);

        Header quickResponseSettingsHeader = new Header();
        Intent quickResponseSettingsIntent =
                new Intent(TelecomManager.ACTION_SHOW_RESPOND_VIA_SMS_SETTINGS);
        quickResponseSettingsHeader.titleRes = R.string.respond_via_sms_setting_title;
        quickResponseSettingsHeader.intent = quickResponseSettingsIntent;
        target.add(quickResponseSettingsHeader);

        TelephonyManager telephonyManager =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        // Only show call setting menus if the current user is the primary/owner user.
        if (isPrimaryUser()) {
            // Show "Call Settings" if there is one SIM and "Phone Accounts" if there are more.
            if (telephonyManager.getPhoneCount() <= 1) {
                Header callSettingsHeader = new Header();
                Intent callSettingsIntent = new Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS);
                callSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                callSettingsHeader.titleRes = R.string.call_settings_label;
                callSettingsHeader.intent = callSettingsIntent;
                target.add(callSettingsHeader);
            } else {
                Header phoneAccountSettingsHeader = new Header();
                Intent phoneAccountSettingsIntent =
                        new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
                phoneAccountSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                phoneAccountSettingsHeader.titleRes = R.string.phone_account_settings_label;
                phoneAccountSettingsHeader.intent = phoneAccountSettingsIntent;
                target.add(phoneAccountSettingsHeader);
            }

            if (telephonyManager.isTtyModeSupported()
                    || telephonyManager.isHearingAidCompatibilitySupported()) {
                Header accessibilitySettingsHeader = new Header();
                Intent accessibilitySettingsIntent =
                        new Intent(TelecomManager.ACTION_SHOW_CALL_ACCESSIBILITY_SETTINGS);
                accessibilitySettingsHeader.titleRes = R.string.accessibility_settings_title;
                accessibilitySettingsHeader.intent = accessibilitySettingsIntent;
                target.add(accessibilitySettingsHeader);
            }
        }
    }

    @Override
    public void onHeaderClick(Header header, int position) {
        if (header.id == R.id.settings_header_sounds_and_vibration) {
            // If we don't have the permission to write to system settings, go to system sound
            // settings instead. Otherwise, perform the super implementation (which launches our
            // own preference fragment.
            if (!Settings.System.canWrite(this)) {
                Toast.makeText(
                        this,
                        getResources().getString(R.string.toast_cannot_write_system_settings),
                        Toast.LENGTH_SHORT).show();
                startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));
                return;
            }
        }
        super.onHeaderClick(header, position);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return false;
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return true;
    }

    /**
     * @return Whether the current user is the primary user.
     */
    private boolean isPrimaryUser() {
        final UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        return userManager.isSystemUser();
    }
}
