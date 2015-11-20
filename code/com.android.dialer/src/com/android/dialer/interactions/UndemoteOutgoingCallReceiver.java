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

package com.android.dialer.interactions;

import static android.Manifest.permission.READ_CONTACTS;
import static android.Manifest.permission.WRITE_CONTACTS;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.PinnedPositions;
import android.text.TextUtils;

import com.android.contacts.common.util.PermissionsUtil;

/**
 * This broadcast receiver is used to listen to outgoing calls and undemote formerly demoted
 * contacts if a phone call is made to a phone number belonging to that contact.
 *
 * NOTE This doesn't work for corp contacts.
 */
public class UndemoteOutgoingCallReceiver extends BroadcastReceiver {

    private static final long NO_CONTACT_FOUND = -1;

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (!PermissionsUtil.hasPermission(context, READ_CONTACTS)
            || !PermissionsUtil.hasPermission(context, WRITE_CONTACTS)) {
            return;
        }
        if (intent != null && Intent.ACTION_NEW_OUTGOING_CALL.equals(intent.getAction())) {
            final String number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            if (TextUtils.isEmpty(number)) {
                return;
            }
            new Thread() {
                @Override
                public void run() {
                    final long id = getContactIdFromPhoneNumber(context, number);
                    if (id != NO_CONTACT_FOUND) {
                        undemoteContactWithId(context, id);
                    }
                }
            }.start();
        }
    }

    private void undemoteContactWithId(Context context, long id) {
        // If the contact is not demoted, this will not do anything. Otherwise, it will
        // restore it to an unpinned position. If it was a frequently called contact, it will
        // show up once again show up on the favorites screen.
        if (PermissionsUtil.hasPermission(context, WRITE_CONTACTS)) {
            try {
                PinnedPositions.undemote(context.getContentResolver(), id);
            } catch (SecurityException e) {
                // Just in case
            }
        }
    }

    private long getContactIdFromPhoneNumber(Context context, String number) {
        if (!PermissionsUtil.hasPermission(context, READ_CONTACTS)) {
            return NO_CONTACT_FOUND;
        }
        final Uri contactUri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number));
        final Cursor cursor;
        try {
            cursor = context.getContentResolver().query(contactUri, new String[] {
                    PhoneLookup._ID}, null, null, null);
        } catch (SecurityException e) {
            // Just in case
            return NO_CONTACT_FOUND;
        }
        if (cursor == null) {
            return NO_CONTACT_FOUND;
        }
        try {
            if (cursor.moveToFirst()) {
                final long id = cursor.getLong(0);
                return id;
            } else {
                return NO_CONTACT_FOUND;
            }
        } finally {
            cursor.close();
        }
    }
}
