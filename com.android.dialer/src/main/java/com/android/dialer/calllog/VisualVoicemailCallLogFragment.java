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

package com.android.dialer.calllog;

import android.database.ContentObserver;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.VoicemailContract;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.dialer.R;
import com.android.dialer.list.ListsFragment;
import com.android.dialer.voicemail.VoicemailPlaybackPresenter;

public class VisualVoicemailCallLogFragment extends CallLogFragment {

    private VoicemailPlaybackPresenter mVoicemailPlaybackPresenter;
    private final ContentObserver mVoicemailStatusObserver = new CustomContentObserver();

    public VisualVoicemailCallLogFragment() {
        super(CallLog.Calls.VOICEMAIL_TYPE);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        mVoicemailPlaybackPresenter = VoicemailPlaybackPresenter.getInstance(getActivity(), state);
        getActivity().getContentResolver().registerContentObserver(
                VoicemailContract.Status.CONTENT_URI, true, mVoicemailStatusObserver);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View view = inflater.inflate(R.layout.call_log_fragment, container, false);
        setupView(view, mVoicemailPlaybackPresenter);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        mVoicemailPlaybackPresenter.onResume();
    }

    @Override
    public void onPause() {
        mVoicemailPlaybackPresenter.onPause();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mVoicemailPlaybackPresenter.onDestroy();
        getActivity().getContentResolver().unregisterContentObserver(mVoicemailStatusObserver);
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mVoicemailPlaybackPresenter.onSaveInstanceState(outState);
    }

    @Override
    public void fetchCalls() {
        super.fetchCalls();
        ((ListsFragment) getParentFragment()).updateTabUnreadCounts();
    }
}
