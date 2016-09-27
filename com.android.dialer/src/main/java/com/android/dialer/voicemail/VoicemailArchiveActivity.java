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

package com.android.dialer.voicemail;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MenuItem;
import android.view.View;

import com.android.contacts.common.GeoUtil;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.TransactionSafeActivity;
import com.android.dialer.calllog.CallLogAdapter;
import com.android.dialer.calllog.CallLogQueryHandler;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.widget.EmptyContentView;
import com.android.dialerbind.ObjectFactory;

/**
 * This activity manages all the voicemails archived by the user.
 */
public class VoicemailArchiveActivity extends TransactionSafeActivity
        implements CallLogAdapter.CallFetcher, CallLogQueryHandler.Listener {
    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;
    private EmptyContentView mEmptyListView;
    private CallLogAdapter mAdapter;
    private VoicemailPlaybackPresenter mVoicemailPlaybackPresenter;
    private CallLogQueryHandler mCallLogQueryHandler;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!isSafeToCommitTransactions()) {
            return true;
        }

        switch (item.getItemId()) {
            case android.R.id.home:
                Intent intent = new Intent(this, DialtactsActivity.class);
                // Clears any activities between VoicemailArchiveActivity and DialtactsActivity
                // on the activity stack and reuses the existing instance of DialtactsActivity
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.call_log_fragment);

        // Make window opaque to reduce overdraw
        getWindow().setBackgroundDrawable(null);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setElevation(0);

        mCallLogQueryHandler = new CallLogQueryHandler(this, getContentResolver(), this);
        mVoicemailPlaybackPresenter = VoicemailArchivePlaybackPresenter
                .getInstance(this, savedInstanceState);

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        mRecyclerView.setHasFixedSize(true);
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mEmptyListView = (EmptyContentView) findViewById(R.id.empty_list_view);
        mEmptyListView.setDescription(R.string.voicemail_archive_empty);
        mEmptyListView.setImage(R.drawable.empty_call_log);

        mAdapter = ObjectFactory.newCallLogAdapter(
                this,
                this,
                new ContactInfoHelper(this, GeoUtil.getCurrentCountryIso(this)),
                mVoicemailPlaybackPresenter,
                CallLogAdapter.ACTIVITY_TYPE_ARCHIVE);
        mRecyclerView.setAdapter(mAdapter);
        fetchCalls();
    }

    @Override
    protected void onPause() {
        mVoicemailPlaybackPresenter.onPause();
        mAdapter.onPause();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        mAdapter.onResume();
        mVoicemailPlaybackPresenter.onResume();
    }

    @Override
    public void onDestroy() {
        mVoicemailPlaybackPresenter.onDestroy();
        mAdapter.changeCursor(null);
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mVoicemailPlaybackPresenter.onSaveInstanceState(outState);
    }

    @Override
    public void fetchCalls() {
        mCallLogQueryHandler.fetchVoicemailArchive();
    }

    @Override
    public void onVoicemailStatusFetched(Cursor statusCursor) {
        // Do nothing
    }

    @Override
    public void onVoicemailUnreadCountFetched(Cursor cursor) {
        // Do nothing
    }

    @Override
    public void onMissedCallsUnreadCountFetched(Cursor cursor) {
        // Do nothing
    }

    @Override
    public boolean onCallsFetched(Cursor cursor) {
        mAdapter.changeCursorVoicemail(cursor);
        boolean showListView = cursor != null && cursor.getCount() > 0;
        mRecyclerView.setVisibility(showListView ? View.VISIBLE : View.GONE);
        mEmptyListView.setVisibility(!showListView ? View.VISIBLE : View.GONE);
        return true;
    }
}
