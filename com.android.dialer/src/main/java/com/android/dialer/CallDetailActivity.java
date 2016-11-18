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

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.v7.app.AppCompatActivity;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.ClipboardUtils;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.interactions.TouchPointManager;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.common.testing.NeededForTesting;
import com.android.contacts.common.util.UriUtils;
import com.android.dialer.calllog.CallDetailHistoryAdapter;
import com.android.dialer.calllog.CallLogAsyncTaskUtil;
import com.android.dialer.calllog.CallLogAsyncTaskUtil.CallLogAsyncTaskListener;
import com.android.dialer.calllog.CallTypeHelper;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.calllog.PhoneAccountUtils;
import com.android.dialer.compat.FilteredNumberCompat;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler.OnCheckBlockedListener;
import com.android.dialer.filterednumber.BlockNumberDialogFragment;
import com.android.dialer.filterednumber.FilteredNumbersUtil;
import com.android.dialer.logging.InteractionEvent;
import com.android.dialer.logging.Logger;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.IntentUtil.CallIntentBuilder;
import com.android.dialer.util.PhoneNumberUtil;
import com.android.dialer.util.TelecomUtil;
import com.android.incallui.Call.LogState;

/**
 * Displays the details of a specific call log entry.
 * <p>
 * This activity can be either started with the URI of a single call log entry, or with the
 * {@link #EXTRA_CALL_LOG_IDS} extra to specify a group of call log entries.
 */
