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
package com.android.dialer.list;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.view.View;

import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.list.ContactListItemView;
import com.android.dialer.R;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler;

/**
 * List adapter to display search results for adding a blocked number.
 */
public class BlockedListSearchAdapter extends RegularSearchListAdapter {

    private Resources mResources;
    private FilteredNumberAsyncQueryHandler mFilteredNumberAsyncQueryHandler;

    public BlockedListSearchAdapter(Context context) {
        super(context);
        mResources = context.getResources();
        disableAllShortcuts();
        setShortcutEnabled(SHORTCUT_BLOCK_NUMBER, true);

        mFilteredNumberAsyncQueryHandler =
                new FilteredNumberAsyncQueryHandler(context.getContentResolver());
    }

    @Override
    protected boolean isChanged(boolean showNumberShortcuts) {
        return setShortcutEnabled(SHORTCUT_BLOCK_NUMBER, showNumberShortcuts || mIsQuerySipAddress);
    }

    public void setViewBlocked(ContactListItemView view, Integer id) {
        view.setTag(R.id.block_id, id);
        final int textColor = mResources.getColor(R.color.blocked_number_block_color);
        view.getDataView().setTextColor(textColor);
        view.getLabelView().setTextColor(textColor);
        //TODO: Add icon
    }

    public void setViewUnblocked(ContactListItemView view) {
        view.setTag(R.id.block_id, null);
        final int textColor = mResources.getColor(R.color.dialtacts_secondary_text_color);
        view.getDataView().setTextColor(textColor);
        view.getLabelView().setTextColor(textColor);
        //TODO: Remove icon
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        super.bindView(itemView, partition, cursor, position);

        final ContactListItemView view = (ContactListItemView) itemView;
        // Reset view state to unblocked.
        setViewUnblocked(view);

        final String number = getPhoneNumber(position);
        final String countryIso = GeoUtil.getCurrentCountryIso(mContext);
        final FilteredNumberAsyncQueryHandler.OnCheckBlockedListener onCheckListener =
                new FilteredNumberAsyncQueryHandler.OnCheckBlockedListener() {
                    @Override
                    public void onCheckComplete(Integer id) {
                        if (id != null) {
                            setViewBlocked(view, id);
                        }
                    }
                };
        mFilteredNumberAsyncQueryHandler.isBlockedNumber(
                onCheckListener, number, countryIso);
    }
}
