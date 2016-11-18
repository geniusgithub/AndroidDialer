/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.dialer.interactions;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.android.contacts.common.Collapser;
import com.android.contacts.common.Collapser.Collapsible;
import com.android.contacts.common.MoreContactUtils;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.dialer.R;
import com.android.dialer.TransactionSafeActivity;
import com.android.dialer.contact.ContactUpdateService;
import com.android.dialer.util.IntentUtil;
import com.android.dialer.util.IntentUtil.CallIntentBuilder;
import com.android.incallui.Call.LogState;
import com.android.dialer.util.DialerUtils;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/**
 * Initiates phone calls or a text message. If there are multiple candidates, this class shows a
 * dialog to pick one. Creating one of these interactions should be done through the static
 * factory methods.
 *
 * Note that this class initiates not only usual *phone* calls but also *SIP* calls.
 *
 * TODO: clean up code and documents since it is quite confusing to use "phone numbers" or
 *        "phone calls" here while they can be SIP addresses or SIP calls (See also issue 5039627).
 */
public class PhoneNumberInteraction implements OnLoadCompleteListener<Cursor> {
    private static final String TAG = PhoneNumberInteraction.class.getSimpleName();

    /**
     * A model object for capturing a phone number for a given contact.
     */
    @VisibleForTesting
    /* package */ static class PhoneItem implements Parcelable, Collapsible<PhoneItem> {
        long id;
        String phoneNumber;
        String accountType;
        String dataSet;
        long type;
        String label;
        /** {@link Phone#CONTENT_ITEM_TYPE} or {@link SipAddress#CONTENT_ITEM_TYPE}. */
        String mimeType;

        public PhoneItem() {
        }

        private PhoneItem(Parcel in) {
            this.id          = in.readLong();
            this.phoneNumber = in.readString();
            this.accountType = in.readString();
            this.dataSet     = in.readString();
            this.type        = in.readLong();
            this.label       = in.readString();
            this.mimeType    = in.readString();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(id);
            dest.writeString(phoneNumber);
            dest.writeString(accountType);
            dest.writeString(dataSet);
            dest.writeLong(type);
            dest.writeString(label);
            dest.writeString(mimeType);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void collapseWith(PhoneItem phoneItem) {
            // Just keep the number and id we already have.
        }

        @Override
        public boolean shouldCollapseWith(PhoneItem phoneItem, Context context) {
            return MoreContactUtils.shouldCollapse(Phone.CONTENT_ITEM_TYPE, phoneNumber,
                    Phone.CONTENT_ITEM_TYPE, phoneItem.phoneNumber);
        }

        @Override
        public String toString() {
            return phoneNumber;
        }

        public static final Parcelable.Creator<PhoneItem> CREATOR
                = new Parcelable.Creator<PhoneItem>() {
            @Override
            public PhoneItem createFromParcel(Parcel in) {
                return new PhoneItem(in);
            }

            @Override
            public PhoneItem[] newArray(int size) {
                return new PhoneItem[size];
            }
        };
    }

    /**
     * A list adapter that populates the list of contact's phone numbers.
     */
    private static class PhoneItemAdapter extends ArrayAdapter<PhoneItem> {
        private final int mInteractionType;

        public PhoneItemAdapter(Context context, List<PhoneItem> list,
                int interactionType) {
            super(context, R.layout.phone_disambig_item, android.R.id.text2, list);
            mInteractionType = interactionType;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View view = super.getView(position, convertView, parent);

            final PhoneItem item = getItem(position);
            final TextView typeView = (TextView) view.findViewById(android.R.id.text1);
            CharSequence value = ContactDisplayUtils.getLabelForCallOrSms((int) item.type,
                    item.label, mInteractionType, getContext());

            typeView.setText(value);
            return view;
        }
    }

    /**
     * {@link DialogFragment} used for displaying a dialog with a list of phone numbers of which
     * one will be chosen to make a call or initiate an sms message.
     *
     * It is recommended to use
     * {@link PhoneNumberInteraction#startInteractionForPhoneCall(TransactionSafeActivity, Uri)} or
     * {@link PhoneNumberInteraction#startInteractionForTextMessage(TransactionSafeActivity, Uri)}
     * instead of directly using this class, as those methods handle one or multiple data cases
     * appropriately.
     */
    /* Made public to let the system reach this class */
    public static class PhoneDisambiguationDialogFragment extends DialogFragment
            implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {

        private static final String ARG_PHONE_LIST = "phoneList";
        private static final String ARG_INTERACTION_TYPE = "interactionType";
        private static final String ARG_CALL_INITIATION_TYPE = "callInitiation";
        private static final String ARG_IS_VIDEO_CALL = "is_video_call";

        private int mInteractionType;
        private ListAdapter mPhonesAdapter;
        private List<PhoneItem> mPhoneList;
        private int mCallInitiationType;
        private boolean mIsVideoCall;

        public static void show(FragmentManager fragmentManager, ArrayList<PhoneItem> phoneList,
                int interactionType, boolean isVideoCall, int callInitiationType) {
            PhoneDisambiguationDialogFragment fragment = new PhoneDisambiguationDialogFragment();
            Bundle bundle = new Bundle();
            bundle.putParcelableArrayList(ARG_PHONE_LIST, phoneList);
            bundle.putInt(ARG_INTERACTION_TYPE, interactionType);
            bundle.putInt(ARG_CALL_INITIATION_TYPE, callInitiationType);
            bundle.putBoolean(ARG_IS_VIDEO_CALL, isVideoCall);
            fragment.setArguments(bundle);
            fragment.show(fragmentManager, TAG);
        }

        public PhoneDisambiguationDialogFragment() {
            super();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            mPhoneList = getArguments().getParcelableArrayList(ARG_PHONE_LIST);
            mInteractionType = getArguments().getInt(ARG_INTERACTION_TYPE);
            mCallInitiationType = getArguments().getInt(ARG_CALL_INITIATION_TYPE);
            mIsVideoCall = getArguments().getBoolean(ARG_IS_VIDEO_CALL);

            mPhonesAdapter = new PhoneItemAdapter(activity, mPhoneList, mInteractionType);
            final LayoutInflater inflater = activity.getLayoutInflater();
            final View setPrimaryView = inflater.inflate(R.layout.set_primary_checkbox, null);
            return new AlertDialog.Builder(activity)
                    .setAdapter(mPhonesAdapter, this)
                    .setTitle(mInteractionType == ContactDisplayUtils.INTERACTION_SMS
                            ? R.string.sms_disambig_title : R.string.call_disambig_title)
                    .setView(setPrimaryView)
                    .create();
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            final Activity activity = getActivity();
            if (activity == null) return;
            final AlertDialog alertDialog = (AlertDialog)dialog;
            if (mPhoneList.size() > which && which >= 0) {
                final PhoneItem phoneItem = mPhoneList.get(which);
                final CheckBox checkBox = (CheckBox)alertDialog.findViewById(R.id.setPrimary);
                if (checkBox.isChecked()) {
                    // Request to mark the data as primary in the background.
                    final Intent serviceIntent = ContactUpdateService.createSetSuperPrimaryIntent(
                            activity, phoneItem.id);
                    activity.startService(serviceIntent);
                }

                PhoneNumberInteraction.performAction(activity, phoneItem.phoneNumber,
                        mInteractionType, mIsVideoCall, mCallInitiationType);
            } else {
                dialog.dismiss();
            }
        }
    }

    private static final String[] PHONE_NUMBER_PROJECTION = new String[] {
            Phone._ID,                      // 0
            Phone.NUMBER,                   // 1
            Phone.IS_SUPER_PRIMARY,         // 2
            RawContacts.ACCOUNT_TYPE,       // 3
            RawContacts.DATA_SET,           // 4
            Phone.TYPE,                     // 5
            Phone.LABEL,                    // 6
            Phone.MIMETYPE,                 // 7
            Phone.CONTACT_ID                // 8
    };

    private static final int _ID = 0;
    private static final int NUMBER = 1;
    private static final int IS_SUPER_PRIMARY = 2;
    private static final int ACCOUNT_TYPE = 3;
    private static final int DATA_SET = 4;
    private static final int TYPE = 5;
    private static final int LABEL = 6;
    private static final int MIMETYPE = 7;
    private static final int CONTACT_ID = 8;

    private static final String PHONE_NUMBER_SELECTION =
            Data.MIMETYPE + " IN ('"
                + Phone.CONTENT_ITEM_TYPE + "', "
                + "'" + SipAddress.CONTENT_ITEM_TYPE + "') AND "
                + Data.DATA1 + " NOT NULL";

    private final Context mContext;
    private final OnDismissListener mDismissListener;
    private final int mInteractionType;

    private final int mCallInitiationType;
    private boolean mUseDefault;

    private static final int UNKNOWN_CONTACT_ID = -1;
    private long mContactId = UNKNOWN_CONTACT_ID;

    private CursorLoader mLoader;
    private boolean mIsVideoCall;

    /**
     * Constructs a new {@link PhoneNumberInteraction}. The constructor takes in a {@link Context}
     * instead of a {@link TransactionSafeActivity} for testing purposes to verify the functionality
     * of this class. However, all factory methods for creating {@link PhoneNumberInteraction}s
     * require a {@link TransactionSafeActivity} (i.e. see {@link #startInteractionForPhoneCall}).
     */
    @VisibleForTesting
    /* package */ PhoneNumberInteraction(Context context, int interactionType,
            DialogInterface.OnDismissListener dismissListener) {
        this(context, interactionType, dismissListener, false /*isVideoCall*/,
                LogState.INITIATION_UNKNOWN);
    }

    private PhoneNumberInteraction(Context context, int interactionType,
            DialogInterface.OnDismissListener dismissListener, boolean isVideoCall, 
            int callInitiationType) {
        mContext = context;
        mInteractionType = interactionType;
        mDismissListener = dismissListener;
        mCallInitiationType = callInitiationType;
        mIsVideoCall = isVideoCall;
    }

    private void performAction(String phoneNumber) {
        PhoneNumberInteraction.performAction(mContext, phoneNumber, mInteractionType, mIsVideoCall,
                mCallInitiationType);
    }

    private static void performAction(
            Context context, String phoneNumber, int interactionType,
            boolean isVideoCall, int callInitiationType) {
        Intent intent;
        switch (interactionType) {
            case ContactDisplayUtils.INTERACTION_SMS:
                intent = new Intent(
                        Intent.ACTION_SENDTO, Uri.fromParts("sms", phoneNumber, null));
                break;
            default:
                intent = new CallIntentBuilder(phoneNumber)
                        .setCallInitiationType(callInitiationType)
                        .setIsVideoCall(isVideoCall)
                        .build();
                break;
        }
        DialerUtils.startActivityWithErrorToast(context, intent);
    }

    /**
     * Initiates the interaction. This may result in a phone call or sms message started
     * or a disambiguation dialog to determine which phone number should be used. If there
     * is a primary phone number, it will be automatically used and a disambiguation dialog
     * will no be shown.
     */
    @VisibleForTesting
    /* package */ void startInteraction(Uri uri) {
        startInteraction(uri, true);
    }

    /**
     * Initiates the interaction to result in either a phone call or sms message for a contact.
     * @param uri Contact Uri
     * @param useDefault Whether or not to use the primary(default) phone number. If true, the
     * primary phone number will always be used by default if one is available. If false, a
     * disambiguation dialog will be shown regardless of whether or not a primary phone number
     * is available.
     */
    @VisibleForTesting
    /* package */ void startInteraction(Uri uri, boolean useDefault) {
        if (mLoader != null) {
            mLoader.reset();
        }
        mUseDefault = useDefault;
        final Uri queryUri;
        final String inputUriAsString = uri.toString();
        if (inputUriAsString.startsWith(Contacts.CONTENT_URI.toString())) {
            if (!inputUriAsString.endsWith(Contacts.Data.CONTENT_DIRECTORY)) {
                queryUri = Uri.withAppendedPath(uri, Contacts.Data.CONTENT_DIRECTORY);
            } else {
                queryUri = uri;
            }
        } else if (inputUriAsString.startsWith(Data.CONTENT_URI.toString())) {
            queryUri = uri;
        } else {
            throw new UnsupportedOperationException(
                    "Input Uri must be contact Uri or data Uri (input: \"" + uri + "\")");
        }

        mLoader = new CursorLoader(mContext,
                queryUri,
                PHONE_NUMBER_PROJECTION,
                PHONE_NUMBER_SELECTION,
                null,
                null);
        mLoader.registerListener(0, this);
        mLoader.startLoading();
    }

    @Override
    public void onLoadComplete(Loader<Cursor> loader, Cursor cursor) {
        if (cursor == null) {
            onDismiss();
            return;
        }
        try {
            ArrayList<PhoneItem> phoneList = new ArrayList<PhoneItem>();
            String primaryPhone = null;
            if (!isSafeToCommitTransactions()) {
                onDismiss();
                return;
            }
            while (cursor.moveToNext()) {
                if (mContactId == UNKNOWN_CONTACT_ID) {
                    mContactId = cursor.getLong(CONTACT_ID);
                }

                if (mUseDefault && cursor.getInt(IS_SUPER_PRIMARY) != 0) {
                    // Found super primary, call it.
                    primaryPhone = cursor.getString(NUMBER);
                }

                PhoneItem item = new PhoneItem();
                item.id = cursor.getLong(_ID);
                item.phoneNumber = cursor.getString(NUMBER);
                item.accountType = cursor.getString(ACCOUNT_TYPE);
                item.dataSet = cursor.getString(DATA_SET);
                item.type = cursor.getInt(TYPE);
                item.label = cursor.getString(LABEL);
                item.mimeType = cursor.getString(MIMETYPE);

                phoneList.add(item);
            }

            if (mUseDefault && primaryPhone != null) {
                performAction(primaryPhone);
                onDismiss();
                return;
            }

            Collapser.collapseList(phoneList, mContext);
            if (phoneList.size() == 0) {
                onDismiss();
            } else if (phoneList.size() == 1) {
                PhoneItem item = phoneList.get(0);
                onDismiss();
                performAction(item.phoneNumber);
            } else {
                // There are multiple candidates. Let the user choose one.
                showDisambiguationDialog(phoneList);
            }
        } finally {
            cursor.close();
        }
    }

    private boolean isSafeToCommitTransactions() {
        return mContext instanceof TransactionSafeActivity ?
                ((TransactionSafeActivity) mContext).isSafeToCommitTransactions() : true;
    }

    private void onDismiss() {
        if (mDismissListener != null) {
            mDismissListener.onDismiss(null);
        }
    }

    /**
     * @param activity that is calling this interaction. This must be of type
     * {@link TransactionSafeActivity} because we need to check on the activity state after the
     * phone numbers have been queried for.
     * @param isVideoCall {@code true} if the call is a video call, {@code false} otherwise.
     * @param callInitiationType Indicates the UI affordance that was used to initiate the call.
     */
    public static void startInteractionForPhoneCall(TransactionSafeActivity activity, Uri uri,
            boolean isVideoCall, int callInitiationType) {
        (new PhoneNumberInteraction(activity, ContactDisplayUtils.INTERACTION_CALL, null,
                isVideoCall, callInitiationType)).startInteraction(uri, true);
    }

    /**
     * Start text messaging (a.k.a SMS) action using given contact Uri. If there are multiple
     * candidates for the phone call, dialog is automatically shown and the user is asked to choose
     * one.
     *
     * @param activity that is calling this interaction. This must be of type
     * {@link TransactionSafeActivity} because we need to check on the activity state after the
     * phone numbers have been queried for.
     * @param uri contact Uri (built from {@link Contacts#CONTENT_URI}) or data Uri
     * (built from {@link Data#CONTENT_URI}). Contact Uri may show the disambiguation dialog while
     * data Uri won't.
     */
    public static void startInteractionForTextMessage(TransactionSafeActivity activity, Uri uri) {
        (new PhoneNumberInteraction(activity, ContactDisplayUtils.INTERACTION_SMS, null))
                .startInteraction(uri, true);
    }

    @VisibleForTesting
    /* package */ CursorLoader getLoader() {
        return mLoader;
    }

    @VisibleForTesting
    /* package */ void showDisambiguationDialog(ArrayList<PhoneItem> phoneList) {
        final Activity activity = (Activity) mContext;
        if (activity.isDestroyed()) {
            // Check whether the activity is still running
            return;
        }
        try {
            PhoneDisambiguationDialogFragment.show(activity.getFragmentManager(),
                    phoneList, mInteractionType, mIsVideoCall, mCallInitiationType);
        } catch (IllegalStateException e) {
            // ignore to be safe. Shouldn't happen because we checked the
            // activity wasn't destroyed, but to be safe.
        }
    }
}
