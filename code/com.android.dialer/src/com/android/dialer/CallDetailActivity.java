/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.dialer;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.VoicemailContract.Voicemails;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.util.PermissionsUtil;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.CallUtil;
import com.android.dialer.calllog.CallDetailHistoryAdapter;
import com.android.dialer.calllog.CallLogAsyncTaskUtil.CallLogAsyncTaskListener;
import com.android.dialer.calllog.CallLogAsyncTaskUtil;
import com.android.dialer.calllog.CallTypeHelper;
import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.calllog.PhoneAccountUtils;
import com.android.dialer.calllog.PhoneNumberDisplayUtil;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.IntentUtil;
import com.android.dialer.util.PhoneNumberUtil;
import com.android.dialer.util.TelecomUtil;

import java.util.List;

/**
 * Displays the details of a specific call log entry.
 * <p>
 * This activity can be either started with the URI of a single call log entry, or with the
 * {@link #EXTRA_CALL_LOG_IDS} extra to specify a group of call log entries.
 */
public class CallDetailActivity extends Activity
        implements MenuItem.OnMenuItemClickListener {
    private static final String TAG = "CallDetail";

     /** A long array extra containing ids of call log entries to display. */
    public static final String EXTRA_CALL_LOG_IDS = "EXTRA_CALL_LOG_IDS";
    /** If we are started with a voicemail, we'll find the uri to play with this extra. */
    public static final String EXTRA_VOICEMAIL_URI = "EXTRA_VOICEMAIL_URI";
    /** If the activity was triggered from a notification. */
    public static final String EXTRA_FROM_NOTIFICATION = "EXTRA_FROM_NOTIFICATION";

    public static final String VOICEMAIL_FRAGMENT_TAG = "voicemail_fragment";

    private CallLogAsyncTaskListener mCallLogAsyncTaskListener = new CallLogAsyncTaskListener() {
        @Override
        public void onDeleteCall() {
            finish();
        }

        @Override
        public void onDeleteVoicemail() {
            finish();
        }

        @Override
        public void onGetCallDetails(PhoneCallDetails[] details) {
            if (details == null) {
                // Somewhere went wrong: we're going to bail out and show error to users.
                Toast.makeText(mContext, R.string.toast_call_detail_error,
                        Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            // We know that all calls are from the same number and the same contact, so pick the
            // first.
            PhoneCallDetails firstDetails = details[0];
            mNumber = TextUtils.isEmpty(firstDetails.number) ?
                    null : firstDetails.number.toString();
            final int numberPresentation = firstDetails.numberPresentation;
            final Uri contactUri = firstDetails.contactUri;
            final Uri photoUri = firstDetails.photoUri;
            final PhoneAccountHandle accountHandle = firstDetails.accountHandle;

            // Cache the details about the phone number.
            final boolean canPlaceCallsTo =
                    PhoneNumberUtil.canPlaceCallsTo(mNumber, numberPresentation);
            mIsVoicemailNumber =
                    PhoneNumberUtil.isVoicemailNumber(mContext, accountHandle, mNumber);
            final boolean isSipNumber = PhoneNumberUtil.isSipNumber(mNumber);

            final CharSequence callLocationOrType = getNumberTypeOrLocation(firstDetails);

            final CharSequence displayNumber = firstDetails.displayNumber;
            final String displayNumberStr = mBidiFormatter.unicodeWrap(
                    displayNumber.toString(), TextDirectionHeuristics.LTR);

            if (!TextUtils.isEmpty(firstDetails.name)) {
                mCallerName.setText(firstDetails.name);
                mCallerNumber.setText(callLocationOrType + " " + displayNumberStr);
            } else {
                mCallerName.setText(displayNumberStr);
                if (!TextUtils.isEmpty(callLocationOrType)) {
                    mCallerNumber.setText(callLocationOrType);
                    mCallerNumber.setVisibility(View.VISIBLE);
                } else {
                    mCallerNumber.setVisibility(View.GONE);
                }
            }

            mCallButton.setVisibility(canPlaceCallsTo ? View.VISIBLE : View.GONE);

            String accountLabel = PhoneAccountUtils.getAccountLabel(mContext, accountHandle);
            if (!TextUtils.isEmpty(accountLabel)) {
                mAccountLabel.setText(accountLabel);
                mAccountLabel.setVisibility(View.VISIBLE);
            } else {
                mAccountLabel.setVisibility(View.GONE);
            }

            mHasEditNumberBeforeCallOption =
                    canPlaceCallsTo && !isSipNumber && !mIsVoicemailNumber;
            mHasReportMenuOption = mContactInfoHelper.canReportAsInvalid(
                    firstDetails.sourceType, firstDetails.objectId);
            invalidateOptionsMenu();

            ListView historyList = (ListView) findViewById(R.id.history);
            historyList.setAdapter(
                    new CallDetailHistoryAdapter(mContext, mInflater, mCallTypeHelper, details));

            String lookupKey = contactUri == null ? null
                    : ContactInfoHelper.getLookupKeyFromUri(contactUri);

            final boolean isBusiness = mContactInfoHelper.isBusiness(firstDetails.sourceType);

            final int contactType =
                    mIsVoicemailNumber ? ContactPhotoManager.TYPE_VOICEMAIL :
                    isBusiness ? ContactPhotoManager.TYPE_BUSINESS :
                    ContactPhotoManager.TYPE_DEFAULT;

            String nameForDefaultImage;
            if (TextUtils.isEmpty(firstDetails.name)) {
                nameForDefaultImage = firstDetails.displayNumber;
            } else {
                nameForDefaultImage = firstDetails.name.toString();
            }

            loadContactPhotos(
                    contactUri, photoUri, nameForDefaultImage, lookupKey, contactType);
            findViewById(R.id.call_detail).setVisibility(View.VISIBLE);
        }

        /**
         * Determines the location geocode text for a call, or the phone number type
         * (if available).
         *
         * @param details The call details.
         * @return The phone number type or location.
         */
        private CharSequence getNumberTypeOrLocation(PhoneCallDetails details) {
            if (!TextUtils.isEmpty(details.name)) {
                return Phone.getTypeLabel(mResources, details.numberType,
                        details.numberLabel);
            } else {
                return details.geocode;
            }
        }
    };

    private Context mContext;
    private CallTypeHelper mCallTypeHelper;
    private QuickContactBadge mQuickContactBadge;
    private TextView mCallerName;
    private TextView mCallerNumber;
    private TextView mAccountLabel;
    private View mCallButton;
    private ContactInfoHelper mContactInfoHelper;

    protected String mNumber;
    private boolean mIsVoicemailNumber;
    private String mDefaultCountryIso;

    /* package */ LayoutInflater mInflater;
    /* package */ Resources mResources;
    /** Helper to load contact photos. */
    private ContactPhotoManager mContactPhotoManager;

    private Uri mVoicemailUri;
    private BidiFormatter mBidiFormatter = BidiFormatter.getInstance();

    /** Whether we should show "edit number before call" in the options menu. */
    private boolean mHasEditNumberBeforeCallOption;
    private boolean mHasReportMenuOption;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mContext = this;

        setContentView(R.layout.call_detail);

        mInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        mResources = getResources();

        mCallTypeHelper = new CallTypeHelper(getResources());

        mVoicemailUri = getIntent().getParcelableExtra(EXTRA_VOICEMAIL_URI);

        mQuickContactBadge = (QuickContactBadge) findViewById(R.id.quick_contact_photo);
        mQuickContactBadge.setOverlay(null);
        if (PermissionsUtil.sIsAtLeastM){
        	mQuickContactBadge.setPrioritizedMimeType(Phone.CONTENT_ITEM_TYPE);
        }else{
        	
        }

        mCallerName = (TextView) findViewById(R.id.caller_name);
        mCallerNumber = (TextView) findViewById(R.id.caller_number);
        mAccountLabel = (TextView) findViewById(R.id.phone_account_label);
        mDefaultCountryIso = GeoUtil.getCurrentCountryIso(this);
        mContactPhotoManager = ContactPhotoManager.getInstance(this);

        mCallButton = (View) findViewById(R.id.call_back_button);
        mCallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mContext.startActivity(IntentUtil.getCallIntent(mNumber));
            }
        });

        mContactInfoHelper = new ContactInfoHelper(this, GeoUtil.getCurrentCountryIso(this));
        getActionBar().setDisplayHomeAsUpEnabled(true);

        if (getIntent().getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
            closeSystemDialogs();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getCallDetails();
    }

    public void getCallDetails() {
        CallLogAsyncTaskUtil.getCallDetails(this, getCallLogEntryUris(), mCallLogAsyncTaskListener);
    }

    private boolean hasVoicemail() {
        return mVoicemailUri != null;
    }

    /**
     * Returns the list of URIs to show.
     * <p>
     * There are two ways the URIs can be provided to the activity: as the data on the intent, or as
     * a list of ids in the call log added as an extra on the URI.
     * <p>
     * If both are available, the data on the intent takes precedence.
     */
    private Uri[] getCallLogEntryUris() {
        final Uri uri = getIntent().getData();
        if (uri != null) {
            // If there is a data on the intent, it takes precedence over the extra.
            return new Uri[]{ uri };
        }
        final long[] ids = getIntent().getLongArrayExtra(EXTRA_CALL_LOG_IDS);
        final int numIds = ids == null ? 0 : ids.length;
        final Uri[] uris = new Uri[numIds];
        for (int index = 0; index < numIds; ++index) {
            uris[index] = ContentUris.withAppendedId(
                    TelecomUtil.getCallLogUri(CallDetailActivity.this), ids[index]);
        }
        return uris;
    }

    /** Load the contact photos and places them in the corresponding views. */
    private void loadContactPhotos(Uri contactUri, Uri photoUri, String displayName,
            String lookupKey, int contactType) {

        final DefaultImageRequest request = new DefaultImageRequest(displayName, lookupKey,
                contactType, true /* isCircular */);

        mQuickContactBadge.assignContactUri(contactUri);
        mQuickContactBadge.setContentDescription(
                mResources.getString(R.string.description_contact_details, displayName));

        mContactPhotoManager.loadDirectoryPhoto(mQuickContactBadge, photoUri,
                false /* darkTheme */, true /* isCircular */, request);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.call_details_options, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // This action deletes all elements in the group from the call log.
        // We don't have this action for voicemails, because you can just use the trash button.
        menu.findItem(R.id.menu_remove_from_call_log)
                .setVisible(!hasVoicemail())
                .setOnMenuItemClickListener(this);
        menu.findItem(R.id.menu_edit_number_before_call)
                .setVisible(mHasEditNumberBeforeCallOption)
                .setOnMenuItemClickListener(this);
        menu.findItem(R.id.menu_trash)
                .setVisible(hasVoicemail())
                .setOnMenuItemClickListener(this);
        menu.findItem(R.id.menu_report)
                .setVisible(mHasReportMenuOption)
                .setOnMenuItemClickListener(this);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_remove_from_call_log:
                final StringBuilder callIds = new StringBuilder();
                for (Uri callUri : getCallLogEntryUris()) {
                    if (callIds.length() != 0) {
                        callIds.append(",");
                    }
                    callIds.append(ContentUris.parseId(callUri));
                }
                CallLogAsyncTaskUtil.deleteCalls(
                        this, callIds.toString(), mCallLogAsyncTaskListener);
                break;
            case R.id.menu_edit_number_before_call:
                startActivity(new Intent(Intent.ACTION_DIAL, CallUtil.getCallUri(mNumber)));
                break;
            case R.id.menu_trash:
                CallLogAsyncTaskUtil.deleteVoicemail(
                        this, mVoicemailUri, mCallLogAsyncTaskListener);
                break;
        }
        return true;
    }

    private void closeSystemDialogs() {
        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    }
}
