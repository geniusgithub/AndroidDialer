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

package com.android.dialer.compat;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

import android.app.FragmentManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.compat.TelecomManagerUtil;
import com.android.contacts.common.testing.NeededForTesting;
import com.android.dialer.DialerApplication;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler.OnCheckBlockedListener;
import com.android.dialer.database.FilteredNumberContract.FilteredNumber;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberColumns;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberSources;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberTypes;
import com.android.dialer.filterednumber.BlockNumberDialogFragment;
import com.android.dialer.filterednumber.BlockNumberDialogFragment.Callback;
import com.android.dialer.filterednumber.BlockedNumbersMigrator;
import com.android.dialer.filterednumber.BlockedNumbersSettingsActivity;
import com.android.dialer.filterednumber.MigrateBlockedNumbersDialogFragment;
import com.android.dialerbind.ObjectFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Compatibility class to encapsulate logic to switch between call blocking using
 * {@link com.android.dialer.database.FilteredNumberContract} and using
 * {@link android.provider.BlockedNumberContract}. This class should be used rather than explicitly
 * referencing columns from either contract class in situations where both blocking solutions may be
 * used.
 */
public class FilteredNumberCompat {

    private static final String TAG = "FilteredNumberCompat";

    protected static final String HAS_MIGRATED_TO_NEW_BLOCKING_KEY = "migratedToNewBlocking";

    private static Boolean isEnabledForTest;

    private static Context contextForTest;

    /**
     * @return The column name for ID in the filtered number database.
     */
    public static String getIdColumnName() {
        return useNewFiltering() ? BlockedNumbersSdkCompat._ID : FilteredNumberColumns._ID;
    }

    /**
     * @return The column name for type in the filtered number database. Will be {@code null} for
     * the framework blocking implementation.
     */
    @Nullable
    public static String getTypeColumnName() {
        return useNewFiltering() ? null : FilteredNumberColumns.TYPE;
    }

    /**
     * @return The column name for source in the filtered number database. Will be {@code null} for
     * the framework blocking implementation
     */
    @Nullable
    public static String getSourceColumnName() {
        return useNewFiltering() ? null : FilteredNumberColumns.SOURCE;
    }

    /**
     * @return The column name for the original number in the filtered number database.
     */
    public static String getOriginalNumberColumnName() {
        return useNewFiltering() ? BlockedNumbersSdkCompat.COLUMN_ORIGINAL_NUMBER
                : FilteredNumberColumns.NUMBER;
    }

    /**
     * @return The column name for country iso in the filtered number database. Will be {@code null}
     * the framework blocking implementation
     */
    @Nullable
    public static String getCountryIsoColumnName() {
        return useNewFiltering() ? null : FilteredNumberColumns.COUNTRY_ISO;
    }

    /**
     * @return The column name for the e164 formatted number in the filtered number database.
     */
    public static String getE164NumberColumnName() {
        return useNewFiltering() ? BlockedNumbersSdkCompat.E164_NUMBER
                : FilteredNumberColumns.NORMALIZED_NUMBER;
    }

    /**
     * @return {@code true} if the current SDK version supports using new filtering, {@code false}
     * otherwise.
     */
    public static boolean canUseNewFiltering() {
        if (isEnabledForTest != null) {
            return CompatUtils.isNCompatible() && isEnabledForTest;
        }
        return CompatUtils.isNCompatible() && ObjectFactory
                .isNewBlockingEnabled(DialerApplication.getContext());
    }

    /**
     * @return {@code true} if the new filtering should be used, i.e. it's enabled and any necessary
     * migration has been performed, {@code false} otherwise.
     */
    public static boolean useNewFiltering() {
        return canUseNewFiltering() && hasMigratedToNewBlocking();
    }

    /**
     * @return {@code true} if the user has migrated to use
     * {@link android.provider.BlockedNumberContract} blocking, {@code false} otherwise.
     */
    public static boolean hasMigratedToNewBlocking() {
        return PreferenceManager.getDefaultSharedPreferences(DialerApplication.getContext())
                .getBoolean(HAS_MIGRATED_TO_NEW_BLOCKING_KEY, false);
    }

