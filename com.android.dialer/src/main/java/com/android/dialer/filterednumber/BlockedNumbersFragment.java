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
 * limitations under the License.
 */
package com.android.dialer.filterednumber;

import com.google.common.base.MoreObjects;

import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.common.lettertiles.LetterTileDrawable;
import com.android.contacts.common.testing.NeededForTesting;
import com.android.dialer.R;
import com.android.dialer.compat.FilteredNumberCompat;
import com.android.dialer.database.FilteredNumberContract;
import com.android.dialer.filterednumber.BlockedNumbersMigrator.Listener;
import com.android.dialer.filterednumber.FilteredNumbersUtil.CheckForSendToVoicemailContactListener;
import com.android.dialer.filterednumber.FilteredNumbersUtil.ImportSendToVoicemailContactsListener;
import com.android.dialer.voicemail.VisualVoicemailEnabledChecker;

public class BlockedNumbersFragment extends ListFragment
        implements LoaderManager.LoaderCallbacks<Cursor>, View.OnClickListener,
        VisualVoicemailEnabledChecker.Callback {
    private static final char ADD_BLOCKED_NUMBER_ICON_LETTER = '+';

    private BlockedNumbersMigrator blockedNumbersMigratorForTest;
    protected View migratePromoView;
    private TextView blockedNumbersText;
    private TextView footerText;
    private BlockedNumbersAdapter mAdapter;
    private VisualVoicemailEnabledChecker mVoicemailEnabledChecker;
    private View mImportSettings;
    private View mBlockedNumbersDisabledForEmergency;
    private View mBlockedNumberListDivider;

    @Override
    public Context getContext() {
        return getActivity();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        LayoutInflater inflater =
                (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        getListView().addHeaderView(inflater.inflate(R.layout.blocked_number_header, null));
        getListView().addFooterView(inflater.inflate(R.layout.blocked_number_footer, null));
        //replace the icon for add number with LetterTileDrawable(), so it will have identical style
        ImageView addNumberIcon = (ImageView) getActivity().findViewById(R.id.add_number_icon);
        LetterTileDrawable drawable = new LetterTileDrawable(getResources());
        drawable.setLetter(ADD_BLOCKED_NUMBER_ICON_LETTER);
        drawable.setColor(ActivityCompat.getColor(getActivity(),
                R.color.add_blocked_number_icon_color));
        drawable.setIsCircular(true);
        addNumberIcon.setImageDrawable(drawable);

        if (mAdapter == null) {
            mAdapter = BlockedNumbersAdapter.newBlockedNumbersAdapter(
                    getContext(), getActivity().getFragmentManager());
        }
        setListAdapter(mAdapter);

        blockedNumbersText = (TextView) getListView().findViewById(R.id.blocked_number_text_view);
        migratePromoView = getListView().findViewById(R.id.migrate_promo);
        getListView().findViewById(R.id.migrate_promo_allow_button).setOnClickListener(this);
        mImportSettings = getListView().findViewById(R.id.import_settings);
        mBlockedNumbersDisabledForEmergency =
                getListView().findViewById(R.id.blocked_numbers_disabled_for_emergency);
        mBlockedNumberListDivider = getActivity().findViewById(R.id.blocked_number_list_divider);
        getListView().findViewById(R.id.import_button).setOnClickListener(this);
        getListView().findViewById(R.id.view_numbers_button).setOnClickListener(this);
        getListView().findViewById(R.id.add_number_linear_layout).setOnClickListener(this);

        footerText = (TextView) getActivity().findViewById(
            R.id.blocked_number_footer_textview);
        mVoicemailEnabledChecker = new VisualVoicemailEnabledChecker(getContext(),this);
        mVoicemailEnabledChecker.asyncUpdate();
        updateActiveVoicemailProvider();
    }

    @Override
    public void onDestroy() {
        setListAdapter(null);
        super.onDestroy();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onResume() {
        super.onResume();

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        ColorDrawable backgroundDrawable = new ColorDrawable(
                ActivityCompat.getColor(getActivity(), R.color.dialer_theme_color));
        actionBar.setBackgroundDrawable(backgroundDrawable);
        actionBar.setDisplayShowCustomEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(R.string.manage_blocked_numbers_label);

        // If the device can use the framework blocking solution, users should not be able to add
        // new blocked numbers from the Blocked Management UI. They will be shown a promo card
        // asking them to migrate to new blocking instead.
        if (FilteredNumberCompat.canUseNewFiltering()) {
            migratePromoView.setVisibility(View.VISIBLE);
            blockedNumbersText.setVisibility(View.GONE);
            getListView().findViewById(R.id.add_number_linear_layout).setVisibility(View.GONE);
            getListView().findViewById(R.id.add_number_linear_layout).setOnClickListener(null);
            mBlockedNumberListDivider.setVisibility(View.GONE);
            mImportSettings.setVisibility(View.GONE);
            getListView().findViewById(R.id.import_button).setOnClickListener(null);
            getListView().findViewById(R.id.view_numbers_button).setOnClickListener(null);
            mBlockedNumbersDisabledForEmergency.setVisibility(View.GONE);
            footerText.setVisibility(View.GONE);
        } else {
            FilteredNumbersUtil.checkForSendToVoicemailContact(
                    getActivity(), new CheckForSendToVoicemailContactListener() {
                        @Override
                        public void onComplete(boolean hasSendToVoicemailContact) {
                            final int visibility =
                                    hasSendToVoicemailContact ? View.VISIBLE : View.GONE;
                            mImportSettings.setVisibility(visibility);
                        }
                    });
        }

        // All views except migrate and the block list are hidden when new filtering is available
        if (!FilteredNumberCompat.canUseNewFiltering()
                && FilteredNumbersUtil.hasRecentEmergencyCall(getContext())) {
            mBlockedNumbersDisabledForEmergency.setVisibility(View.VISIBLE);
        } else {
            mBlockedNumbersDisabledForEmergency.setVisibility(View.GONE);
        }

        mVoicemailEnabledChecker.asyncUpdate();
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.blocked_number_fragment, container, false);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        final String[] projection = {
            FilteredNumberContract.FilteredNumberColumns._ID,
            FilteredNumberContract.FilteredNumberColumns.COUNTRY_ISO,
            FilteredNumberContract.FilteredNumberColumns.NUMBER,
            FilteredNumberContract.FilteredNumberColumns.NORMALIZED_NUMBER
        };
        final String selection = FilteredNumberContract.FilteredNumberColumns.TYPE
                + "=" + FilteredNumberContract.FilteredNumberTypes.BLOCKED_NUMBER;
        return new CursorLoader(
                getContext(), FilteredNumberContract.FilteredNumber.CONTENT_URI, projection,
                selection, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAdapter.swapCursor(data);
        if (FilteredNumberCompat.canUseNewFiltering() || data.getCount() == 0) {
            mBlockedNumberListDivider.setVisibility(View.INVISIBLE);
        } else {
            mBlockedNumberListDivider.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
    }

    @Override
    public void onClick(final View view) {
        final BlockedNumbersSettingsActivity activity =
                (BlockedNumbersSettingsActivity) getActivity();
        if (activity == null) {
            return;
        }

        int resId = view.getId();
        if (resId == R.id.add_number_linear_layout) {
            activity.showSearchUi();
        } else if (resId == R.id.view_numbers_button) {
            activity.showNumbersToImportPreviewUi();
        } else if (resId == R.id.import_button) {
            FilteredNumbersUtil.importSendToVoicemailContacts(activity,
                    new ImportSendToVoicemailContactsListener() {
                        @Override
                        public void onImportComplete() {
                            mImportSettings.setVisibility(View.GONE);
                        }
                    });
        } else if (resId == R.id.migrate_promo_allow_button) {
            view.setEnabled(false);
            MoreObjects.firstNonNull(blockedNumbersMigratorForTest,
                new BlockedNumbersMigrator(getContext().getContentResolver()))
                .migrate(new Listener() {
                    @Override
                    public void onComplete() {
                        getContext().startActivity(
                            FilteredNumberCompat.createManageBlockedNumbersIntent(getContext()));
                        // Remove this activity from the backstack
                        activity.finish();
                }
            });
        }
    }

    @Override
    public void onVisualVoicemailEnabledStatusChanged(boolean newStatus){
        updateActiveVoicemailProvider();
    }

    private void updateActiveVoicemailProvider(){
        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }
        if (mVoicemailEnabledChecker.isVisualVoicemailEnabled()) {
            footerText.setText(R.string.block_number_footer_message_vvm);
        } else {
            footerText.setText(R.string.block_number_footer_message_no_vvm);
        }
    }

    @NeededForTesting
    void setBlockedNumbersMigratorForTest(BlockedNumbersMigrator blockedNumbersMigrator) {
        blockedNumbersMigratorForTest = blockedNumbersMigrator;
    }
}
