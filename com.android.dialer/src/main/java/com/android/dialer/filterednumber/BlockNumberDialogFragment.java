/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.dialer.filterednumber;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.dialer.R;
import com.android.dialer.compat.FilteredNumberCompat;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler.OnBlockNumberListener;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler.OnUnblockNumberListener;
import com.android.dialer.voicemail.VisualVoicemailEnabledChecker;
import com.android.dialer.logging.InteractionEvent;
import com.android.dialer.logging.Logger;

/**
 * Fragment for confirming and enacting blocking/unblocking a number. Also invokes snackbar
 * providing undo functionality.
 */
public class BlockNumberDialogFragment extends DialogFragment {

    /**
     * Use a callback interface to update UI after success/undo. Favor this approach over other
     * more standard paradigms because of the variety of scenarios in which the DialogFragment
     * can be invoked (by an Activity, by a fragment, by an adapter, by an adapter list item).
     * Because of this, we do NOT support retaining state on rotation, and will dismiss the dialog
     * upon rotation instead.
     */
    public interface Callback {
        /**
         * Called when a number is successfully added to the set of filtered numbers
         */
        void onFilterNumberSuccess();

        /**
         * Called when a number is successfully removed from the set of filtered numbers
         */
        void onUnfilterNumberSuccess();

        /**
         * Called when the action of filtering or unfiltering a number is undone
         */
        void onChangeFilteredNumberUndo();
    }

    private static final String BLOCK_DIALOG_FRAGMENT = "BlockNumberDialog";

    private static final String ARG_BLOCK_ID = "argBlockId";
    private static final String ARG_NUMBER = "argNumber";
    private static final String ARG_COUNTRY_ISO = "argCountryIso";
    private static final String ARG_DISPLAY_NUMBER = "argDisplayNumber";
    private static final String ARG_PARENT_VIEW_ID = "parentViewId";

    private String mNumber;
    private String mDisplayNumber;
    private String mCountryIso;

    private FilteredNumberAsyncQueryHandler mHandler;
    private View mParentView;
    private VisualVoicemailEnabledChecker mVoicemailEnabledChecker;
    private Callback mCallback;

    public static void show(
            Integer blockId,
            String number,
            String countryIso,
            String displayNumber,
            Integer parentViewId,
            FragmentManager fragmentManager,
            Callback callback) {
        final BlockNumberDialogFragment newFragment = BlockNumberDialogFragment.newInstance(
                blockId, number, countryIso, displayNumber, parentViewId);

        newFragment.setCallback(callback);
        newFragment.show(fragmentManager, BlockNumberDialogFragment.BLOCK_DIALOG_FRAGMENT);
    }