    /**
     * Called to inform this class whether the user has fully migrated to use
     * {@link android.provider.BlockedNumberContract} blocking or not.
     *
     * @param hasMigrated {@code true} if the user has migrated, {@code false} otherwise.
     */
    @NeededForTesting
    public static void setHasMigratedToNewBlocking(boolean hasMigrated) {
        PreferenceManager.getDefaultSharedPreferences(
                MoreObjects.firstNonNull(contextForTest, DialerApplication.getContext())).edit()
                .putBoolean(HAS_MIGRATED_TO_NEW_BLOCKING_KEY, hasMigrated).apply();
    }

    @NeededForTesting
    public static void setIsEnabledForTest(Boolean isEnabled) {
        isEnabledForTest = isEnabled;
    }

    @NeededForTesting
    public static void setContextForTest(Context context) {
        contextForTest = context;
    }

    /**
     * Gets the content {@link Uri} for number filtering.
     *
     * @param id The optional id to append with the base content uri.
     * @return The Uri for number filtering.
     */
    public static Uri getContentUri(@Nullable Integer id) {
        if (id == null) {
            return getBaseUri();
        }
        return ContentUris.withAppendedId(getBaseUri(), id);
    }


    private static Uri getBaseUri() {
        return useNewFiltering() ? BlockedNumbersSdkCompat.CONTENT_URI : FilteredNumber.CONTENT_URI;
    }

    /**
     * Removes any null column names from the given projection array. This method is intended to be
     * used to strip out any column names that aren't available in every version of number blocking.
     * Example:
     * {@literal
     *   getContext().getContentResolver().query(
     *       someUri,
     *       // Filtering ensures that no non-existant columns are queried
     *       FilteredNumberCompat.filter(new String[] {FilteredNumberCompat.getIdColumnName(),
     *           FilteredNumberCompat.getTypeColumnName()},
     *       FilteredNumberCompat.getE164NumberColumnName() + " = ?",
     *       new String[] {e164Number});
     * }
     *
     * @param projection The projection array.
     * @return The filtered projection array.
     */
    @Nullable
    public static String[] filter(@Nullable String[] projection) {
        if (projection == null) {
            return null;
        }
        List<String> filtered = new ArrayList<>();
        for (String column : projection) {
            if (column != null) {
                filtered.add(column);
            }
        }
        return filtered.toArray(new String[filtered.size()]);
    }

