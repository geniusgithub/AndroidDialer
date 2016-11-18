/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.filterednumber;

import com.google.common.base.Preconditions;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.os.Bundle;
import android.view.View;
import com.android.dialer.R;
import com.android.dialer.filterednumber.BlockedNumbersMigrator.Listener;

/**
 * Dialog fragment shown to users when they need to migrate to use
 * {@link android.provider.BlockedNumberContract} for blocking.
 */
public class MigrateBlockedNumbersDialogFragment extends DialogFragment {

    private BlockedNumbersMigrator mBlockedNumbersMigrator;
    private BlockedNumbersMigrator.Listener mMigrationListener;

    /**
     * Creates a new MigrateBlockedNumbersDialogFragment.
     *
     * @param blockedNumbersMigrator The {@link BlockedNumbersMigrator} which will be used to
     * migrate the numbers.
     * @param migrationListener The {@link BlockedNumbersMigrator.Listener} to call when the
     * migration is complete.
     * @return The new MigrateBlockedNumbersDialogFragment.
     * @throws NullPointerException if blockedNumbersMigrator or migrationListener are {@code null}.
     */
    public static DialogFragment newInstance(BlockedNumbersMigrator blockedNumbersMigrator,
            BlockedNumbersMigrator.Listener migrationListener) {
        MigrateBlockedNumbersDialogFragment fragment = new MigrateBlockedNumbersDialogFragment();
        fragment.mBlockedNumbersMigrator = Preconditions.checkNotNull(blockedNumbersMigrator);
        fragment.mMigrationListener = Preconditions.checkNotNull(migrationListener);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.migrate_blocked_numbers_dialog_title)
                .setMessage(R.string.migrate_blocked_numbers_dialog_message)
                .setPositiveButton(R.string.migrate_blocked_numbers_dialog_allow_button, null)
                .setNegativeButton(R.string.migrate_blocked_numbers_dialog_cancel_button, null)
                .create();
        // The Dialog's buttons aren't available until show is called, so an OnShowListener
        // is used to set the positive button callback.
        dialog.setOnShowListener(new OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                final AlertDialog alertDialog = (AlertDialog) dialog;
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(newPositiveButtonOnClickListener(alertDialog));
            }
        });
        return dialog;
    }

    /*
     * Creates a new View.OnClickListener to be used as the positive button in this dialog. The
     * OnClickListener will grey out the dialog's positive and negative buttons while the migration
     * is underway, and close the dialog once the migrate is complete.
     */
    private View.OnClickListener newPositiveButtonOnClickListener(final AlertDialog alertDialog) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
                mBlockedNumbersMigrator.migrate(new Listener() {
                    @Override
                    public void onComplete() {
                        alertDialog.dismiss();
                        mMigrationListener.onComplete();
                    }
                });
            }
        };
    }

    @Override
    public void onPause() {
        // The dialog is dismissed and state is cleaned up onPause, i.e. rotation.
        dismiss();
        mBlockedNumbersMigrator = null;
        mMigrationListener = null;
        super.onPause();
    }
}
