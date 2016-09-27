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

package com.android.dialer.database;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import com.android.dialer.compat.FilteredNumberCompat;
import com.android.dialer.database.FilteredNumberContract.FilteredNumber;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberColumns;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberTypes;

public class FilteredNumberAsyncQueryHandler extends AsyncQueryHandler {
    private static final int NO_TOKEN = 0;

    public FilteredNumberAsyncQueryHandler(ContentResolver cr) {
        super(cr);
    }

    /**
     * Methods for FilteredNumberAsyncQueryHandler result returns.
     */
    private static abstract class Listener {
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
        }
        protected void onInsertComplete(int token, Object cookie, Uri uri) {
        }
        protected void onUpdateComplete(int token, Object cookie, int result) {
        }
        protected void onDeleteComplete(int token, Object cookie, int result) {
        }
    }

    public interface OnCheckBlockedListener {
        /**
         * Invoked after querying if a number is blocked.
         * @param id The ID of the row if blocked, null otherwise.
         */
        void onCheckComplete(Integer id);
    }

    public interface OnBlockNumberListener {
        /**
         * Invoked after inserting a blocked number.
         * @param uri The uri of the newly created row.
         */
        void onBlockComplete(Uri uri);
    }

    public interface OnUnblockNumberListener {
        /**
         * Invoked after removing a blocked number
         * @param rows The number of rows affected (expected value 1).
         * @param values The deleted data (used for restoration).
         */
        void onUnblockComplete(int rows, ContentValues values);
    }

    public interface OnHasBlockedNumbersListener {
        /**
         * @param hasBlockedNumbers {@code true} if any blocked numbers are stored.
         *     {@code false} otherwise.
         */
        void onHasBlockedNumbers(boolean hasBlockedNumbers);
    }

    @Override
    protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
        if (cookie != null) {
            ((Listener) cookie).onQueryComplete(token, cookie, cursor);
        }
    }

    @Override
    protected void onInsertComplete(int token, Object cookie, Uri uri) {
        if (cookie != null) {
            ((Listener) cookie).onInsertComplete(token, cookie, uri);
        }
    }

    @Override
    protected void onUpdateComplete(int token, Object cookie, int result) {
        if (cookie != null) {
            ((Listener) cookie).onUpdateComplete(token, cookie, result);
        }
    }

    @Override
    protected void onDeleteComplete(int token, Object cookie, int result) {
        if (cookie != null) {
            ((Listener) cookie).onDeleteComplete(token, cookie, result);
        }
    }

    public final void incrementFilteredCount(Integer id) {
        // No concept of counts with new filtering
        if (FilteredNumberCompat.useNewFiltering()) {
            return;
        }
        startUpdate(NO_TOKEN, null,
                ContentUris.withAppendedId(FilteredNumber.CONTENT_URI_INCREMENT_FILTERED_COUNT, id),
                null, null, null);
    }

    public void hasBlockedNumbers(final OnHasBlockedNumbersListener listener) {
        startQuery(NO_TOKEN,
                new Listener() {
                    @Override
                    protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                        listener.onHasBlockedNumbers(cursor != null && cursor.getCount() > 0);
                    }
                },
                FilteredNumberCompat.getContentUri(null),
                new String[]{ FilteredNumberCompat.getIdColumnName() },
                FilteredNumberCompat.useNewFiltering() ? null : FilteredNumberColumns.TYPE
                        + "=" + FilteredNumberTypes.BLOCKED_NUMBER,
                null,
                null);
    }

    /**
     * Check if this number has been blocked.
     *
     * @return {@code false} if the number was invalid and couldn't be checked,
     *     {@code true} otherwise,
     */
    public boolean isBlockedNumber(
            final OnCheckBlockedListener listener, String number, String countryIso) {
        final String e164Number = PhoneNumberUtils.formatNumberToE164(number, countryIso);
        if (TextUtils.isEmpty(e164Number)) {
            return false;
        }

        startQuery(NO_TOKEN,
                new Listener() {
                    @Override
                    protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                        /*
                         * In the frameworking blocking, numbers can be blocked in both e164 format
                         * and not, resulting in multiple rows being returned for this query. For
                         * example, both '16502530000' and '6502530000' can exist at the same time
                         * and will be returned by this query.
                         */
                        if (cursor == null || cursor.getCount() == 0) {
                            listener.onCheckComplete(null);
                            return;
                        }
                        cursor.moveToFirst();
                        // New filtering doesn't have a concept of type
                        if (!FilteredNumberCompat.useNewFiltering()
                                && cursor.getInt(cursor.getColumnIndex(FilteredNumberColumns.TYPE))
                                != FilteredNumberTypes.BLOCKED_NUMBER) {
                            listener.onCheckComplete(null);
                            return;
                        }
                        listener.onCheckComplete(
                                cursor.getInt(cursor.getColumnIndex(FilteredNumberColumns._ID)));
                    }
                },
                FilteredNumberCompat.getContentUri(null),
                FilteredNumberCompat.filter(new String[]{FilteredNumberCompat.getIdColumnName(),
                        FilteredNumberCompat.getTypeColumnName()}),
                FilteredNumberCompat.getE164NumberColumnName() + " = ?",
                new String[]{e164Number},
                null);

        return true;
    }

    public void blockNumber(
            final OnBlockNumberListener listener, String number, @Nullable String countryIso) {
        blockNumber(listener, null, number, countryIso);
    }

    /**
     * Add a number manually blocked by the user.
     */
    public void blockNumber(
            final OnBlockNumberListener listener,
            @Nullable String normalizedNumber,
            String number,
            @Nullable String countryIso) {
        blockNumber(listener, FilteredNumberCompat.newBlockNumberContentValues(number,
                normalizedNumber, countryIso));
    }

    /**
     * Block a number with specified ContentValues. Can be manually added or a restored row
     * from performing the 'undo' action after unblocking.
     */
    public void blockNumber(final OnBlockNumberListener listener, ContentValues values) {
        startInsert(NO_TOKEN,
                new Listener() {
                    @Override
                    public void onInsertComplete(int token, Object cookie, Uri uri) {
                        if (listener != null ) {
                            listener.onBlockComplete(uri);
                        }
                    }
                }, FilteredNumberCompat.getContentUri(null), values);
    }

    /**
     * Unblocks the number with the given id.
     *
     * @param listener (optional) The {@link OnUnblockNumberListener} called after the number is
     * unblocked.
     * @param id The id of the number to unblock.
     */
    public void unblock(@Nullable final OnUnblockNumberListener listener, Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("Null id passed into unblock");
        }
        unblock(listener, FilteredNumberCompat.getContentUri(id));
    }

    /**
     * Removes row from database.
     * @param listener (optional) The {@link OnUnblockNumberListener} called after the number is
     * unblocked.
     * @param uri The uri of row to remove, from
     * {@link FilteredNumberAsyncQueryHandler#blockNumber}.
     */
    public void unblock(@Nullable final OnUnblockNumberListener listener, final Uri uri) {
        startQuery(NO_TOKEN, new Listener() {
            @Override
            public void onQueryComplete(int token, Object cookie, Cursor cursor) {
                int rowsReturned = cursor == null ? 0 : cursor.getCount();
                if (rowsReturned != 1) {
                    throw new SQLiteDatabaseCorruptException
                            ("Returned " + rowsReturned + " rows for uri "
                                    + uri + "where 1 expected.");
                }
                cursor.moveToFirst();
                final ContentValues values = new ContentValues();
                DatabaseUtils.cursorRowToContentValues(cursor, values);
                values.remove(FilteredNumberCompat.getIdColumnName());

                startDelete(NO_TOKEN, new Listener() {
                    @Override
                    public void onDeleteComplete(int token, Object cookie, int result) {
                        if (listener != null) {
                            listener.onUnblockComplete(result, values);
                        }
                    }
                }, uri, null, null);
            }
        }, uri, null, null, null, null);
    }
}
