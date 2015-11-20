/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.dialer.contact;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import com.android.contacts.common.database.ContactUpdateUtils;

/**
 * Service for updating primary number on a contact.
 */
public class ContactUpdateService extends IntentService {

    public static final String EXTRA_PHONE_NUMBER_DATA_ID = "phone_number_data_id";

    public ContactUpdateService() {
        super(ContactUpdateService.class.getSimpleName());
        setIntentRedelivery(true);
    }

    /** Creates an intent that sets the selected data item as super primary (default) */
    public static Intent createSetSuperPrimaryIntent(Context context, long dataId) {
        Intent serviceIntent = new Intent(context, ContactUpdateService.class);
        serviceIntent.putExtra(EXTRA_PHONE_NUMBER_DATA_ID, dataId);
        return serviceIntent;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // Currently this service only handles one type of update.
        long dataId = intent.getLongExtra(EXTRA_PHONE_NUMBER_DATA_ID, -1);

        ContactUpdateUtils.setSuperPrimary(this, dataId);
    }
}
