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

package com.android.dialer.filterednumber;

import com.google.common.base.Preconditions;

import android.content.SharedPreferences;
import android.util.Log;

import com.android.dialer.compat.FilteredNumberCompat;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler.OnHasBlockedNumbersListener;

/**
 * Class responsible for checking if the user can be auto-migrated to {@link
 * android.provider.BlockedNumberContract} blocking. In order for this to happen, the user cannot
 * have any numbers that are blocked in the Dialer solution.
 */
public class BlockedNumbersAutoMigrator {

    private static final String TAG = "BlockedNumbersAuto";

    private static final String HAS_CHECKED_AUTO_MIGRATE_KEY = "checkedAutoMigrate";

    private final SharedPreferences sharedPreferences;
    private final FilteredNumberAsyncQueryHandler queryHandler;

    /**
     * Constructs the BlockedNumbersAutoMigrator with the given {@link SharedPreferences} and {@link
     * FilteredNumberAsyncQueryHandler}.
     *
     * @param sharedPreferences The SharedPreferences used to persist information.
     * @param queryHandler The FilteredNumberAsyncQueryHandler used to determine if there are
     * blocked numbers.
     * @throws NullPointerException if sharedPreferences or queryHandler are null.
     */
    public BlockedNumbersAutoMigrator(SharedPreferences sharedPreferences,
            FilteredNumberAsyncQueryHandler queryHandler) {
        this.sharedPreferences = Preconditions.checkNotNull(sharedPreferences);
        this.queryHandler = Preconditions.checkNotNull(queryHandler);
    }

    /**
     * Attempts to perform the auto-migration. Auto-migration will only be attempted once and can be
     * performed only when the user has no blocked numbers. As a result of this method, the user
     * will be migrated to the framework blocking solution, as determined by {@link
     * FilteredNumberCompat#hasMigratedToNewBlocking()}.
     */
    public void autoMigrate() {
        if (!shouldAttemptAutoMigrate()) {
            return;
        }

        Log.i(TAG, "Attempting to auto-migrate.");
        queryHandler.hasBlockedNumbers(new OnHasBlockedNumbersListener() {
            @Override
            public void onHasBlockedNumbers(boolean hasBlockedNumbers) {
                if (hasBlockedNumbers) {
                    Log.i(TAG, "Not auto-migrating: blocked numbers exist.");
                    return;
                }
                Log.i(TAG, "Auto-migrating: no blocked numbers.");
                FilteredNumberCompat.setHasMigratedToNewBlocking(true);
            }
        });
    }

    private boolean shouldAttemptAutoMigrate() {
        if (sharedPreferences.contains(HAS_CHECKED_AUTO_MIGRATE_KEY)) {
            Log.d(TAG, "Not attempting auto-migrate: already checked once.");
            return false;
        }
        Log.i(TAG, "Updating state as already checked for auto-migrate.");
        sharedPreferences.edit().putBoolean(HAS_CHECKED_AUTO_MIGRATE_KEY, true).apply();

        if (!FilteredNumberCompat.canUseNewFiltering()) {
            Log.i(TAG, "Not attempting auto-migrate: not available.");
            return false;
        }

        if (FilteredNumberCompat.hasMigratedToNewBlocking()) {
            Log.i(TAG, "Not attempting auto-migrate: already migrated.");
            return false;
        }
        return true;
    }
}