public class CallDetailActivity extends AppCompatActivity
        implements MenuItem.OnMenuItemClickListener, View.OnClickListener,
                BlockNumberDialogFragment.Callback {
    private static final String TAG = CallDetailActivity.class.getSimpleName();

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

            // All calls are from the same number and same contact, so pick the first detail.
            mDetails = details[0];
            mNumber = TextUtils.isEmpty(mDetails.number) ? null : mDetails.number.toString();
            mPostDialDigits = TextUtils.isEmpty(mDetails.postDialDigits)
                    ? "" : mDetails.postDialDigits;
            mDisplayNumber = mDetails.displayNumber;

            final CharSequence callLocationOrType = getNumberTypeOrLocation(mDetails);

            final CharSequence displayNumber;
            if (!TextUtils.isEmpty(mDetails.postDialDigits)) {
                displayNumber = mDetails.number + mDetails.postDialDigits;
            } else {
                displayNumber = mDetails.displayNumber;
            }

            final String displayNumberStr = mBidiFormatter.unicodeWrap(
                    displayNumber.toString(), TextDirectionHeuristics.LTR);

            mDetails.nameDisplayOrder = mContactsPreferences.getDisplayOrder();

            if (!TextUtils.isEmpty(mDetails.getPreferredName())) {
                mCallerName.setText(mDetails.getPreferredName());
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

            CharSequence accountLabel = PhoneAccountUtils.getAccountLabel(mContext,
                    mDetails.accountHandle);
            CharSequence accountContentDescription =
                    PhoneCallDetails.createAccountLabelDescription(mResources, mDetails.viaNumber,
                            accountLabel);
            if (!TextUtils.isEmpty(mDetails.viaNumber)) {
                if (!TextUtils.isEmpty(accountLabel)) {
                    accountLabel = mResources.getString(R.string.call_log_via_number_phone_account,
                            accountLabel, mDetails.viaNumber);
                } else {
                    accountLabel = mResources.getString(R.string.call_log_via_number,
                            mDetails.viaNumber);
                }
            }
            if (!TextUtils.isEmpty(accountLabel)) {
                mAccountLabel.setText(accountLabel);
                mAccountLabel.setContentDescription(accountContentDescription);
                mAccountLabel.setVisibility(View.VISIBLE);
            } else {
                mAccountLabel.setVisibility(View.GONE);
            }

            final boolean canPlaceCallsTo =
                    PhoneNumberUtil.canPlaceCallsTo(mNumber, mDetails.numberPresentation);
            mCallButton.setVisibility(canPlaceCallsTo ? View.VISIBLE : View.GONE);
            mCopyNumberActionItem.setVisibility(canPlaceCallsTo ? View.VISIBLE : View.GONE);

            updateBlockActionItemVisibility(canPlaceCallsTo ? View.VISIBLE : View.GONE);

            final boolean isSipNumber = PhoneNumberUtil.isSipNumber(mNumber);
            final boolean isVoicemailNumber =
                    PhoneNumberUtil.isVoicemailNumber(mContext, mDetails.accountHandle, mNumber);
            final boolean showEditNumberBeforeCallAction =
                    canPlaceCallsTo && !isSipNumber && !isVoicemailNumber;
            mEditBeforeCallActionItem.setVisibility(
                    showEditNumberBeforeCallAction ? View.VISIBLE : View.GONE);

            final boolean showReportAction = mContactInfoHelper.canReportAsInvalid(
                    mDetails.sourceType, mDetails.objectId);
            mReportActionItem.setVisibility(
                    showReportAction ? View.VISIBLE : View.GONE);

            invalidateOptionsMenu();

            mHistoryList.setAdapter(
                    new CallDetailHistoryAdapter(mContext, mInflater, mCallTypeHelper, details));

            updateFilteredNumberChanges();
            updateContactPhoto();

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
            if (!TextUtils.isEmpty(details.namePrimary)) {
                return Phone.getTypeLabel(mResources, details.numberType,
                        details.numberLabel);
            } else {
                return details.geocode;
            }
        }
    };

    private Context mContext;
    private ContactInfoHelper mContactInfoHelper;
    private ContactsPreferences mContactsPreferences;
    private CallTypeHelper mCallTypeHelper;
    private ContactPhotoManager mContactPhotoManager;
    private FilteredNumberAsyncQueryHandler mFilteredNumberAsyncQueryHandler;
    private BidiFormatter mBidiFormatter = BidiFormatter.getInstance();
    private LayoutInflater mInflater;
    private Resources mResources;

    private PhoneCallDetails mDetails;
    protected String mNumber;
    private Uri mVoicemailUri;
    private String mPostDialDigits = "";
    private String mDisplayNumber;

    private ListView mHistoryList;
    private QuickContactBadge mQuickContactBadge;
    private TextView mCallerName;
    private TextView mCallerNumber;
    private TextView mAccountLabel;
    private View mCallButton;

    private TextView mBlockNumberActionItem;
    private View mEditBeforeCallActionItem;
    private View mReportActionItem;
    private View mCopyNumberActionItem;

    private Integer mBlockedNumberId;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mContext = this;
        mResources = getResources();
        mContactInfoHelper = new ContactInfoHelper(this, GeoUtil.getCurrentCountryIso(this));
        mContactsPreferences = new ContactsPreferences(mContext);
        mCallTypeHelper = new CallTypeHelper(getResources());
        mFilteredNumberAsyncQueryHandler =
                new FilteredNumberAsyncQueryHandler(getContentResolver());

        mVoicemailUri = getIntent().getParcelableExtra(EXTRA_VOICEMAIL_URI);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setContentView(R.layout.call_detail);
        mInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

        mHistoryList = (ListView) findViewById(R.id.history);
        mHistoryList.addHeaderView(mInflater.inflate(R.layout.call_detail_header, null));
        mHistoryList.addFooterView(
                mInflater.inflate(R.layout.call_detail_footer, null), null, false);

        mQuickContactBadge = (QuickContactBadge) findViewById(R.id.quick_contact_photo);
        mQuickContactBadge.setOverlay(null);
        if (CompatUtils.hasPrioritizedMimeType()) {
            mQuickContactBadge.setPrioritizedMimeType(Phone.CONTENT_ITEM_TYPE);
        }
        mCallerName = (TextView) findViewById(R.id.caller_name);
        mCallerNumber = (TextView) findViewById(R.id.caller_number);
        mAccountLabel = (TextView) findViewById(R.id.phone_account_label);
        mContactPhotoManager = ContactPhotoManager.getInstance(this);

        mCallButton = findViewById(R.id.call_back_button);
        mCallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (TextUtils.isEmpty(mNumber)) {
                    return;
                }
                mContext.startActivity(
                        new CallIntentBuilder(getDialableNumber())
                                .setCallInitiationType(LogState.INITIATION_CALL_DETAILS)
                                .build());
            }
        });


        mBlockNumberActionItem = (TextView) findViewById(R.id.call_detail_action_block);
        updateBlockActionItemVisibility(View.VISIBLE);
        mBlockNumberActionItem.setOnClickListener(this);
        mEditBeforeCallActionItem = findViewById(R.id.call_detail_action_edit_before_call);
        mEditBeforeCallActionItem.setOnClickListener(this);
        mReportActionItem = findViewById(R.id.call_detail_action_report);
        mReportActionItem.setOnClickListener(this);

        mCopyNumberActionItem = findViewById(R.id.call_detail_action_copy);
        mCopyNumberActionItem.setOnClickListener(this);

        if (getIntent().getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
            closeSystemDialogs();
        }
    }

    private void updateBlockActionItemVisibility(int visibility) {
        if (!FilteredNumberCompat.canAttemptBlockOperations(mContext)) {
            visibility = View.GONE;
        }
        mBlockNumberActionItem.setVisibility(visibility);
    }

    @Override
    public void onResume() {
        super.onResume();
        mContactsPreferences.refreshValue(ContactsPreferences.DISPLAY_ORDER_KEY);
        getCallDetails();
        StatisticsUtil.onResume(this);
    }

    @Override
    protected void onPause() {
        StatisticsUtil.onPause(this);
        super.onPause();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            TouchPointManager.getInstance().setPoint((int) ev.getRawX(), (int) ev.getRawY());
        }
        return super.dispatchTouchEvent(ev);
    }

    public void getCallDetails() {
        CallLogAsyncTaskUtil.getCallDetails(this, getCallLogEntryUris(), mCallLogAsyncTaskListener);
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final MenuItem deleteMenuItem = menu.add(
                Menu.NONE,
                R.id.call_detail_delete_menu_item,
                Menu.NONE,
                R.string.call_details_delete);
        deleteMenuItem.setIcon(R.drawable.ic_delete_24dp);
        deleteMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        deleteMenuItem.setOnMenuItemClickListener(this);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.call_detail_delete_menu_item) {
            if (hasVoicemail()) {
                CallLogAsyncTaskUtil.deleteVoicemail(
                        this, mVoicemailUri, mCallLogAsyncTaskListener);
            } else {
                final StringBuilder callIds = new StringBuilder();
                for (Uri callUri : getCallLogEntryUris()) {
                    if (callIds.length() != 0) {
                        callIds.append(",");
                    }
                    callIds.append(ContentUris.parseId(callUri));
                }
                CallLogAsyncTaskUtil.deleteCalls(
                        this, callIds.toString(), mCallLogAsyncTaskListener);
            }
        }
        return true;
    }

    @Override
    public void onClick(View view) {
        int resId = view.getId();
        if (resId == R.id.call_detail_action_block) {
            FilteredNumberCompat
                    .showBlockNumberDialogFlow(mContext.getContentResolver(), mBlockedNumberId,
                            mNumber, mDetails.countryIso, mDisplayNumber, R.id.call_detail,
                            getFragmentManager(), this);
        } else if (resId == R.id.call_detail_action_copy) {
            ClipboardUtils.copyText(mContext, null, mNumber, true);
        } else if (resId == R.id.call_detail_action_edit_before_call) {
            Intent dialIntent = new Intent(Intent.ACTION_DIAL,
                    CallUtil.getCallUri(getDialableNumber()));
            DialerUtils.startActivityWithErrorToast(mContext, dialIntent);
        } else {
            Log.wtf(TAG, "Unexpected onClick event from " + view);
        }
    }

    @Override
    public void onFilterNumberSuccess() {
        Logger.logInteraction(InteractionEvent.BLOCK_NUMBER_CALL_DETAIL);
        updateFilteredNumberChanges();
    }

    @Override
    public void onUnfilterNumberSuccess() {
        Logger.logInteraction(InteractionEvent.UNBLOCK_NUMBER_CALL_DETAIL);
        updateFilteredNumberChanges();
    }

    @Override
    public void onChangeFilteredNumberUndo() {
        updateFilteredNumberChanges();
    }

    private void updateFilteredNumberChanges() {
        if (mDetails == null ||
                !FilteredNumbersUtil.canBlockNumber(this, mNumber, mDetails.countryIso)) {
            return;
        }

        final boolean success = mFilteredNumberAsyncQueryHandler.isBlockedNumber(
                new OnCheckBlockedListener() {
                    @Override
                    public void onCheckComplete(Integer id) {
                        mBlockedNumberId = id;
                        updateBlockActionItem();
                    }
                }, mNumber, mDetails.countryIso);

        if (!success) {
            updateBlockActionItem();
        }
    }

    // Loads and displays the contact photo.
    private void updateContactPhoto() {
        if (mDetails == null) {
            return;
        }

        final boolean isVoicemailNumber =
                PhoneNumberUtil.isVoicemailNumber(mContext, mDetails.accountHandle, mNumber);
        final boolean isBusiness = mContactInfoHelper.isBusiness(mDetails.sourceType);
        int contactType = ContactPhotoManager.TYPE_DEFAULT;
        if (isVoicemailNumber) {
            contactType = ContactPhotoManager.TYPE_VOICEMAIL;
        } else if (isBusiness) {
            contactType = ContactPhotoManager.TYPE_BUSINESS;
        }

        final String displayName = TextUtils.isEmpty(mDetails.namePrimary)
                ? mDetails.displayNumber : mDetails.namePrimary.toString();
        final String lookupKey = mDetails.contactUri == null
                ? null : UriUtils.getLookupKeyFromUri(mDetails.contactUri);

        final DefaultImageRequest request =
                new DefaultImageRequest(displayName, lookupKey, contactType, true /* isCircular */);

        mQuickContactBadge.assignContactUri(mDetails.contactUri);
        mQuickContactBadge.setContentDescription(
                mResources.getString(R.string.description_contact_details, displayName));

        mContactPhotoManager.loadDirectoryPhoto(mQuickContactBadge, mDetails.photoUri,
                false /* darkTheme */, true /* isCircular */, request);
    }

    private void updateBlockActionItem() {
        if (mBlockedNumberId == null) {
            mBlockNumberActionItem.setText(R.string.action_block_number);
            mBlockNumberActionItem.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_call_detail_block, 0, 0, 0);
        } else {
            mBlockNumberActionItem.setText(R.string.action_unblock_number);
            mBlockNumberActionItem.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_call_detail_unblock, 0, 0, 0);
        }
    }

    private void closeSystemDialogs() {
        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    }

    private String getDialableNumber() {
        return mNumber + mPostDialDigits;
    }

    @NeededForTesting
    public boolean hasVoicemail() {
        return mVoicemailUri != null;
    }
}
