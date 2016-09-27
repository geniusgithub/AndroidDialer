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
 * limitations under the License
 */

package com.android.dialer.database;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import com.android.dialerbind.DatabaseHelperManager;
import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * An implementation of the Voicemail Archive content provider. This class performs
 * all database level operations on the voicemail_archive_table.
 */
public class VoicemailArchiveProvider extends ContentProvider {
    private static final String TAG = "VMArchiveProvider";
    private static final int VOICEMAIL_ARCHIVE_TABLE = 1;
    private static final int VOICEMAIL_ARCHIVE_TABLE_ID = 2;
    private static final String VOICEMAIL_FOLDER = "voicemails";

    private DialerDatabaseHelper mDialerDatabaseHelper;
    private final UriMatcher mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    @Override
    public boolean onCreate() {
         mDialerDatabaseHelper = getDatabaseHelper(getContext());
         if (mDialerDatabaseHelper == null) {
             return false;
         }
        mUriMatcher.addURI(VoicemailArchiveContract.AUTHORITY,
                 VoicemailArchiveContract.VoicemailArchive.VOICEMAIL_ARCHIVE_TABLE,
                 VOICEMAIL_ARCHIVE_TABLE);
        mUriMatcher.addURI(VoicemailArchiveContract.AUTHORITY,
                 VoicemailArchiveContract.VoicemailArchive.VOICEMAIL_ARCHIVE_TABLE + "/#",
                 VOICEMAIL_ARCHIVE_TABLE_ID);
         return true;
    }

    @VisibleForTesting
    protected DialerDatabaseHelper getDatabaseHelper(Context context) {
        return DatabaseHelperManager.getDatabaseHelper(context);
    }

    /**
     * Used by the test class because it extends {@link android.test.ProviderTestCase2} in which the
     * {@link android.test.IsolatedContext} returns /dev/null when getFilesDir() is called.
     *
     * @see android.test.IsolatedContext#getFilesDir
     */
    @VisibleForTesting
    protected File getFilesDir() {
        return getContext().getFilesDir();
    }

    @Nullable
    @Override
    public Cursor query(Uri uri,
                        @Nullable String[] projection,
                        @Nullable String selection,
                        @Nullable String[] selectionArgs,
                        @Nullable String sortOrder) {
        SQLiteDatabase db = mDialerDatabaseHelper.getReadableDatabase();
        SQLiteQueryBuilder queryBuilder = getQueryBuilder(uri);
        Cursor cursor = queryBuilder
                .query(db, projection, selection, selectionArgs, null, null, sortOrder);
        if (cursor != null) {
            cursor.setNotificationUri(getContext().getContentResolver(),
                    VoicemailArchiveContract.VoicemailArchive.CONTENT_URI);
        }
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return VoicemailArchiveContract.VoicemailArchive.CONTENT_ITEM_TYPE;
    }

    @Nullable
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mDialerDatabaseHelper.getWritableDatabase();
        long id = db.insert(DialerDatabaseHelper.Tables.VOICEMAIL_ARCHIVE_TABLE,
                null, values);
        if (id < 0) {
            return null;
        }
        notifyChange(uri);
        // Create the directory for archived voicemails if it doesn't already exist
        File directory = new File(getFilesDir(), VOICEMAIL_FOLDER);
        directory.mkdirs();
        Uri newUri = ContentUris.withAppendedId(uri, id);

        // Create new file only if path is not provided to one
        if (!values.containsKey(VoicemailArchiveContract.VoicemailArchive._DATA)) {
            String fileExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType(
                    values.getAsString(VoicemailArchiveContract.VoicemailArchive.MIME_TYPE));
            File voicemailFile = new File(directory,
                    TextUtils.isEmpty(fileExtension) ? Long.toString(id) :
                            id + "." + fileExtension);
            values.put(VoicemailArchiveContract.VoicemailArchive._DATA, voicemailFile.getPath());
        }
        update(newUri, values, null, null);
        return newUri;
    }


    @Override
    public int delete(Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        SQLiteDatabase db = mDialerDatabaseHelper.getWritableDatabase();
        SQLiteQueryBuilder queryBuilder = getQueryBuilder(uri);
        Cursor cursor = queryBuilder.query(db, null, selection, selectionArgs, null, null, null);

        // Delete all the voicemail files related to the selected rows
        while (cursor.moveToNext()) {
            deleteFile(cursor.getString(cursor.getColumnIndex(
                    VoicemailArchiveContract.VoicemailArchive._DATA)));
        }

        int rows = db.delete(DialerDatabaseHelper.Tables.VOICEMAIL_ARCHIVE_TABLE,
                getSelectionWithId(selection, uri),
                selectionArgs);
        if (rows > 0) {
            notifyChange(uri);
        }
        return rows;
    }

    @Override
    public int update(Uri uri,
                      ContentValues values,
                      @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        SQLiteDatabase db = mDialerDatabaseHelper.getWritableDatabase();
        selection = getSelectionWithId(selection, uri);
        int rows = db.update(DialerDatabaseHelper.Tables.VOICEMAIL_ARCHIVE_TABLE,
                values,
                selection,
                selectionArgs);
        if (rows > 0) {
            notifyChange(uri);
        }
        return rows;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mUriMatcher.match(uri) != VOICEMAIL_ARCHIVE_TABLE_ID) {
            throw new IllegalArgumentException("URI Invalid.");
        }
        return openFileHelper(uri, mode);
    }

    private void deleteFile(@Nullable String path) {
        if (TextUtils.isEmpty(path)) {
            return;
        }
        File file = new File(path);
        if (file.exists()) {
            file.delete();
        }
    }

    private SQLiteQueryBuilder getQueryBuilder(Uri uri) {
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(DialerDatabaseHelper.Tables.VOICEMAIL_ARCHIVE_TABLE);
        String selectionWithId = getSelectionWithId(null, uri);
        if (!TextUtils.isEmpty(selectionWithId)) {
            queryBuilder.appendWhere(selectionWithId);
        }
        return queryBuilder;
    }

    private String getSelectionWithId(String selection, Uri uri) {
        int match = mUriMatcher.match(uri);
        switch (match) {
            case VOICEMAIL_ARCHIVE_TABLE:
                return selection;
            case VOICEMAIL_ARCHIVE_TABLE_ID:
                String idStr = VoicemailArchiveContract.VoicemailArchive._ID + "=" +
                        ContentUris.parseId(uri);
                return TextUtils.isEmpty(selection) ? idStr : selection + " AND " + idStr;
            default:
                throw new IllegalArgumentException("Unknown uri: " + uri);
        }
    }

    private void notifyChange(Uri uri) {
        getContext().getContentResolver().notifyChange(uri, null);
    }
}
