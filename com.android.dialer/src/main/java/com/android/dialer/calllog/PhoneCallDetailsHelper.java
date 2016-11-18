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

package com.android.dialer.calllog;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.v4.content.ContextCompat;
import android.telecom.PhoneAccount;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.TextView;

import com.android.contacts.common.testing.NeededForTesting;
import com.android.contacts.common.util.PhoneNumberHelper;
import com.android.dialer.PhoneCallDetails;
import com.android.dialer.R;
import com.android.dialer.calllog.calllogcache.CallLogCache;
import com.android.dialer.util.DialerUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * Helper class to fill in the views in {@link PhoneCallDetailsViews}.
 */
public class PhoneCallDetailsHelper {

    /** The maximum number of icons will be shown to represent the call types in a group. */
    private static final int MAX_CALL_TYPE_ICONS = 3;

    private final Context mContext;
    private final Resources mResources;
    /** The injected current time in milliseconds since the epoch. Used only by tests. */
    private Long mCurrentTimeMillisForTest;

    private CharSequence mPhoneTypeLabelForTest;

    private final CallLogCache mCallLogCache;

    /** Calendar used to construct dates */
    private final Calendar mCalendar;

    /**
     * List of items to be concatenated together for accessibility descriptions
     */
    private ArrayList<CharSequence> mDescriptionItems = Lists.newArrayList();

    /**
     * Creates a new instance of the helper.
     * <p>
     * Generally you should have a single instance of this helper in any context.
     *
     * @param resources used to look up strings
     */
    public PhoneCallDetailsHelper(
            Context context,
            Resources resources,
            CallLogCache callLogCache) {
        mContext = context;
        mResources = resources;
        mCallLogCache = callLogCache;
        mCalendar = Calendar.getInstance();
    }