    private static BlockNumberDialogFragment newInstance(
            Integer blockId,
            String number,
            String countryIso,
            String displayNumber,
            Integer parentViewId) {
        final BlockNumberDialogFragment fragment = new BlockNumberDialogFragment();
        final Bundle args = new Bundle();
        if (blockId != null) {
            args.putInt(ARG_BLOCK_ID, blockId.intValue());
        }
        if (parentViewId != null) {
            args.putInt(ARG_PARENT_VIEW_ID, parentViewId.intValue());
        }
        args.putString(ARG_NUMBER, number);
        args.putString(ARG_COUNTRY_ISO, countryIso);
        args.putString(ARG_DISPLAY_NUMBER, displayNumber);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public Context getContext() {
        return getActivity();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        final boolean isBlocked = getArguments().containsKey(ARG_BLOCK_ID);

        mNumber = getArguments().getString(ARG_NUMBER);
        mDisplayNumber = getArguments().getString(ARG_DISPLAY_NUMBER);
        mCountryIso = getArguments().getString(ARG_COUNTRY_ISO);

        if (TextUtils.isEmpty(mDisplayNumber)) {
            mDisplayNumber = mNumber;
        }

        mHandler = new FilteredNumberAsyncQueryHandler(getContext().getContentResolver());
        mVoicemailEnabledChecker = new VisualVoicemailEnabledChecker(getActivity(), null);
      	/**
         * Choose not to update VoicemailEnabledChecker, as checks should already been done in
         * all current use cases.
         */
        mParentView = getActivity().findViewById(getArguments().getInt(ARG_PARENT_VIEW_ID));

        CharSequence title;
        String okText;
        String message;
        if (isBlocked) {
            title = null;
            okText = getString(R.string.unblock_number_ok);
            message = ContactDisplayUtils.getTtsSpannedPhoneNumber(getResources(),
                    R.string.unblock_number_confirmation_title,
                    mDisplayNumber).toString();
        } else {
            title = ContactDisplayUtils.getTtsSpannedPhoneNumber(getResources(),
                    R.string.block_number_confirmation_title,
                    mDisplayNumber);
            okText = getString(R.string.block_number_ok);
            if (FilteredNumberCompat.useNewFiltering()) {
                message = getString(R.string.block_number_confirmation_message_new_filtering);
            } else if (mVoicemailEnabledChecker.isVisualVoicemailEnabled()) {
                message = getString(R.string.block_number_confirmation_message_vvm);
            } else {
                message = getString(R.string.block_number_confirmation_message_no_vvm);
            }
        }


        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(okText, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if (isBlocked) {
                            unblockNumber();
                        } else {
                            blockNumber();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null);
        return builder.create();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (!FilteredNumbersUtil.canBlockNumber(getActivity(), mNumber, mCountryIso)) {
            dismiss();
            Toast.makeText(getContext(),
                    ContactDisplayUtils.getTtsSpannedPhoneNumber(
                            getResources(), R.string.invalidNumber, mDisplayNumber),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPause() {
        // Dismiss on rotation.
        dismiss();
        mCallback = null;

        super.onPause();
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    private CharSequence getBlockedMessage() {
        return ContactDisplayUtils.getTtsSpannedPhoneNumber(getResources(),
                R.string.snackbar_number_blocked, mDisplayNumber);
    }

    private CharSequence getUnblockedMessage() {
        return ContactDisplayUtils.getTtsSpannedPhoneNumber(getResources(),
                R.string.snackbar_number_unblocked, mDisplayNumber);
    }

    private int getActionTextColor() {
        return getContext().getResources().getColor(R.color.dialer_snackbar_action_text_color);
    }

    private void blockNumber() {
        final CharSequence message = getBlockedMessage();
        final CharSequence undoMessage = getUnblockedMessage();
        final Callback callback = mCallback;
        final int actionTextColor = getActionTextColor();
        final Context context = getContext();

        final OnUnblockNumberListener onUndoListener = new OnUnblockNumberListener() {
            @Override
            public void onUnblockComplete(int rows, ContentValues values) {
                Snackbar.make(mParentView, undoMessage, Snackbar.LENGTH_LONG).show();
                if (callback != null) {
                    callback.onChangeFilteredNumberUndo();
                }
            }
        };

        final OnBlockNumberListener onBlockNumberListener = new OnBlockNumberListener() {
            @Override
            public void onBlockComplete(final Uri uri) {
                final View.OnClickListener undoListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // Delete the newly created row on 'undo'.
                        Logger.logInteraction(InteractionEvent.UNDO_BLOCK_NUMBER);
                        mHandler.unblock(onUndoListener, uri);
                    }
                };

                Snackbar.make(mParentView, message, Snackbar.LENGTH_LONG)
                        .setAction(R.string.block_number_undo, undoListener)
                        .setActionTextColor(actionTextColor)
                        .show();

                if (callback != null) {
                    callback.onFilterNumberSuccess();
                }

                if (context != null && FilteredNumbersUtil.hasRecentEmergencyCall(context)) {
                    FilteredNumbersUtil.maybeNotifyCallBlockingDisabled(context);
                }
            }
        };

        mHandler.blockNumber(
                onBlockNumberListener,
                mNumber,
                mCountryIso);
    }

    private void unblockNumber() {
        final CharSequence message = getUnblockedMessage();
        final CharSequence undoMessage = getBlockedMessage();
        final Callback callback = mCallback;
        final int actionTextColor = getActionTextColor();

        final OnBlockNumberListener onUndoListener = new OnBlockNumberListener() {
            @Override
            public void onBlockComplete(final Uri uri) {
                Snackbar.make(mParentView, undoMessage, Snackbar.LENGTH_LONG).show();
                if (callback != null) {
                    callback.onChangeFilteredNumberUndo();
                }
            }
        };

        mHandler.unblock(new OnUnblockNumberListener() {
            @Override
            public void onUnblockComplete(int rows, final ContentValues values) {
                final View.OnClickListener undoListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // Re-insert the row on 'undo', with a new ID.
                        Logger.logInteraction(InteractionEvent.UNDO_UNBLOCK_NUMBER);
                        mHandler.blockNumber(onUndoListener, values);
                    }
                };

                Snackbar.make(mParentView, message, Snackbar.LENGTH_LONG)
                        .setAction(R.string.block_number_undo, undoListener)
                        .setActionTextColor(actionTextColor)
                        .show();

                if (callback != null) {
                    callback.onUnfilterNumberSuccess();
                }
            }
        }, getArguments().getInt(ARG_BLOCK_ID));
    }
}
