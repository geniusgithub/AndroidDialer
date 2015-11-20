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

import android.content.Context;
import android.content.res.Resources;
import android.provider.CallLog.Calls;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Log;

import com.android.dialer.PhoneCallDetails;
import com.android.dialer.R;

/**
 * Helper class to fill in the views of a call log entry.
 */
/* package */class CallLogListItemHelper {
    private static final String TAG = "CallLogListItemHelper";

    /** Helper for populating the details of a phone call. */
    private final PhoneCallDetailsHelper mPhoneCallDetailsHelper;
    /** Resources to look up strings. */
    private final Resources mResources;
    private final TelecomCallLogCache mTelecomCallLogCache;

    /**
     * Creates a new helper instance.
     *
     * @param phoneCallDetailsHelper used to set the details of a phone call
     * @param phoneNumberHelper used to process phone number
     */
    public CallLogListItemHelper(
            PhoneCallDetailsHelper phoneCallDetailsHelper,
            Resources resources,
            TelecomCallLogCache telecomCallLogCache) {
        mPhoneCallDetailsHelper = phoneCallDetailsHelper;
        mResources = resources;
        mTelecomCallLogCache = telecomCallLogCache;
    }

    /**
     * Sets the name, label, and number for a contact.
     *
     * @param context The application context.
     * @param views the views to populate
     * @param details the details of a phone call needed to fill in the data
     */
    public void setPhoneCallDetails(
            CallLogListItemViewHolder views,
            PhoneCallDetails details) {
        mPhoneCallDetailsHelper.setPhoneCallDetails(views.phoneCallDetailsViews, details);

        // Set the accessibility text for the contact badge
        views.quickContactView.setContentDescription(getContactBadgeDescription(details));

        // Set the primary action accessibility description
        views.primaryActionView.setContentDescription(getCallDescription(details));

        // Cache name or number of caller.  Used when setting the content descriptions of buttons
        // when the actions ViewStub is inflated.
        views.nameOrNumber = getNameOrNumber(details);
    }

    /**
     * Sets the accessibility descriptions for the action buttons in the action button ViewStub.
     *
     * @param views The views associated with the current call log entry.
     */
    public void setActionContentDescriptions(CallLogListItemViewHolder views) {
        if (views.nameOrNumber == null) {
            Log.e(TAG, "setActionContentDescriptions; name or number is null.");
        }

        // Calling expandTemplate with a null parameter will cause a NullPointerException.
        // Although we don't expect a null name or number, it is best to protect against it.
        CharSequence nameOrNumber = views.nameOrNumber == null ? "" : views.nameOrNumber;

        views.videoCallButtonView.setContentDescription(
                TextUtils.expandTemplate(
                        mResources.getString(R.string.description_video_call_action),
                        nameOrNumber));

        views.createNewContactButtonView.setContentDescription(
                TextUtils.expandTemplate(
                        mResources.getString(R.string.description_create_new_contact_action),
                        nameOrNumber));

        views.addToExistingContactButtonView.setContentDescription(
                TextUtils.expandTemplate(
                        mResources.getString(R.string.description_add_to_existing_contact_action),
                        nameOrNumber));

        views.detailsButtonView.setContentDescription(
                TextUtils.expandTemplate(
                        mResources.getString(R.string.description_details_action), nameOrNumber));
    }

    /**
     * Returns the accessibility description for the contact badge for a call log entry.
     *
     * @param details Details of call.
     * @return Accessibility description.
     */
    private CharSequence getContactBadgeDescription(PhoneCallDetails details) {
        return mResources.getString(R.string.description_contact_details, getNameOrNumber(details));
    }

    /**
     * Returns the accessibility description of the "return call/call" action for a call log
     * entry.
     * Accessibility text is a combination of:
     * {Voicemail Prefix}. {Number of Calls}. {Caller information} {Phone Account}.
     * If most recent call is a voicemail, {Voicemail Prefix} is "New Voicemail.", otherwise "".
     *
     * If more than one call for the caller, {Number of Calls} is:
     * "{number of calls} calls.", otherwise "".
     *
     * The {Caller Information} references the most recent call associated with the caller.
     * For incoming calls:
     * If missed call:  Missed call from {Name/Number} {Call Type} {Call Time}.
     * If answered call: Answered call from {Name/Number} {Call Type} {Call Time}.
     *
     * For outgoing calls:
     * If outgoing:  Call to {Name/Number] {Call Type} {Call Time}.
     *
     * Where:
     * {Name/Number} is the name or number of the caller (as shown in call log).
     * {Call type} is the contact phone number type (eg mobile) or location.
     * {Call Time} is the time since the last call for the contact occurred.
     *
     * The {Phone Account} refers to the account/SIM through which the call was placed or received
     * in multi-SIM devices.
     *
     * Examples:
     * 3 calls.  New Voicemail.  Missed call from Joe Smith mobile 2 hours ago on SIM 1.
     *
     * 2 calls.  Answered call from John Doe mobile 1 hour ago.
     *
     * @param context The application context.
     * @param details Details of call.
     * @return Return call action description.
     */
    public CharSequence getCallDescription(PhoneCallDetails details) {
        int lastCallType = getLastCallType(details.callTypes);
        boolean isVoiceMail = lastCallType == Calls.VOICEMAIL_TYPE;

        // Get the name or number of the caller.
        final CharSequence nameOrNumber = getNameOrNumber(details);

        // Get the call type or location of the caller; null if not applicable
        final CharSequence typeOrLocation = mPhoneCallDetailsHelper.getCallTypeOrLocation(details);

        // Get the time/date of the call
        final CharSequence timeOfCall = mPhoneCallDetailsHelper.getCallDate(details);

        SpannableStringBuilder callDescription = new SpannableStringBuilder();

        // Prepend the voicemail indication.
        if (isVoiceMail) {
            callDescription.append(mResources.getString(R.string.description_new_voicemail));
        }

        // Add number of calls if more than one.
        if (details.callTypes.length > 1) {
            callDescription.append(mResources.getString(R.string.description_num_calls,
                    details.callTypes.length));
        }

        // If call had video capabilities, add the "Video Call" string.
        if ((details.features & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO) {
            callDescription.append(mResources.getString(R.string.description_video_call));
        }

        int stringID = getCallDescriptionStringID(details.callTypes);
        String accountLabel = mTelecomCallLogCache.getAccountLabel(details.accountHandle);

        // Use chosen string resource to build up the message.
        CharSequence onAccountLabel = accountLabel == null
                ? ""
                : TextUtils.expandTemplate(
                        mResources.getString(R.string.description_phone_account),
                        accountLabel);
        callDescription.append(
                TextUtils.expandTemplate(
                        mResources.getString(stringID),
                        nameOrNumber,
                        // If no type or location can be determined, sub in empty string.
                        typeOrLocation == null ? "" : typeOrLocation,
                        timeOfCall,
                        onAccountLabel));

        return callDescription;
    }

    /**
     * Determine the appropriate string ID to describe a call for accessibility purposes.
     *
     * @param details Call details.
     * @return String resource ID to use.
     */
    public int getCallDescriptionStringID(int[] callTypes) {
        int lastCallType = getLastCallType(callTypes);
        int stringID;

        if (lastCallType == Calls.VOICEMAIL_TYPE || lastCallType == Calls.MISSED_TYPE) {
            //Message: Missed call from <NameOrNumber>, <TypeOrLocation>, <TimeOfCall>,
            //<PhoneAccount>.
            stringID = R.string.description_incoming_missed_call;
        } else if (lastCallType == Calls.INCOMING_TYPE) {
            //Message: Answered call from <NameOrNumber>, <TypeOrLocation>, <TimeOfCall>,
            //<PhoneAccount>.
            stringID = R.string.description_incoming_answered_call;
        } else {
            //Message: Call to <NameOrNumber>, <TypeOrLocation>, <TimeOfCall>, <PhoneAccount>.
            stringID = R.string.description_outgoing_call;
        }
        return stringID;
    }

    /**
     * Determine the call type for the most recent call.
     * @param callTypes Call types to check.
     * @return Call type.
     */
    private int getLastCallType(int[] callTypes) {
        if (callTypes.length > 0) {
            return callTypes[0];
        } else {
            return Calls.MISSED_TYPE;
        }
    }

    /**
     * Return the name or number of the caller specified by the details.
     * @param details Call details
     * @return the name (if known) of the caller, otherwise the formatted number.
     */
    private CharSequence getNameOrNumber(PhoneCallDetails details) {
        final CharSequence recipient;
        if (!TextUtils.isEmpty(details.name)) {
            recipient = details.name;
        } else {
            recipient = details.displayNumber;
        }
        return recipient;
    }
}
