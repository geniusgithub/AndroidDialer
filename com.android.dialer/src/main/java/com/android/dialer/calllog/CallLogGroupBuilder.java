/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.database.Cursor;
import android.provider.CallLog.Calls;
import android.telephony.PhoneNumberUtils;
import android.text.format.Time;

import com.android.contacts.common.util.DateUtils;
import com.android.contacts.common.util.PhoneNumberHelper;

import com.google.common.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * Groups together calls in the call log.  The primary grouping attempts to group together calls
 * to and from the same number into a single row on the call log.
 * A secondary grouping assigns calls, grouped via the primary grouping, to "day groups".  The day
 * groups provide a means of identifying the calls which occurred "Today", "Yesterday", "Last week",
 * or "Other".
 * <p>
 * This class is meant to be used in conjunction with {@link GroupingListAdapter}.
 */
public class CallLogGroupBuilder {
    public interface GroupCreator {

        /**
         * Defines the interface for adding a group to the call log.
         * The primary group for a call log groups the calls together based on the number which was
         * dialed.
         * @param cursorPosition The starting position of the group in the cursor.
         * @param size The size of the group.
         * @param expanded Whether the group is expanded; always false for the call log.
         */
        public void addGroup(int cursorPosition, int size, boolean expanded);

        /**
         * Defines the interface for tracking the day group each call belongs to.  Calls in a call
         * group are assigned the same day group as the first call in the group.  The day group
         * assigns calls to the buckets: Today, Yesterday, Last week, and Other
         *
         * @param rowId The row Id of the current call.
         * @param dayGroup The day group the call belongs in.
         */
        public void setDayGroup(long rowId, int dayGroup);

        /**
         * Defines the interface for clearing the day groupings information on rebind/regroup.
         */
        public void clearDayGroups();
    }

    /**
     * Day grouping for call log entries used to represent no associated day group.  Used primarily
     * when retrieving the previous day group, but there is no previous day group (i.e. we are at
     * the start of the list).
     */
    public static final int DAY_GROUP_NONE = -1;

    /** Day grouping for calls which occurred today. */
    public static final int DAY_GROUP_TODAY = 0;

    /** Day grouping for calls which occurred yesterday. */
    public static final int DAY_GROUP_YESTERDAY = 1;

    /** Day grouping for calls which occurred before last week. */
    public static final int DAY_GROUP_OTHER = 2;

    /** Instance of the time object used for time calculations. */
    private static final Time TIME = new Time();

    /** The object on which the groups are created. */
    private final GroupCreator mGroupCreator;

    public CallLogGroupBuilder(GroupCreator groupCreator) {
        mGroupCreator = groupCreator;
    }