    /**
     * Creates a new {@link ContentValues} suitable for inserting in the filtered number table.
     *
     * @param number The unformatted number to insert.
     * @param e164Number (optional) The number to insert formatted to E164 standard.
     * @param countryIso (optional) The country iso to use to format the number.
     * @return The ContentValues to insert.
     * @throws NullPointerException If number is null.
     */
    public static ContentValues newBlockNumberContentValues(String number,
            @Nullable String e164Number, @Nullable String countryIso) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(getOriginalNumberColumnName(), Preconditions.checkNotNull(number));
        if (!useNewFiltering()) {
            if (e164Number == null) {
                e164Number = PhoneNumberUtils.formatNumberToE164(number, countryIso);
            }
            contentValues.put(getE164NumberColumnName(), e164Number);
            contentValues.put(getCountryIsoColumnName(), countryIso);
            contentValues.put(getTypeColumnName(), FilteredNumberTypes.BLOCKED_NUMBER);
            contentValues.put(getSourceColumnName(), FilteredNumberSources.USER);
        }
        return contentValues;
    }

    /**
     * Shows the flow of {@link android.app.DialogFragment}s for blocking or unblocking numbers.
     *
     * @param blockId The id into the blocked numbers database.
     * @param number The number to block or unblock.
     * @param countryIso The countryIso used to format the given number.
     * @param displayNumber The form of the number to block, suitable for displaying.
     * @param parentViewId The id for the containing view of the Dialog.
     * @param fragmentManager The {@link FragmentManager} used to show fragments.
     * @param callback (optional) The {@link Callback} to call when the block or unblock operation
     * is complete.
     */
    public static void showBlockNumberDialogFlow(final ContentResolver contentResolver,
            final Integer blockId, final String number, final String countryIso,
            final String displayNumber, final Integer parentViewId,
            final FragmentManager fragmentManager, @Nullable final Callback callback) {
        Log.i(TAG, "showBlockNumberDialogFlow - start");
        // If the user is blocking a number and isn't using the framework solution when they
        // should be, show the migration dialog
        if (shouldShowMigrationDialog(blockId == null)) {
            Log.i(TAG, "showBlockNumberDialogFlow - showing migration dialog");
            MigrateBlockedNumbersDialogFragment
                    .newInstance(new BlockedNumbersMigrator(contentResolver), newMigrationListener(
                            DialerApplication.getContext().getContentResolver(), number, countryIso,
                            displayNumber, parentViewId, fragmentManager, callback))
                    .show(fragmentManager, "MigrateBlockedNumbers");
            return;
        }
        Log.i(TAG, "showBlockNumberDialogFlow - showing block number dialog");
        BlockNumberDialogFragment
                .show(blockId, number, countryIso, displayNumber, parentViewId, fragmentManager,
                        callback);
    }

    private static boolean shouldShowMigrationDialog(boolean isBlocking) {
        return isBlocking && canUseNewFiltering() && !hasMigratedToNewBlocking();
    }

    private static BlockedNumbersMigrator.Listener newMigrationListener(
            final ContentResolver contentResolver, final String number, final String countryIso,
            final String displayNumber, final Integer parentViewId,
            final FragmentManager fragmentManager, @Nullable final Callback callback) {
        return new BlockedNumbersMigrator.Listener() {
            @Override
            public void onComplete() {
                Log.i(TAG, "showBlockNumberDialogFlow - listener showing block number dialog");
                if (!hasMigratedToNewBlocking()) {
                    Log.i(TAG, "showBlockNumberDialogFlow - migration failed");
                    return;
                }
                /*
                 * Edge case to cover here: if the user initiated the migration workflow with a
                 * number that's already blocked in the framework, don't show the block number
                 * dialog. Doing so would allow them to block the same number twice, causing a
                 * crash.
                 */
                new FilteredNumberAsyncQueryHandler(contentResolver).isBlockedNumber(
                        new OnCheckBlockedListener() {
                            @Override
                            public void onCheckComplete(Integer id) {
                                if (id != null) {
                                    Log.i(TAG,
                                            "showBlockNumberDialogFlow - number already blocked");
                                    return;
                                }
                                Log.i(TAG, "showBlockNumberDialogFlow - need to block number");
                                BlockNumberDialogFragment
                                        .show(null, number, countryIso, displayNumber, parentViewId,
                                                fragmentManager, callback);
                            }
                        }, number, countryIso);
            }
        };
    }

    /**
     * Creates the {@link Intent} which opens the blocked numbers management interface.
     *
     * @param context The {@link Context}.
     * @return The intent.
     */
    public static Intent createManageBlockedNumbersIntent(Context context) {
        if (canUseNewFiltering() && hasMigratedToNewBlocking()) {
            return TelecomManagerUtil.createManageBlockedNumbersIntent(
                    (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE));
        }
        return new Intent(context, BlockedNumbersSettingsActivity.class);
    }

    /**
     * Method used to determine if block operations are possible.
     *
     * @param context The {@link Context}.
     * @return {@code true} if the app and user can block numbers, {@code false} otherwise.
     */
    public static boolean canAttemptBlockOperations(Context context) {
        if (!CompatUtils.isNCompatible()) {
            // Dialer blocking, must be primary user
            return UserManagerCompat.isSystemUser(
                    (UserManager) context.getSystemService(Context.USER_SERVICE));
        }

        // Great Wall blocking, must be primary user and the default or system dialer
        // TODO(maxwelb): check that we're the default or system Dialer
        return BlockedNumbersSdkCompat.canCurrentUserBlockNumbers(context);
    }

    /**
     * Used to determine if the call blocking settings can be opened.
     *
     * @param context The {@link Context}.
     * @return {@code true} if the current user can open the call blocking settings, {@code false}
     * otherwise.
     */
    public static boolean canCurrentUserOpenBlockSettings(Context context) {
        if (!CompatUtils.isNCompatible()) {
            // Dialer blocking, must be primary user
            return UserManagerCompat.isSystemUser(
                    (UserManager) context.getSystemService(Context.USER_SERVICE));
        }
        // BlockedNumberContract blocking, verify through Contract API
        return BlockedNumbersSdkCompat.canCurrentUserBlockNumbers(context);
    }
}
