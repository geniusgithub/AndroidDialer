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

package com.android.dialer.voicemail;

import com.android.contacts.common.testing.NeededForTesting;
import com.android.dialer.calllog.CallLogQuery;
import com.android.dialer.database.VoicemailArchiveContract;
import com.android.dialer.util.AsyncTaskExecutor;
import com.android.dialer.util.AsyncTaskExecutors;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.CallLog;
import android.provider.VoicemailContract;
import android.util.Log;
import com.android.common.io.MoreCloseables;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.annotation.Nullable;

/**
 * Class containing asynchronous tasks for voicemails.
 */
@NeededForTesting
public class VoicemailAsyncTaskUtil {
    private static final String TAG = "VoicemailAsyncTaskUtil";

    /** The enumeration of {@link AsyncTask} objects we use in this class. */
    public enum Tasks {
        GET_VOICEMAIL_FILE_PATH,
        SET_VOICEMAIL_ARCHIVE_STATUS,
        ARCHIVE_VOICEMAIL_CONTENT
    }

    @NeededForTesting
    public interface OnArchiveVoicemailListener {
        /**
         * Called after the voicemail has been archived.
         *
         * @param archivedVoicemailUri the URI of the archived voicemail
         */
        void onArchiveVoicemail(@Nullable Uri archivedVoicemailUri);
    }

    @NeededForTesting
    public interface OnSetVoicemailArchiveStatusListener {
        /**
         * Called after the voicemail archived_by_user column is updated.
         *
         * @param success whether the update was successful or not
         */
        void onSetVoicemailArchiveStatus(boolean success);
    }

    @NeededForTesting
    public interface OnGetArchivedVoicemailFilePathListener {
        /**
         * Called after the voicemail file path is obtained.
         *
         * @param filePath the file path of the archived voicemail
         */
        void onGetArchivedVoicemailFilePath(@Nullable String filePath);
    }

    private final ContentResolver mResolver;
    private final AsyncTaskExecutor mAsyncTaskExecutor;

    @NeededForTesting
    public VoicemailAsyncTaskUtil(ContentResolver contentResolver) {
        mResolver = Preconditions.checkNotNull(contentResolver);
        mAsyncTaskExecutor = AsyncTaskExecutors.createThreadPoolExecutor();
    }

