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

package com.android.dialer.logging;

/**
 * Class holding constants for Dialer interactions
 */
public class InteractionEvent {

    public static final int UNKNOWN = 0;

    /**
     * An incoming call was blocked
     */
    public static final int CALL_BLOCKED = 15;

    /**
     * The user blocked a number from the Call Log screen
     */
    public static final int BLOCK_NUMBER_CALL_LOG = 16;

    /**
     * The user blocked a number from the Call details screen
     */
    public static final int BLOCK_NUMBER_CALL_DETAIL = 17;

    /**
     * The user blocked a number from the Management screen
     */
    public static final int BLOCK_NUMBER_MANAGEMENT_SCREEN = 18;

    /**
     * The user unblocked a number from the Call Log screen
     */
    public static final int UNBLOCK_NUMBER_CALL_LOG = 19;

    /**
     * The user unblocked a number from the Call details screen
     */
    public static final int UNBLOCK_NUMBER_CALL_DETAIL = 20;

    /**
     * The user unblocked a number from the Management screen
     */
    public static final int UNBLOCK_NUMBER_MANAGEMENT_SCREEN = 21;

    /**
     * The user blocked numbers from contacts marked as send to voicemail
     */
    public static final int IMPORT_SEND_TO_VOICEMAIL = 22;

    /**
     * The user blocked a number then undid the block
     */
    public static final int UNDO_BLOCK_NUMBER = 23;

    /**
     * The user unblocked a number then undid the unblock
     */
    public static final int UNDO_UNBLOCK_NUMBER = 24;

}
