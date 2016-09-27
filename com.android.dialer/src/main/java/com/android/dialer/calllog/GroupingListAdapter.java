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

package com.android.dialer.calllog;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.os.Handler;
import android.support.v7.widget.RecyclerView;
import android.util.SparseIntArray;

/**
 * Maintains a list that groups items into groups of consecutive elements which are disjoint,
 * that is, an item can only belong to one group. This is leveraged for grouping calls in the
 * call log received from or made to the same phone number.
 *
 * There are two integers stored as metadata for every list item in the adapter.
 */
abstract class GroupingListAdapter extends RecyclerView.Adapter {

    private Context mContext;
    private Cursor mCursor;

    /**
     * SparseIntArray, which maps the cursor position of the first element of a group to the size
     * of the group. The index of a key in this map corresponds to the list position of that group.
     */
    private SparseIntArray mGroupMetadata;
    private int mItemCount;

    protected ContentObserver mChangeObserver = new ContentObserver(new Handler()) {
        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            onContentChanged();
        }
    };

    protected DataSetObserver mDataSetObserver = new DataSetObserver() {
        @Override
        public void onChanged() {
            notifyDataSetChanged();
        }
    };

    public GroupingListAdapter(Context context) {
        mContext = context;
        reset();
    }

    /**
     * Finds all groups of adjacent items in the cursor and calls {@link #addGroup} for
     * each of them.
     */
    protected abstract void addGroups(Cursor cursor);

    protected abstract void addVoicemailGroups(Cursor cursor);

    protected abstract void onContentChanged();

    public void changeCursor(Cursor cursor) {
        changeCursor(cursor, false);
    }

    public void changeCursorVoicemail(Cursor cursor) {
        changeCursor(cursor, true);
    }

    public void changeCursor(Cursor cursor, boolean voicemail) {
        if (cursor == mCursor) {
            return;
        }

        if (mCursor != null) {
            mCursor.unregisterContentObserver(mChangeObserver);
            mCursor.unregisterDataSetObserver(mDataSetObserver);
            mCursor.close();
        }

        // Reset whenever the cursor is changed.
        reset();
        mCursor = cursor;

        if (cursor != null) {
            if (voicemail) {
                addVoicemailGroups(mCursor);
            } else {
                addGroups(mCursor);
            }

            // Calculate the item count by subtracting group child counts from the cursor count.
            mItemCount = mGroupMetadata.size();

            cursor.registerContentObserver(mChangeObserver);
            cursor.registerDataSetObserver(mDataSetObserver);
            notifyDataSetChanged();
        }
    }

    /**
     * Records information about grouping in the list.
     * Should be called by the overridden {@link #addGroups} method.
     */
    public void addGroup(int cursorPosition, int groupSize) {
        int lastIndex = mGroupMetadata.size() - 1;
        if (lastIndex < 0 || cursorPosition <= mGroupMetadata.keyAt(lastIndex)) {
            mGroupMetadata.put(cursorPosition, groupSize);
        } else {
            // Optimization to avoid binary search if adding groups in ascending cursor position.
            mGroupMetadata.append(cursorPosition, groupSize);
        }
    }

    @Override
    public int getItemCount() {
        return mItemCount;
    }

    /**
     * Given the position of a list item, returns the size of the group of items corresponding to
     * that position.
     */
    public int getGroupSize(int listPosition) {
        if (listPosition < 0 || listPosition >= mGroupMetadata.size()) {
            return 0;
        }

        return mGroupMetadata.valueAt(listPosition);
    }

    /**
     * Given the position of a list item, returns the the first item in the group of items
     * corresponding to that position.
     */
    public Object getItem(int listPosition) {
        if (mCursor == null || listPosition < 0 || listPosition >= mGroupMetadata.size()) {
            return null;
        }

        int cursorPosition = mGroupMetadata.keyAt(listPosition);
        if (mCursor.moveToPosition(cursorPosition)) {
            return mCursor;
        } else {
            return null;
        }
    }

    private void reset() {
        mItemCount = 0;
        mGroupMetadata = new SparseIntArray();
    }
}