    /**
     * Returns the archived voicemail file path.
     */
    @NeededForTesting
    public void getVoicemailFilePath(
            final OnGetArchivedVoicemailFilePathListener listener,
            final Uri voicemailUri) {
        Preconditions.checkNotNull(listener);
        Preconditions.checkNotNull(voicemailUri);
        mAsyncTaskExecutor.submit(Tasks.GET_VOICEMAIL_FILE_PATH,
                new AsyncTask<Void, Void, String>() {
                    @Nullable
                    @Override
                    protected String doInBackground(Void... params) {
                        try (Cursor cursor = mResolver.query(voicemailUri,
                                new String[]{VoicemailArchiveContract.VoicemailArchive._DATA},
                                null, null, null)) {
                            if (hasContent(cursor)) {
                                return cursor.getString(cursor.getColumnIndex(
                                        VoicemailArchiveContract.VoicemailArchive._DATA));
                            }
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(String filePath) {
                        listener.onGetArchivedVoicemailFilePath(filePath);
                    }
                });
    }

    /**
     * Updates the archived_by_user flag of the archived voicemail.
     */
    @NeededForTesting
    public void setVoicemailArchiveStatus(
            final OnSetVoicemailArchiveStatusListener listener,
            final Uri voicemailUri,
            final boolean archivedByUser) {
        Preconditions.checkNotNull(listener);
        Preconditions.checkNotNull(voicemailUri);
        mAsyncTaskExecutor.submit(Tasks.SET_VOICEMAIL_ARCHIVE_STATUS,
                new AsyncTask<Void, Void, Boolean>() {
                    @Override
                    protected Boolean doInBackground(Void... params) {
                        ContentValues values = new ContentValues(1);
                        values.put(VoicemailArchiveContract.VoicemailArchive.ARCHIVED,
                                archivedByUser);
                        return mResolver.update(voicemailUri, values, null, null) > 0;
                    }

                    @Override
                    protected void onPostExecute(Boolean success) {
                        listener.onSetVoicemailArchiveStatus(success);
                    }
                });
    }

    /**
     * Checks if a voicemail has already been archived, if so, return the previously archived URI.
     * Otherwise, copy the voicemail information to the local dialer database. If archive was
     * successful, archived voicemail URI is returned to listener, otherwise null.
     */
    @NeededForTesting
    public void archiveVoicemailContent(
            final OnArchiveVoicemailListener listener,
            final Uri voicemailUri) {
        Preconditions.checkNotNull(listener);
        Preconditions.checkNotNull(voicemailUri);
        mAsyncTaskExecutor.submit(Tasks.ARCHIVE_VOICEMAIL_CONTENT,
                new AsyncTask<Void, Void, Uri>() {
                    @Nullable
                    @Override
                    protected Uri doInBackground(Void... params) {
                        Uri archivedVoicemailUri = getArchivedVoicemailUri(voicemailUri);

                        // If previously archived, return uri, otherwise archive everything.
                        if (archivedVoicemailUri != null) {
                            return archivedVoicemailUri;
                        }

                        // Combine call log and voicemail content info.
                        ContentValues values = getVoicemailContentValues(voicemailUri);
                        if (values == null) {
                            return null;
                        }

                        Uri insertedVoicemailUri = mResolver.insert(
                                VoicemailArchiveContract.VoicemailArchive.CONTENT_URI, values);
                        if (insertedVoicemailUri == null) {
                            return null;
                        }

                        // Copy voicemail content to a new file.
                        boolean copiedFile = false;
                        try (InputStream inputStream = mResolver.openInputStream(voicemailUri);
                             OutputStream outputStream =
                                     mResolver.openOutputStream(insertedVoicemailUri)) {
                            if (inputStream != null && outputStream != null) {
                                ByteStreams.copy(inputStream, outputStream);
                                copiedFile = true;
                                return insertedVoicemailUri;
                            }
                        } catch (IOException e) {
                            Log.w(TAG, "Failed to copy voicemail content to new file: "
                                    + e.toString());
                        } finally {
                            if (!copiedFile) {
                                // Roll back insert if the voicemail content was not copied.
                                mResolver.delete(insertedVoicemailUri, null, null);
                            }
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Uri archivedVoicemailUri) {
                        listener.onArchiveVoicemail(archivedVoicemailUri);
                    }
                });
    }

    /**
     * Helper method to get the archived URI of a voicemail.
     *
     * @param voicemailUri a {@link android.provider.VoicemailContract.Voicemails#CONTENT_URI} URI.
     * @return the URI of the archived voicemail or {@code null}
     */
    @Nullable
    private Uri getArchivedVoicemailUri(Uri voicemailUri) {
        try (Cursor cursor = getArchiveExistsCursor(voicemailUri)) {
            if (hasContent(cursor)) {
                return VoicemailArchiveContract.VoicemailArchive
                        .buildWithId(cursor.getInt(cursor.getColumnIndex(
                                VoicemailArchiveContract.VoicemailArchive._ID)));
            }
        }
        return null;
    }

    /**
     * Helper method to make a copy of all the values needed to display a voicemail.
     *
     * @param voicemailUri a {@link VoicemailContract.Voicemails#CONTENT_URI} URI.
     * @return the combined call log and voicemail values for the given URI, or {@code null}
     */
    @Nullable
    private ContentValues getVoicemailContentValues(Uri voicemailUri) {
        try (Cursor callLogInfo = getCallLogInfoCursor(voicemailUri);
             Cursor contentInfo = getContentInfoCursor(voicemailUri)) {

            if (hasContent(callLogInfo) && hasContent(contentInfo)) {
                // Create values to insert into database.
                ContentValues values = new ContentValues();

                // Insert voicemail call log info.
                values.put(VoicemailArchiveContract.VoicemailArchive.COUNTRY_ISO,
                        callLogInfo.getString(CallLogQuery.COUNTRY_ISO));
                values.put(VoicemailArchiveContract.VoicemailArchive.GEOCODED_LOCATION,
                        callLogInfo.getString(CallLogQuery.GEOCODED_LOCATION));
                values.put(VoicemailArchiveContract.VoicemailArchive.CACHED_NAME,
                        callLogInfo.getString(CallLogQuery.CACHED_NAME));
                values.put(VoicemailArchiveContract.VoicemailArchive.CACHED_NUMBER_TYPE,
                        callLogInfo.getInt(CallLogQuery.CACHED_NUMBER_TYPE));
                values.put(VoicemailArchiveContract.VoicemailArchive.CACHED_NUMBER_LABEL,
                        callLogInfo.getString(CallLogQuery.CACHED_NUMBER_LABEL));
                values.put(VoicemailArchiveContract.VoicemailArchive.CACHED_LOOKUP_URI,
                        callLogInfo.getString(CallLogQuery.CACHED_LOOKUP_URI));
                values.put(VoicemailArchiveContract.VoicemailArchive.CACHED_MATCHED_NUMBER,
                        callLogInfo.getString(CallLogQuery.CACHED_MATCHED_NUMBER));
                values.put(VoicemailArchiveContract.VoicemailArchive.CACHED_NORMALIZED_NUMBER,
                        callLogInfo.getString(CallLogQuery.CACHED_NORMALIZED_NUMBER));
                values.put(VoicemailArchiveContract.VoicemailArchive.CACHED_FORMATTED_NUMBER,
                        callLogInfo.getString(CallLogQuery.CACHED_FORMATTED_NUMBER));
                values.put(VoicemailArchiveContract.VoicemailArchive.NUMBER_PRESENTATION,
                        callLogInfo.getInt(CallLogQuery.NUMBER_PRESENTATION));
                values.put(VoicemailArchiveContract.VoicemailArchive.ACCOUNT_COMPONENT_NAME,
                        callLogInfo.getString(CallLogQuery.ACCOUNT_COMPONENT_NAME));
                values.put(VoicemailArchiveContract.VoicemailArchive.ACCOUNT_ID,
                        callLogInfo.getString(CallLogQuery.ACCOUNT_ID));
                values.put(VoicemailArchiveContract.VoicemailArchive.FEATURES,
                        callLogInfo.getInt(CallLogQuery.FEATURES));
                values.put(VoicemailArchiveContract.VoicemailArchive.CACHED_PHOTO_URI,
                        callLogInfo.getString(CallLogQuery.CACHED_PHOTO_URI));

                // Insert voicemail content info.
                values.put(VoicemailArchiveContract.VoicemailArchive.SERVER_ID,
                        contentInfo.getInt(contentInfo.getColumnIndex(
                                VoicemailContract.Voicemails._ID)));
                values.put(VoicemailArchiveContract.VoicemailArchive.NUMBER,
                        contentInfo.getString(contentInfo.getColumnIndex(
                                VoicemailContract.Voicemails.NUMBER)));
                values.put(VoicemailArchiveContract.VoicemailArchive.DATE,
                        contentInfo.getLong(contentInfo.getColumnIndex(
                                VoicemailContract.Voicemails.DATE)));
                values.put(VoicemailArchiveContract.VoicemailArchive.DURATION,
                        contentInfo.getLong(contentInfo.getColumnIndex(
                                VoicemailContract.Voicemails.DURATION)));
                values.put(VoicemailArchiveContract.VoicemailArchive.MIME_TYPE,
                        contentInfo.getString(contentInfo.getColumnIndex(
                                VoicemailContract.Voicemails.MIME_TYPE)));
                values.put(VoicemailArchiveContract.VoicemailArchive.TRANSCRIPTION,
                        contentInfo.getString(contentInfo.getColumnIndex(
                                VoicemailContract.Voicemails.TRANSCRIPTION)));

                // Achived is false by default because it is updated after insertion.
                values.put(VoicemailArchiveContract.VoicemailArchive.ARCHIVED, false);

                return values;
            }
        }
        return null;
    }

    private boolean hasContent(@Nullable Cursor cursor) {
        return cursor != null && cursor.moveToFirst();
    }

    @Nullable
    private Cursor getCallLogInfoCursor(Uri voicemailUri) {
        return mResolver.query(
                ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI_WITH_VOICEMAIL,
                        ContentUris.parseId(voicemailUri)),
                CallLogQuery._PROJECTION, null, null, null);
    }

    @Nullable
    private Cursor getContentInfoCursor(Uri voicemailUri) {
        return mResolver.query(voicemailUri,
                new String[] {
                        VoicemailContract.Voicemails._ID,
                        VoicemailContract.Voicemails.NUMBER,
                        VoicemailContract.Voicemails.DATE,
                        VoicemailContract.Voicemails.DURATION,
                        VoicemailContract.Voicemails.MIME_TYPE,
                        VoicemailContract.Voicemails.TRANSCRIPTION,
                }, null, null, null);
    }

    @Nullable
    private Cursor getArchiveExistsCursor(Uri voicemailUri) {
        return mResolver.query(VoicemailArchiveContract.VoicemailArchive.CONTENT_URI,
                new String[] {VoicemailArchiveContract.VoicemailArchive._ID},
                VoicemailArchiveContract.VoicemailArchive.SERVER_ID + "="
                        + ContentUris.parseId(voicemailUri),
                null,
                null);
    }
}