    /** Fills the call details views with content. */
    public void setPhoneCallDetails(PhoneCallDetailsViews views, PhoneCallDetails details) {
        // Display up to a given number of icons.
        views.callTypeIcons.clear();
        int count = details.callTypes.length;
        boolean isVoicemail = false;
        for (int index = 0; index < count && index < MAX_CALL_TYPE_ICONS; ++index) {
            views.callTypeIcons.add(details.callTypes[index]);
            if (index == 0) {
                isVoicemail = details.callTypes[index] == Calls.VOICEMAIL_TYPE;
            }
        }

        // Show the video icon if the call had video enabled.
        views.callTypeIcons.setShowVideo(
                (details.features & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO);
        views.callTypeIcons.requestLayout();
        views.callTypeIcons.setVisibility(View.VISIBLE);

        // Show the total call count only if there are more than the maximum number of icons.
        final Integer callCount;
        if (count > MAX_CALL_TYPE_ICONS) {
            callCount = count;
        } else {
            callCount = null;
        }

        // Set the call count, location, date and if voicemail, set the duration.
        setDetailText(views, callCount, details);

        // Set the account label if it exists.
        String accountLabel = mCallLogCache.getAccountLabel(details.accountHandle);
        if (!TextUtils.isEmpty(details.viaNumber)) {
            if (!TextUtils.isEmpty(accountLabel)) {
                accountLabel = mResources.getString(R.string.call_log_via_number_phone_account,
                        accountLabel, details.viaNumber);
            } else {
                accountLabel = mResources.getString(R.string.call_log_via_number,
                        details.viaNumber);
            }
        }
        if (!TextUtils.isEmpty(accountLabel)) {
            views.callAccountLabel.setVisibility(View.VISIBLE);
            views.callAccountLabel.setText(accountLabel);
            int color = mCallLogCache.getAccountColor(details.accountHandle);
            if (color == PhoneAccount.NO_HIGHLIGHT_COLOR) {
                int defaultColor = R.color.dialtacts_secondary_text_color;
                views.callAccountLabel.setTextColor(mContext.getResources().getColor(defaultColor));
            } else {
                views.callAccountLabel.setTextColor(color);
            }
        } else {
            views.callAccountLabel.setVisibility(View.GONE);
        }

        final CharSequence nameText;
        final CharSequence displayNumber = details.displayNumber;
        if (TextUtils.isEmpty(details.getPreferredName())) {
            nameText = displayNumber;
            // We have a real phone number as "nameView" so make it always LTR
            views.nameView.setTextDirection(View.TEXT_DIRECTION_LTR);
        } else {
            nameText = details.getPreferredName();
        }

        views.nameView.setText(nameText);

        if (isVoicemail) {
            views.voicemailTranscriptionView.setText(TextUtils.isEmpty(details.transcription) ? null
                    : details.transcription);
        }

        // Bold if not read
        Typeface typeface = details.isRead ? Typeface.SANS_SERIF : Typeface.DEFAULT_BOLD;
        views.nameView.setTypeface(typeface);
        views.voicemailTranscriptionView.setTypeface(typeface);
        views.callLocationAndDate.setTypeface(typeface);
        views.callLocationAndDate.setTextColor(ContextCompat.getColor(mContext, details.isRead ?
                R.color.call_log_detail_color : R.color.call_log_unread_text_color));
    }

    /**
     * Builds a string containing the call location and date. For voicemail logs only the call date
     * is returned because location information is displayed in the call action button
     *
     * @param details The call details.
     * @return The call location and date string.
     */
    private CharSequence getCallLocationAndDate(PhoneCallDetails details) {
        mDescriptionItems.clear();

        if (details.callTypes[0] != Calls.VOICEMAIL_TYPE) {
            // Get type of call (ie mobile, home, etc) if known, or the caller's location.
            CharSequence callTypeOrLocation = getCallTypeOrLocation(details);

            // Only add the call type or location if its not empty.  It will be empty for unknown
            // callers.
            if (!TextUtils.isEmpty(callTypeOrLocation)) {
                mDescriptionItems.add(callTypeOrLocation);
            }
        }

        // The date of this call
        mDescriptionItems.add(getCallDate(details));

        // Create a comma separated list from the call type or location, and call date.
        return DialerUtils.join(mResources, mDescriptionItems);
    }

    /**
     * For a call, if there is an associated contact for the caller, return the known call type
     * (e.g. mobile, home, work).  If there is no associated contact, attempt to use the caller's
     * location if known.
     *
     * @param details Call details to use.
     * @return Type of call (mobile/home) if known, or the location of the caller (if known).
     */
    public CharSequence getCallTypeOrLocation(PhoneCallDetails details) {
        CharSequence numberFormattedLabel = null;
        // Only show a label if the number is shown and it is not a SIP address.
        if (!TextUtils.isEmpty(details.number)
                && !PhoneNumberHelper.isUriNumber(details.number.toString())
                && !mCallLogCache.isVoicemailNumber(details.accountHandle, details.number)) {

            if (TextUtils.isEmpty(details.namePrimary) && !TextUtils.isEmpty(details.geocode)) {
                numberFormattedLabel = details.geocode;
            } else if (!(details.numberType == Phone.TYPE_CUSTOM
                    && TextUtils.isEmpty(details.numberLabel))) {
                // Get type label only if it will not be "Custom" because of an empty number label.
                numberFormattedLabel = MoreObjects.firstNonNull(mPhoneTypeLabelForTest,
                        Phone.getTypeLabel(mResources, details.numberType, details.numberLabel));
            }
        }

        if (!TextUtils.isEmpty(details.namePrimary) && TextUtils.isEmpty(numberFormattedLabel)) {
            numberFormattedLabel = details.displayNumber;
        }
        return numberFormattedLabel;
    }

    @NeededForTesting
    public void setPhoneTypeLabelForTest(CharSequence phoneTypeLabel) {
        this.mPhoneTypeLabelForTest = phoneTypeLabel;
    }

    /**
     * Get the call date/time of the call. For the call log this is relative to the current time.
     * e.g. 3 minutes ago. For voicemail, see {@link #getGranularDateTime(PhoneCallDetails)}
     *
     * @param details Call details to use.
     * @return String representing when the call occurred.
     */
    public CharSequence getCallDate(PhoneCallDetails details) {
        if (details.callTypes[0] == Calls.VOICEMAIL_TYPE) {
            return getGranularDateTime(details);
        }

        return DateUtils.getRelativeTimeSpanString(details.date, getCurrentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
    }

    /**
     * Get the granular version of the call date/time of the call. The result is always in the form
     * 'DATE at TIME'. The date value changes based on when the call was created.
     *
     * If created today, DATE is 'Today'
     * If created this year, DATE is 'MMM dd'
     * Otherwise, DATE is 'MMM dd, yyyy'
     *
     * TIME is the localized time format, e.g. 'hh:mm a' or 'HH:mm'
     *
     * @param details Call details to use
     * @return String representing when the call occurred
     */
    public CharSequence getGranularDateTime(PhoneCallDetails details) {
        return mResources.getString(R.string.voicemailCallLogDateTimeFormat,
                getGranularDate(details.date),
                DateUtils.formatDateTime(mContext, details.date, DateUtils.FORMAT_SHOW_TIME));
    }

    /**
     * Get the granular version of the call date. See {@link #getGranularDateTime(PhoneCallDetails)}
     */
    private String getGranularDate(long date) {
        if (DateUtils.isToday(date)) {
            return mResources.getString(R.string.voicemailCallLogToday);
        }
        return DateUtils.formatDateTime(mContext, date, DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_ABBREV_MONTH
                | (shouldShowYear(date) ? DateUtils.FORMAT_SHOW_YEAR : DateUtils.FORMAT_NO_YEAR));
    }

    /**
     * Determines whether the year should be shown for the given date
     *
     * @return {@code true} if date is within the current year, {@code false} otherwise
     */
    private boolean shouldShowYear(long date) {
        mCalendar.setTimeInMillis(getCurrentTimeMillis());
        int currentYear = mCalendar.get(Calendar.YEAR);
        mCalendar.setTimeInMillis(date);
        return currentYear != mCalendar.get(Calendar.YEAR);
    }

    /** Sets the text of the header view for the details page of a phone call. */
    @NeededForTesting
    public void setCallDetailsHeader(TextView nameView, PhoneCallDetails details) {
        final CharSequence nameText;
        if (!TextUtils.isEmpty(details.namePrimary)) {
            nameText = details.namePrimary;
        } else if (!TextUtils.isEmpty(details.displayNumber)) {
            nameText = details.displayNumber;
        } else {
            nameText = mResources.getString(R.string.unknown);
        }

        nameView.setText(nameText);
    }

    @NeededForTesting
    public void setCurrentTimeForTest(long currentTimeMillis) {
        mCurrentTimeMillisForTest = currentTimeMillis;
    }

    /**
     * Returns the current time in milliseconds since the epoch.
     * <p>
     * It can be injected in tests using {@link #setCurrentTimeForTest(long)}.
     */
    private long getCurrentTimeMillis() {
        if (mCurrentTimeMillisForTest == null) {
            return System.currentTimeMillis();
        } else {
            return mCurrentTimeMillisForTest;
        }
    }

    /** Sets the call count, date, and if it is a voicemail, sets the duration. */
    private void setDetailText(PhoneCallDetailsViews views, Integer callCount,
                               PhoneCallDetails details) {
        // Combine the count (if present) and the date.
        CharSequence dateText = getCallLocationAndDate(details);
        final CharSequence text;
        if (callCount != null) {
            text = mResources.getString(
                    R.string.call_log_item_count_and_date, callCount.intValue(), dateText);
        } else {
            text = dateText;
        }

        if (details.callTypes[0] == Calls.VOICEMAIL_TYPE && details.duration > 0) {
            views.callLocationAndDate.setText(mResources.getString(
                    R.string.voicemailCallLogDateTimeFormatWithDuration, text,
                    getVoicemailDuration(details)));
        } else {
            views.callLocationAndDate.setText(text);
        }

    }

    private String getVoicemailDuration(PhoneCallDetails details) {
        long minutes = TimeUnit.SECONDS.toMinutes(details.duration);
        long seconds = details.duration - TimeUnit.MINUTES.toSeconds(minutes);
        if (minutes > 99) {
            minutes = 99;
        }
        return mResources.getString(R.string.voicemailDurationFormat, minutes, seconds);
    }
}