    /**
     * Finds all groups of adjacent entries in the call log which should be grouped together and
     * calls {@link GroupCreator#addGroup(int, int, boolean)} on {@link #mGroupCreator} for each of
     * them.
     * <p>
     * For entries that are not grouped with others, we do not need to create a group of size one.
     * <p>
     * It assumes that the cursor will not change during its execution.
     *
     * @see GroupingListAdapter#addGroups(Cursor)
     */
    public void addGroups(Cursor cursor) {
        final int count = cursor.getCount();
        if (count == 0) {
            return;
        }

        // Clear any previous day grouping information.
        mGroupCreator.clearDayGroups();

        // Get current system time, used for calculating which day group calls belong to.
        long currentTime = System.currentTimeMillis();

        int currentGroupSize = 1;
        cursor.moveToFirst();
        // The number of the first entry in the group.
        String firstNumber = cursor.getString(CallLogQuery.NUMBER);
        // This is the type of the first call in the group.
        int firstCallType = cursor.getInt(CallLogQuery.CALL_TYPE);

        // The account information of the first entry in the group.
        String firstAccountComponentName = cursor.getString(CallLogQuery.ACCOUNT_COMPONENT_NAME);
        String firstAccountId = cursor.getString(CallLogQuery.ACCOUNT_ID);

        // Determine the day group for the first call in the cursor.
        final long firstDate = cursor.getLong(CallLogQuery.DATE);
        final long firstRowId = cursor.getLong(CallLogQuery.ID);
        int currentGroupDayGroup = getDayGroup(firstDate, currentTime);
        mGroupCreator.setDayGroup(firstRowId, currentGroupDayGroup);

        while (cursor.moveToNext()) {
            // The number of the current row in the cursor.
            final String currentNumber = cursor.getString(CallLogQuery.NUMBER);
            final int callType = cursor.getInt(CallLogQuery.CALL_TYPE);
            final String currentAccountComponentName = cursor.getString(
                    CallLogQuery.ACCOUNT_COMPONENT_NAME);
            final String currentAccountId = cursor.getString(CallLogQuery.ACCOUNT_ID);

            final boolean sameNumber = equalNumbers(firstNumber, currentNumber);
            final boolean sameAccountComponentName = Objects.equals(
                    firstAccountComponentName,
                    currentAccountComponentName);
            final boolean sameAccountId = Objects.equals(
                    firstAccountId,
                    currentAccountId);
            final boolean sameAccount = sameAccountComponentName && sameAccountId;

            final boolean shouldGroup;
            final long currentCallId = cursor.getLong(CallLogQuery.ID);
            final long date = cursor.getLong(CallLogQuery.DATE);

            if (!sameNumber || !sameAccount) {
                // Should only group with calls from the same number.
                shouldGroup = false;
            } else if (firstCallType == Calls.VOICEMAIL_TYPE) {
                // never group voicemail.
                shouldGroup = false;
            } else {
                // Incoming, outgoing, and missed calls group together.
                shouldGroup = callType != Calls.VOICEMAIL_TYPE;
            }

            if (shouldGroup) {
                // Increment the size of the group to include the current call, but do not create
                // the group until we find a call that does not match.
                currentGroupSize++;
            } else {
                // The call group has changed, so determine the day group for the new call group.
                // This ensures all calls grouped together in the call log are assigned the same
                // day group.
                currentGroupDayGroup = getDayGroup(date, currentTime);

                // Create a group for the previous set of calls, excluding the current one, but do
                // not create a group for a single call.
                if (currentGroupSize > 1) {
                    addGroup(cursor.getPosition() - currentGroupSize, currentGroupSize);
                }
                // Start a new group; it will include at least the current call.
                currentGroupSize = 1;
                // The current entry is now the first in the group.
                firstNumber = currentNumber;
                firstCallType = callType;
                firstAccountComponentName = currentAccountComponentName;
                firstAccountId = currentAccountId;
            }

            // Save the day group associated with the current call.
            mGroupCreator.setDayGroup(currentCallId, currentGroupDayGroup);
        }
        // If the last set of calls at the end of the call log was itself a group, create it now.
        if (currentGroupSize > 1) {
            addGroup(count - currentGroupSize, currentGroupSize);
        }
    }

    /**
     * Creates a group of items in the cursor.
     * <p>
     * The group is always unexpanded.
     *
     * @see CallLogAdapter#addGroup(int, int, boolean)
     */
    private void addGroup(int cursorPosition, int size) {
        mGroupCreator.addGroup(cursorPosition, size, false);
    }

    @VisibleForTesting
    boolean equalNumbers(String number1, String number2) {
        if (PhoneNumberHelper.isUriNumber(number1) || PhoneNumberHelper.isUriNumber(number2)) {
            return compareSipAddresses(number1, number2);
        } else {
            return PhoneNumberUtils.compare(number1, number2);
        }
    }

    @VisibleForTesting
    boolean compareSipAddresses(String number1, String number2) {
        if (number1 == null || number2 == null) return number1 == number2;

        int index1 = number1.indexOf('@');
        final String userinfo1;
        final String rest1;
        if (index1 != -1) {
            userinfo1 = number1.substring(0, index1);
            rest1 = number1.substring(index1);
        } else {
            userinfo1 = number1;
            rest1 = "";
        }

        int index2 = number2.indexOf('@');
        final String userinfo2;
        final String rest2;
        if (index2 != -1) {
            userinfo2 = number2.substring(0, index2);
            rest2 = number2.substring(index2);
        } else {
            userinfo2 = number2;
            rest2 = "";
        }

        return userinfo1.equals(userinfo2) && rest1.equalsIgnoreCase(rest2);
    }

    /**
     * Given a call date and the current date, determine which date group the call belongs in.
     *
     * @param date The call date.
     * @param now The current date.
     * @return The date group the call belongs in.
     */
    private int getDayGroup(long date, long now) {
        int days = DateUtils.getDayDifference(TIME, date, now);

        if (days == 0) {
            return DAY_GROUP_TODAY;
        } else if (days == 1) {
            return DAY_GROUP_YESTERDAY;
        } else {
            return DAY_GROUP_OTHER;
        }
    }
}
