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
import android.content.Intent;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.widget.QuickContactBadge;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.testing.NeededForTesting;
import com.android.contacts.common.util.UriUtils;
import com.android.dialer.R;
import com.android.dialer.calllog.CallLogAsyncTaskUtil;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.PhoneNumberUtil;
import com.android.dialer.voicemail.VoicemailPlaybackPresenter;
import com.android.dialer.voicemail.VoicemailPlaybackLayout;

/**
 * This is an object containing references to views contained by the call log list item. This
 * improves performance by reducing the frequency with which we need to find views by IDs.
 *
 * This object also contains UI logic pertaining to the view, to isolate it from the CallLogAdapter.
 */
public final class CallLogListItemViewHolder extends RecyclerView.ViewHolder
        implements View.OnClickListener {

    /** The root view of the call log list item */
    public final View rootView;
    /** The quick contact badge for the contact. */
    public final QuickContactBadge quickContactView;
    /** The primary action view of the entry. */
    public final View primaryActionView;
    /** The details of the phone call. */
    public final PhoneCallDetailsViews phoneCallDetailsViews;
    /** The text of the header for a day grouping. */
    public final TextView dayGroupHeader;
    /** The view containing the details for the call log row, including the action buttons. */
    public final CardView callLogEntryView;
    /** The actionable view which places a call to the number corresponding to the call log row. */
    public final ImageView primaryActionButtonView;

    /** The view containing call log item actions.  Null until the ViewStub is inflated. */
    public View actionsView;
    /** The button views below are assigned only when the action section is expanded. */
    public VoicemailPlaybackLayout voicemailPlaybackView;
    public View callButtonView;
    public View videoCallButtonView;
    public View createNewContactButtonView;
    public View addToExistingContactButtonView;
    public View sendMessageView;
    public View detailsButtonView;

    /**
     * The row Id for the first call associated with the call log entry.  Used as a key for the
     * map used to track which call log entries have the action button section expanded.
     */
    public long rowId;

    /**
     * The call Ids for the calls represented by the current call log entry.  Used when the user
     * deletes a call log entry.
     */
    public long[] callIds;

    /**
     * The callable phone number for the current call log entry.  Cached here as the call back
     * intent is set only when the actions ViewStub is inflated.
     */
    public String number;

    /**
     * The phone number presentation for the current call log entry.  Cached here as the call back
     * intent is set only when the actions ViewStub is inflated.
     */
    public int numberPresentation;

    /**
     * The type of call for the current call log entry.  Cached here as the call back
     * intent is set only when the actions ViewStub is inflated.
     */
    public int callType;

    /**
     * The account for the current call log entry.  Cached here as the call back
     * intent is set only when the actions ViewStub is inflated.
     */
    public PhoneAccountHandle accountHandle;

    /**
     * If the call has an associated voicemail message, the URI of the voicemail message for
     * playback.  Cached here as the voicemail intent is only set when the actions ViewStub is
     * inflated.
     */
    public String voicemailUri;

    /**
     * The name or number associated with the call.  Cached here for use when setting content
     * descriptions on buttons in the actions ViewStub when it is inflated.
     */
    public CharSequence nameOrNumber;

    /**
     * The contact info for the contact displayed in this list item.
     */
    public ContactInfo info;

    private static final int VOICEMAIL_TRANSCRIPTION_MAX_LINES = 10;

    private final Context mContext;
    private final TelecomCallLogCache mTelecomCallLogCache;
    private final CallLogListItemHelper mCallLogListItemHelper;
    private final VoicemailPlaybackPresenter mVoicemailPlaybackPresenter;

    private final int mPhotoSize;

    private View.OnClickListener mExpandCollapseListener;
    private boolean mVoicemailPrimaryActionButtonClicked;

    private CallLogListItemViewHolder(
            Context context,
            View.OnClickListener expandCollapseListener,
            TelecomCallLogCache telecomCallLogCache,
            CallLogListItemHelper callLogListItemHelper,
            VoicemailPlaybackPresenter voicemailPlaybackPresenter,
            View rootView,
            QuickContactBadge quickContactView,
            View primaryActionView,
            PhoneCallDetailsViews phoneCallDetailsViews,
            CardView callLogEntryView,
            TextView dayGroupHeader,
            ImageView primaryActionButtonView) {
        super(rootView);

        mContext = context;
        mExpandCollapseListener = expandCollapseListener;
        mTelecomCallLogCache = telecomCallLogCache;
        mCallLogListItemHelper = callLogListItemHelper;
        mVoicemailPlaybackPresenter = voicemailPlaybackPresenter;

        this.rootView = rootView;
        this.quickContactView = quickContactView;
        this.primaryActionView = primaryActionView;
        this.phoneCallDetailsViews = phoneCallDetailsViews;
        this.callLogEntryView = callLogEntryView;
        this.dayGroupHeader = dayGroupHeader;
        this.primaryActionButtonView = primaryActionButtonView;

        Resources resources = mContext.getResources();
        mPhotoSize = mContext.getResources().getDimensionPixelSize(R.dimen.contact_photo_size);

        // Set text height to false on the TextViews so they don't have extra padding.
        phoneCallDetailsViews.nameView.setElegantTextHeight(false);
        phoneCallDetailsViews.callLocationAndDate.setElegantTextHeight(false);

        quickContactView.setPrioritizedMimeType(Phone.CONTENT_ITEM_TYPE);

        primaryActionButtonView.setOnClickListener(this);
        primaryActionView.setOnClickListener(mExpandCollapseListener);
    }

    public static CallLogListItemViewHolder create(
            View view,
            Context context,
            View.OnClickListener expandCollapseListener,
            TelecomCallLogCache telecomCallLogCache,
            CallLogListItemHelper callLogListItemHelper,
            VoicemailPlaybackPresenter voicemailPlaybackPresenter) {

        return new CallLogListItemViewHolder(
                context,
                expandCollapseListener,
                telecomCallLogCache,
                callLogListItemHelper,
                voicemailPlaybackPresenter,
                view,
                (QuickContactBadge) view.findViewById(R.id.quick_contact_photo),
                view.findViewById(R.id.primary_action_view),
                PhoneCallDetailsViews.fromView(view),
                (CardView) view.findViewById(R.id.call_log_row),
                (TextView) view.findViewById(R.id.call_log_day_group_label),
                (ImageView) view.findViewById(R.id.primary_action_button));
    }

    /**
     * Configures the action buttons in the expandable actions ViewStub. The ViewStub is not
     * inflated during initial binding, so click handlers, tags and accessibility text must be set
     * here, if necessary.
     *
     * @param callLogItem The call log list item view.
     */
    public void inflateActionViewStub() {
        ViewStub stub = (ViewStub) rootView.findViewById(R.id.call_log_entry_actions_stub);
        if (stub != null) {
            actionsView = (ViewGroup) stub.inflate();

            voicemailPlaybackView = (VoicemailPlaybackLayout) actionsView
                    .findViewById(R.id.voicemail_playback_layout);

            callButtonView = actionsView.findViewById(R.id.call_action);
            callButtonView.setOnClickListener(this);

            videoCallButtonView = actionsView.findViewById(R.id.video_call_action);
            videoCallButtonView.setOnClickListener(this);

            createNewContactButtonView = actionsView.findViewById(R.id.create_new_contact_action);
            createNewContactButtonView.setOnClickListener(this);

            addToExistingContactButtonView =
                    actionsView.findViewById(R.id.add_to_existing_contact_action);
            addToExistingContactButtonView.setOnClickListener(this);

            sendMessageView = actionsView.findViewById(R.id.send_message_action);
            sendMessageView.setOnClickListener(this);

            detailsButtonView = actionsView.findViewById(R.id.details_action);
            detailsButtonView.setOnClickListener(this);
        }

        bindActionButtons();
    }

    private void updatePrimaryActionButton(boolean isExpanded) {
        if (!TextUtils.isEmpty(voicemailUri)) {
            // Treat as voicemail list item; show play button if not expanded.
            if (!isExpanded) {
                primaryActionButtonView.setImageResource(R.drawable.ic_play_arrow_24dp);
                primaryActionButtonView.setVisibility(View.VISIBLE);
            } else {
                primaryActionButtonView.setVisibility(View.GONE);
            }
        } else {
            // Treat as normal list item; show call button, if possible.
            boolean canPlaceCallToNumber =
                    PhoneNumberUtil.canPlaceCallsTo(number, numberPresentation);

            if (canPlaceCallToNumber) {
                boolean isVoicemailNumber =
                        mTelecomCallLogCache.isVoicemailNumber(accountHandle, number);
                if (isVoicemailNumber) {
                    // Call to generic voicemail number, in case there are multiple accounts.
                    primaryActionButtonView.setTag(
                            IntentProvider.getReturnVoicemailCallIntentProvider());
                } else {
                    primaryActionButtonView.setTag(
                            IntentProvider.getReturnCallIntentProvider(number));
                }

                primaryActionButtonView.setContentDescription(TextUtils.expandTemplate(
                        mContext.getString(R.string.description_call_action),
                        nameOrNumber));
                primaryActionButtonView.setImageResource(R.drawable.ic_call_24dp);
                primaryActionButtonView.setVisibility(View.VISIBLE);
            } else {
                primaryActionButtonView.setTag(null);
                primaryActionButtonView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Binds text titles, click handlers and intents to the voicemail, details and callback action
     * buttons.
     */
    private void bindActionButtons() {
        boolean canPlaceCallToNumber = PhoneNumberUtil.canPlaceCallsTo(number, numberPresentation);

        if (!TextUtils.isEmpty(voicemailUri) && canPlaceCallToNumber) {
            callButtonView.setTag(IntentProvider.getReturnCallIntentProvider(number));
            ((TextView) callButtonView.findViewById(R.id.call_action_text))
                    .setText(TextUtils.expandTemplate(
                            mContext.getString(R.string.call_log_action_call),
                            nameOrNumber));
            callButtonView.setVisibility(View.VISIBLE);
        } else {
            callButtonView.setVisibility(View.GONE);
        }

        // If one of the calls had video capabilities, show the video call button.
        if (mTelecomCallLogCache.isVideoEnabled() && canPlaceCallToNumber &&
                phoneCallDetailsViews.callTypeIcons.isVideoShown()) {
            videoCallButtonView.setTag(IntentProvider.getReturnVideoCallIntentProvider(number));
            videoCallButtonView.setVisibility(View.VISIBLE);
        } else {
            videoCallButtonView.setVisibility(View.GONE);
        }

        // For voicemail calls, show the voicemail playback layout; hide otherwise.
        if (callType == Calls.VOICEMAIL_TYPE && mVoicemailPlaybackPresenter != null) {
            voicemailPlaybackView.setVisibility(View.VISIBLE);

            Uri uri = Uri.parse(voicemailUri);
            mVoicemailPlaybackPresenter.setPlaybackView(
                    voicemailPlaybackView, uri, mVoicemailPrimaryActionButtonClicked);
            mVoicemailPrimaryActionButtonClicked = false;

            CallLogAsyncTaskUtil.markVoicemailAsRead(mContext, uri);
        } else {
            voicemailPlaybackView.setVisibility(View.GONE);
        }

        detailsButtonView.setVisibility(View.VISIBLE);
        detailsButtonView.setTag(
                IntentProvider.getCallDetailIntentProvider(rowId, callIds, null));

        if (info != null && UriUtils.isEncodedContactUri(info.lookupUri)) {
            createNewContactButtonView.setTag(IntentProvider.getAddContactIntentProvider(
                    info.lookupUri, info.name, info.number, info.type, true /* isNewContact */));
            createNewContactButtonView.setVisibility(View.VISIBLE);

            addToExistingContactButtonView.setTag(IntentProvider.getAddContactIntentProvider(
                    info.lookupUri, info.name, info.number, info.type, false /* isNewContact */));
            addToExistingContactButtonView.setVisibility(View.VISIBLE);
        } else {
            createNewContactButtonView.setVisibility(View.GONE);
            addToExistingContactButtonView.setVisibility(View.GONE);
        }

        sendMessageView.setTag(IntentProvider.getSendSmsIntentProvider(number));

        mCallLogListItemHelper.setActionContentDescriptions(this);
    }

    /**
     * Show or hide the action views, such as voicemail, details, and add contact.
     *
     * If the action views have never been shown yet for this view, inflate the view stub.
     */
    public void showActions(boolean show) {
        expandVoicemailTranscriptionView(show);

        if (show) {
            // Inflate the view stub if necessary, and wire up the event handlers.
            inflateActionViewStub();

            actionsView.setVisibility(View.VISIBLE);
            actionsView.setAlpha(1.0f);
        } else {
            // When recycling a view, it is possible the actionsView ViewStub was previously
            // inflated so we should hide it in this case.
            if (actionsView != null) {
                actionsView.setVisibility(View.GONE);
            }
        }

        updatePrimaryActionButton(show);
    }

    public void expandVoicemailTranscriptionView(boolean isExpanded) {
        if (callType != Calls.VOICEMAIL_TYPE) {
            return;
        }

        final TextView view = phoneCallDetailsViews.voicemailTranscriptionView;
        if (TextUtils.isEmpty(view.getText())) {
            return;
        }
        view.setMaxLines(isExpanded ? VOICEMAIL_TRANSCRIPTION_MAX_LINES : 1);
        view.setSingleLine(!isExpanded);
    }

    public void setPhoto(long photoId, Uri photoUri, Uri contactUri, String displayName,
            boolean isVoicemail, boolean isBusiness) {
        quickContactView.assignContactUri(contactUri);
        quickContactView.setOverlay(null);

        int contactType = ContactPhotoManager.TYPE_DEFAULT;
        if (isVoicemail) {
            contactType = ContactPhotoManager.TYPE_VOICEMAIL;
        } else if (isBusiness) {
            contactType = ContactPhotoManager.TYPE_BUSINESS;
        }

        String lookupKey = null;
        if (contactUri != null) {
            lookupKey = ContactInfoHelper.getLookupKeyFromUri(contactUri);
        }

        DefaultImageRequest request = new DefaultImageRequest(
                displayName, lookupKey, contactType, true /* isCircular */);

        if (photoId == 0 && photoUri != null) {
            ContactPhotoManager.getInstance(mContext).loadPhoto(quickContactView, photoUri,
                    mPhotoSize, false /* darkTheme */, true /* isCircular */, request);
        } else {
            ContactPhotoManager.getInstance(mContext).loadThumbnail(quickContactView, photoId,
                    false /* darkTheme */, true /* isCircular */, request);
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.primary_action_button && !TextUtils.isEmpty(voicemailUri)) {
            mVoicemailPrimaryActionButtonClicked = true;
            mExpandCollapseListener.onClick(primaryActionView);
        } else {
            final IntentProvider intentProvider = (IntentProvider) view.getTag();
            if (intentProvider != null) {
                final Intent intent = intentProvider.getIntent(mContext);
                // See IntentProvider.getCallDetailIntentProvider() for why this may be null.
                if (intent != null) {
                    DialerUtils.startActivityWithErrorToast(mContext, intent);
                }
            }
        }
    }

    @NeededForTesting
    public static CallLogListItemViewHolder createForTest(Context context) {
        Resources resources = context.getResources();
        TelecomCallLogCache telecomCallLogCache = new TelecomCallLogCache(context);
        PhoneCallDetailsHelper phoneCallDetailsHelper = new PhoneCallDetailsHelper(
                context, resources, telecomCallLogCache);

        CallLogListItemViewHolder viewHolder = new CallLogListItemViewHolder(
                context,
                null /* expandCollapseListener */,
                telecomCallLogCache,
                new CallLogListItemHelper(phoneCallDetailsHelper, resources, telecomCallLogCache),
                null /* voicemailPlaybackPresenter */,
                new View(context),
                new QuickContactBadge(context),
                new View(context),
                PhoneCallDetailsViews.createForTest(context),
                new CardView(context),
                new TextView(context),
                new ImageView(context));
        viewHolder.detailsButtonView = new TextView(context);
        viewHolder.actionsView = new View(context);
        viewHolder.voicemailPlaybackView = new VoicemailPlaybackLayout(context);

        return viewHolder;
    }
}
