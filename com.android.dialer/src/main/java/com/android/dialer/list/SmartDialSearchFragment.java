/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.dialer.list;

import static android.Manifest.permission.CALL_PHONE;

import android.app.Activity;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v13.app.FragmentCompat;
import android.util.Log;
import android.view.View;

import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.util.PermissionsUtil;
import com.android.dialer.dialpad.SmartDialCursorLoader;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ScreenEvent;
import com.android.dialer.R;
import com.android.dialer.widget.EmptyContentView;
import com.android.incallui.Call.LogState;

import java.util.ArrayList;

/**
 * Implements a fragment to load and display SmartDial search results.
 */
public class SmartDialSearchFragment extends SearchFragment
        implements EmptyContentView.OnEmptyViewActionButtonClickedListener,
        FragmentCompat.OnRequestPermissionsResultCallback {
    private static final String TAG = SmartDialSearchFragment.class.getSimpleName();

    private static final int CALL_PHONE_PERMISSION_REQUEST_CODE = 1;

    /**
     * Creates a SmartDialListAdapter to display and operate on search results.
     */
    @Override
    protected ContactEntryListAdapter createListAdapter() {
        SmartDialNumberListAdapter adapter = new SmartDialNumberListAdapter(getActivity());
        adapter.setUseCallableUri(super.usesCallableUri());
        adapter.setQuickContactEnabled(true);
        // Set adapter's query string to restore previous instance state.
        adapter.setQueryString(getQueryString());
        adapter.setListener(this);
        return adapter;
    }

    /**
     * Creates a SmartDialCursorLoader object to load query results.
     */
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // Smart dialing does not support Directory Load, falls back to normal search instead.
        if (id == getDirectoryLoaderId()) {
            return super.onCreateLoader(id, args);
        } else {
            final SmartDialNumberListAdapter adapter = (SmartDialNumberListAdapter) getAdapter();
            SmartDialCursorLoader loader = new SmartDialCursorLoader(super.getContext());
            adapter.configureLoader(loader);
            return loader;
        }
    }

    /**
     * Gets the Phone Uri of an entry for calling.
     * @param position Location of the data of interest.
     * @return Phone Uri to establish a phone call.
     */
    @Override
    protected Uri getPhoneUri(int position) {
        final SmartDialNumberListAdapter adapter = (SmartDialNumberListAdapter) getAdapter();
        return adapter.getDataUri(position);
    }

    @Override
    protected void setupEmptyView() {
        if (mEmptyView != null && getActivity() != null) {
            if (!PermissionsUtil.hasPermission(getActivity(), CALL_PHONE)) {
                mEmptyView.setImage(R.drawable.empty_contacts);
                mEmptyView.setActionLabel(R.string.permission_single_turn_on);
                mEmptyView.setDescription(R.string.permission_place_call);
                mEmptyView.setActionClickedListener(this);
            } else {
                mEmptyView.setImage(EmptyContentView.NO_IMAGE);
                mEmptyView.setActionLabel(EmptyContentView.NO_LABEL);
                mEmptyView.setDescription(EmptyContentView.NO_LABEL);
            }
        }
    }

    @Override
    public void onEmptyViewActionButtonClicked() {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        FragmentCompat.requestPermissions(this, new String[] {CALL_PHONE},
            CALL_PHONE_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        if (requestCode == CALL_PHONE_PERMISSION_REQUEST_CODE) {
            setupEmptyView();
        }
    }

    @Override
    protected int getCallInitiationType(boolean isRemoteDirectory) {
        return LogState.INITIATION_SMART_DIAL;
    }

    public boolean isShowingPermissionRequest() {
        return mEmptyView != null && mEmptyView.isShowingContent();
    }
}
