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

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Binder;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.common.GeoUtil;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberColumns;
import com.android.dialerbind.DatabaseHelperManager;
import com.android.dialerbind.ObjectFactory;
import com.google.common.annotations.VisibleForTesting;

import java.util.Arrays;

/**
 * Filtered number content provider.
 */
public class FilteredNumberProvider extends ContentProvider {

    private static String TAG = FilteredNumberProvider.class.getSimpleName();

    private DialerDatabaseHelper mDialerDatabaseHelper;

    private static final int FILTERED_NUMBERS_TABLE = 1;
    private static final int FILTERED_NUMBERS_TABLE_ID = 2;
    private static final int FILTERED_NUMBERS_INCREMENT_FILTERED_COUNT = 3;

    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    @Override
    public boolean onCreate() {
        mDialerDatabaseHelper = getDatabaseHelper(getContext());
        if (mDialerDatabaseHelper == null) {
            return false;
        }
        sUriMatcher.addURI(ObjectFactory.getFilteredNumberProviderAuthority(),
                FilteredNumberContract.FilteredNumber.FILTERED_NUMBERS_TABLE,
                FILTERED_NUMBERS_TABLE);
        sUriMatcher.addURI(ObjectFactory.getFilteredNumberProviderAuthority(),
                FilteredNumberContract.FilteredNumber.FILTERED_NUMBERS_TABLE + "/#",
                FILTERED_NUMBERS_TABLE_ID);
        sUriMatcher.addURI(ObjectFactory.getFilteredNumberProviderAuthority(),
                FilteredNumberContract.FilteredNumber.FILTERED_NUMBERS_INCREMENT_FILTERED_COUNT
                        + "/#",
                FILTERED_NUMBERS_INCREMENT_FILTERED_COUNT);
        return true;
    }

    @VisibleForTesting
    protected DialerDatabaseHelper getDatabaseHelper(Context context) {
        return DatabaseHelperManager.getDatabaseHelper(context);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        final SQLiteDatabase db = mDialerDatabaseHelper.getReadableDatabase();
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(DialerDatabaseHelper.Tables.FILTERED_NUMBER_TABLE);
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case FILTERED_NUMBERS_TABLE:
                break;
            case FILTERED_NUMBERS_TABLE_ID:
                qb.appendWhere(FilteredNumberColumns._ID + "=" + ContentUris.parseId(uri));
                break;
            default:
                throw new IllegalArgumentException("Unknown uri: " + uri);
        }
        final Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, null);
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(),
                    FilteredNumberContract.FilteredNumber.CONTENT_URI);
        } else {
            Log.d(TAG, "CURSOR WAS NULL");
        }
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return FilteredNumberContract.FilteredNumber.CONTENT_ITEM_TYPE;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mDialerDatabaseHelper.getWritableDatabase();
        setDefaultValues(values);
        long id = db.insert(DialerDatabaseHelper.Tables.FILTERED_NUMBER_TABLE, null, values);
        if (id < 0) {
            return null;
        }
        notifyChange(uri);
        return ContentUris.withAppendedId(uri, id);
    }

    @VisibleForTesting
    protected long getCurrentTimeMs() {
        return System.currentTimeMillis();
    }

    private void setDefaultValues(ContentValues values) {
        if (values.getAsString(FilteredNumberColumns.COUNTRY_ISO) == null) {
            values.put(FilteredNumberColumns.COUNTRY_ISO,
                    GeoUtil.getCurrentCountryIso(getContext()));
        }
        if (values.getAsInteger(FilteredNumberColumns.TIMES_FILTERED) == null) {
            values.put(FilteredNumberContract.FilteredNumberColumns.TIMES_FILTERED, 0);
        }
        if (values.getAsLong(FilteredNumberColumns.CREATION_TIME) == null) {
            values.put(FilteredNumberColumns.CREATION_TIME, getCurrentTimeMs());
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = mDialerDatabaseHelper.getWritableDatabase();
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case FILTERED_NUMBERS_TABLE:
                break;
            case FILTERED_NUMBERS_TABLE_ID:
                selection = getSelectionWithId(selection, ContentUris.parseId(uri));
                break;
            default:
                throw new IllegalArgumentException("Unknown uri: " + uri);
        }
        int rows = db.delete(DialerDatabaseHelper.Tables.FILTERED_NUMBER_TABLE,
                selection,
                selectionArgs);
        if (rows > 0) {
            notifyChange(uri);
        }
        return rows;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SQLiteDatabase db = mDialerDatabaseHelper.getWritableDatabase();
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case FILTERED_NUMBERS_TABLE:
                break;
            case FILTERED_NUMBERS_TABLE_ID:
                selection = getSelectionWithId(selection, ContentUris.parseId(uri));
                break;
            case FILTERED_NUMBERS_INCREMENT_FILTERED_COUNT:
                final long id = ContentUris.parseId(uri);
                try {
                    db.execSQL(" UPDATE " + DialerDatabaseHelper.Tables.FILTERED_NUMBER_TABLE
                            + " SET" + FilteredNumberColumns.TIMES_FILTERED + "="
                            + FilteredNumberColumns.TIMES_FILTERED + "+1,"
                            + FilteredNumberColumns.LAST_TIME_FILTERED + "="
                            + getCurrentTimeMs()
                            + " WHERE " + FilteredNumberColumns._ID + "=" + id);
                } catch (SQLException e) {
                    Log.d(TAG, "Could not update blocked statistics for " + id);
                    return 0;
                }
                return 1;
            default:
                throw new IllegalArgumentException("Unknown uri: " + uri);
        }
        int rows = db.update(DialerDatabaseHelper.Tables.FILTERED_NUMBER_TABLE,
                values,
                selection,
                selectionArgs);
        if (rows > 0 ) {
            notifyChange(uri);
        }
        return rows;
    }

    private String getSelectionWithId(String selection, long id) {
        if (TextUtils.isEmpty(selection)) {
            return FilteredNumberContract.FilteredNumberColumns._ID + "=" + id;
        } else {
            return selection + "AND " + FilteredNumberContract.FilteredNumberColumns._ID + "=" + id;
        }
    }

    private void notifyChange(Uri uri) {
        getContext().getContentResolver().notifyChange(uri, null);
    }
}
