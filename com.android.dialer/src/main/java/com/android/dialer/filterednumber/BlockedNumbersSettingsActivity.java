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

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.Toast;

import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.dialog.IndeterminateProgressDialog;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.dialer.R;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler;
import com.android.dialer.list.BlockedListSearchAdapter;
import com.android.dialer.list.OnListFragmentScrolledListener;
import com.android.dialer.list.BlockedListSearchFragment;
import com.android.dialer.list.SearchFragment;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ScreenEvent;

public class BlockedNumbersSettingsActivity extends AppCompatActivity
        implements SearchFragment.HostInterface {

    private static final String TAG_BLOCKED_MANAGEMENT_FRAGMENT = "blocked_management";
    private static final String TAG_BLOCKED_SEARCH_FRAGMENT = "blocked_search";
    private static final String TAG_VIEW_NUMBERS_TO_IMPORT_FRAGMENT = "view_numbers_to_import";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.blocked_numbers_activity);

        // If savedInstanceState != null, the Activity will automatically restore the last fragment.
        if (savedInstanceState == null) {
            showManagementUi();
        }
    }

    /**
     * Shows fragment with the list of currently blocked numbers and settings related to blocking.
     */
    public void showManagementUi() {
        BlockedNumbersFragment fragment = (BlockedNumbersFragment) getFragmentManager()
                .findFragmentByTag(TAG_BLOCKED_MANAGEMENT_FRAGMENT);
        if (fragment == null) {
            fragment = new BlockedNumbersFragment();
        }

        getFragmentManager().beginTransaction()
                .replace(R.id.blocked_numbers_activity_container, fragment,
                        TAG_BLOCKED_MANAGEMENT_FRAGMENT)
                .commit();

        Logger.logScreenView(ScreenEvent.BLOCKED_NUMBER_MANAGEMENT, this);
    }

    /**
     * Shows fragment with search UI for browsing/finding numbers to block.
     */
    public void showSearchUi() {
        BlockedListSearchFragment fragment = (BlockedListSearchFragment) getFragmentManager()
                .findFragmentByTag(TAG_BLOCKED_SEARCH_FRAGMENT);
        if (fragment == null) {
            fragment = new BlockedListSearchFragment();
            fragment.setHasOptionsMenu(false);
            fragment.setShowEmptyListForNullQuery(true);
            fragment.setDirectorySearchEnabled(false);
        }

        getFragmentManager().beginTransaction()
                .replace(R.id.blocked_numbers_activity_container, fragment,
                        TAG_BLOCKED_SEARCH_FRAGMENT)
                .addToBackStack(null)
                .commit();

        Logger.logScreenView(ScreenEvent.BLOCKED_NUMBER_ADD_NUMBER, this);
    }

    /**
     * Shows fragment with UI to preview the numbers of contacts currently marked as
     * send-to-voicemail in Contacts. These numbers can be imported into Dialer's blocked number
     * list.
     */
    public void showNumbersToImportPreviewUi() {
        ViewNumbersToImportFragment fragment = (ViewNumbersToImportFragment) getFragmentManager()
                .findFragmentByTag(TAG_VIEW_NUMBERS_TO_IMPORT_FRAGMENT);
        if (fragment == null) {
            fragment = new ViewNumbersToImportFragment();
        }

        getFragmentManager().beginTransaction()
                .replace(R.id.blocked_numbers_activity_container, fragment,
                        TAG_VIEW_NUMBERS_TO_IMPORT_FRAGMENT)
                .addToBackStack(null)
                .commit();
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
    public void onBackPressed() {
        // TODO: Achieve back navigation without overriding onBackPressed.
        if (getFragmentManager().getBackStackEntryCount() > 0) {
            getFragmentManager().popBackStack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean isActionBarShowing() {
        return false;
    }

    @Override
    public boolean isDialpadShown() {
        return false;
    }

    @Override
    public int getDialpadHeight() {
        return 0;
    }

    @Override
    public int getActionBarHideOffset() {
        return 0;
    }

    @Override
    public int getActionBarHeight() {
        return 0;
    }
}
