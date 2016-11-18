/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.dialer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.KeyguardManager;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Looper;
import android.provider.Settings;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import com.android.common.io.MoreCloseables;
import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.compat.TelephonyManagerCompat;
import com.android.contacts.common.database.NoNullCursorAsyncQueryHandler;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment;
import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment.SelectPhoneAccountListener;
import com.android.dialer.calllog.PhoneAccountUtils;
import com.android.dialer.util.TelecomUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to listen for some magic character sequences
 * that are handled specially by the dialer.
 *
 * Note the Phone app also handles these sequences too (in a couple of
 * relatively obscure places in the UI), so there's a separate version of
 * this class under apps/Phone.
 *
 * TODO: there's lots of duplicated code between this class and the
 * corresponding class under apps/Phone.  Let's figure out a way to
 * unify these two classes (in the framework? in a common shared library?)
 */
public class SpecialCharSequenceMgr {
    private static final String TAG = "SpecialCharSequenceMgr";

    private static final String TAG_SELECT_ACCT_FRAGMENT = "tag_select_acct_fragment";

    private static final String SECRET_CODE_ACTION = "android.provider.Telephony.SECRET_CODE";
    private static final String MMI_IMEI_DISPLAY = "*#06#";
    private static final String MMI_REGULATORY_INFO_DISPLAY = "*#07#";

    /**
     * Remembers the previous {@link QueryHandler} and cancel the operation when needed, to
     * prevent possible crash.
     *
     * QueryHandler may call {@link ProgressDialog#dismiss()} when the screen is already gone,
     * which will cause the app crash. This variable enables the class to prevent the crash
     * on {@link #cleanup()}.
     *
     * TODO: Remove this and replace it (and {@link #cleanup()}) with better implementation.
     * One complication is that we have SpecialCharSequenceMgr in Phone package too, which has
     * *slightly* different implementation. Note that Phone package doesn't have this problem,
     * so the class on Phone side doesn't have this functionality.
     * Fundamental fix would be to have one shared implementation and resolve this corner case more
     * gracefully.
     */
    private static QueryHandler sPreviousAdnQueryHandler;

    public static class HandleAdnEntryAccountSelectedCallback extends SelectPhoneAccountListener{
        final private Context mContext;
        final private QueryHandler mQueryHandler;
        final private SimContactQueryCookie mCookie;

        public HandleAdnEntryAccountSelectedCallback(Context context,
                QueryHandler queryHandler, SimContactQueryCookie cookie) {
            mContext = context;
            mQueryHandler = queryHandler;
            mCookie = cookie;
        }

        @Override
        public void onPhoneAccountSelected(PhoneAccountHandle selectedAccountHandle,
                boolean setDefault) {
            Uri uri = TelecomUtil.getAdnUriForPhoneAccount(mContext, selectedAccountHandle);
            handleAdnQuery(mQueryHandler, mCookie, uri);
            // TODO: Show error dialog if result isn't valid.
        }

    }

    public static class HandleMmiAccountSelectedCallback extends SelectPhoneAccountListener{
        final private Context mContext;
        final private String mInput;
        public HandleMmiAccountSelectedCallback(Context context, String input) {
            mContext = context.getApplicationContext();
            mInput = input;
        }

        @Override
        public void onPhoneAccountSelected(PhoneAccountHandle selectedAccountHandle,
                boolean setDefault) {
            TelecomUtil.handleMmi(mContext, mInput, selectedAccountHandle);
        }
    }

    /** This class is never instantiated. */
    private SpecialCharSequenceMgr() {
    }

    public static boolean handleChars(Context context, String input, EditText textField) {
        //get rid of the separators so that the string gets parsed correctly
        String dialString = PhoneNumberUtils.stripSeparators(input);

        if (handleDeviceIdDisplay(context, dialString)
                || handleRegulatoryInfoDisplay(context, dialString)
                || handlePinEntry(context, dialString)
                || handleAdnEntry(context, dialString, textField)
                || handleSecretCode(context, dialString)) {
            return true;
        }

        return false;
    }

    /**
     * Cleanup everything around this class. Must be run inside the main thread.
     *
     * This should be called when the screen becomes background.
     */
    public static void cleanup() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.wtf(TAG, "cleanup() is called outside the main thread");
            return;
        }

        if (sPreviousAdnQueryHandler != null) {
            sPreviousAdnQueryHandler.cancel();
            sPreviousAdnQueryHandler = null;
        }
    }

    /**
     * Handles secret codes to launch arbitrary activities in the form of *#*#<code>#*#*.
     * If a secret code is encountered an Intent is started with the android_secret_code://<code>
     * URI.
     *
     * @param context the context to use
     * @param input the text to check for a secret code in
     * @return true if a secret code was encountered
     */
    static boolean handleSecretCode(Context context, String input) {
        // Secret codes are in the form *#*#<code>#*#*
        int len = input.length();
        if (len > 8 && input.startsWith("*#*#") && input.endsWith("#*#*")) {
            final Intent intent = new Intent(SECRET_CODE_ACTION,
                    Uri.parse("android_secret_code://" + input.substring(4, len - 4)));
            context.sendBroadcast(intent);
            return true;
        }

        return false;
    }

    /**
     * Handle ADN requests by filling in the SIM contact number into the requested
     * EditText.
     *
     * This code works alongside the Asynchronous query handler {@link QueryHandler}
     * and query cancel handler implemented in {@link SimContactQueryCookie}.
     */
    static boolean handleAdnEntry(Context context, String input, EditText textField) {
        /* ADN entries are of the form "N(N)(N)#" */
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager == null
                || telephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_GSM) {
            return false;
        }

        // if the phone is keyguard-restricted, then just ignore this
        // input.  We want to make sure that sim card contacts are NOT
        // exposed unless the phone is unlocked, and this code can be
        // accessed from the emergency dialer.
        KeyguardManager keyguardManager =
                (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager.inKeyguardRestrictedInputMode()) {
            return false;
        }

        int len = input.length();
        if ((len > 1) && (len < 5) && (input.endsWith("#"))) {
            try {
                // get the ordinal number of the sim contact
                final int index = Integer.parseInt(input.substring(0, len-1));

                // The original code that navigated to a SIM Contacts list view did not
                // highlight the requested contact correctly, a requirement for PTCRB
                // certification.  This behaviour is consistent with the UI paradigm
                // for touch-enabled lists, so it does not make sense to try to work
                // around it.  Instead we fill in the the requested phone number into
                // the dialer text field.

                // create the async query handler
                final QueryHandler handler = new QueryHandler(context.getContentResolver());

                // create the cookie object
                final SimContactQueryCookie sc = new SimContactQueryCookie(index - 1, handler,
                        ADN_QUERY_TOKEN);

                // setup the cookie fields
                sc.contactNum = index - 1;
                sc.setTextField(textField);

                // create the progress dialog
                sc.progressDialog = new ProgressDialog(context);
                sc.progressDialog.setTitle(R.string.simContacts_title);
                sc.progressDialog.setMessage(context.getText(R.string.simContacts_emptyLoading));
                sc.progressDialog.setIndeterminate(true);
                sc.progressDialog.setCancelable(true);
                sc.progressDialog.setOnCancelListener(sc);
                sc.progressDialog.getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_BLUR_BEHIND);

                List<PhoneAccountHandle> subscriptionAccountHandles =
                        PhoneAccountUtils.getSubscriptionPhoneAccounts(context);
                Context applicationContext = context.getApplicationContext();
                boolean hasUserSelectedDefault = subscriptionAccountHandles.contains(
                        TelecomUtil.getDefaultOutgoingPhoneAccount(applicationContext,
                                PhoneAccount.SCHEME_TEL));

                if (subscriptionAccountHandles.size() <= 1 || hasUserSelectedDefault) {
                    Uri uri = TelecomUtil.getAdnUriForPhoneAccount(applicationContext, null);
                    handleAdnQuery(handler, sc, uri);
                } else {
                    SelectPhoneAccountListener callback = new HandleAdnEntryAccountSelectedCallback(
                            applicationContext, handler, sc);

                    DialogFragment dialogFragment = SelectPhoneAccountDialogFragment.newInstance(
                            subscriptionAccountHandles, callback);
                    dialogFragment.show(((Activity) context).getFragmentManager(),
                            TAG_SELECT_ACCT_FRAGMENT);
                }

                return true;
            } catch (NumberFormatException ex) {
                // Ignore
            }
        }
        return false;
    }

    private static void handleAdnQuery(QueryHandler handler, SimContactQueryCookie cookie,
            Uri uri) {
        if (handler == null || cookie == null || uri == null) {
            Log.w(TAG, "queryAdn parameters incorrect");
            return;
        }

        // display the progress dialog
        cookie.progressDialog.show();

        // run the query.
        handler.startQuery(ADN_QUERY_TOKEN, cookie, uri, new String[]{ADN_PHONE_NUMBER_COLUMN_NAME},
                null, null, null);

        if (sPreviousAdnQueryHandler != null) {
            // It is harmless to call cancel() even after the handler's gone.
            sPreviousAdnQueryHandler.cancel();
        }
        sPreviousAdnQueryHandler = handler;
    }

    static boolean handlePinEntry(final Context context, final String input) {
        if ((input.startsWith("**04") || input.startsWith("**05")) && input.endsWith("#")) {
            List<PhoneAccountHandle> subscriptionAccountHandles =
                    PhoneAccountUtils.getSubscriptionPhoneAccounts(context);
            boolean hasUserSelectedDefault = subscriptionAccountHandles.contains(
                    TelecomUtil.getDefaultOutgoingPhoneAccount(context, PhoneAccount.SCHEME_TEL));

            if (subscriptionAccountHandles.size() <= 1 || hasUserSelectedDefault) {
                // Don't bring up the dialog for single-SIM or if the default outgoing account is
                // a subscription account.
                return TelecomUtil.handleMmi(context, input, null);
            } else {
                SelectPhoneAccountListener listener =
                        new HandleMmiAccountSelectedCallback(context, input);

                DialogFragment dialogFragment = SelectPhoneAccountDialogFragment.newInstance(
                        subscriptionAccountHandles, listener);
                dialogFragment.show(((Activity) context).getFragmentManager(),
                        TAG_SELECT_ACCT_FRAGMENT);
            }
            return true;
        }
        return false;
    }

    // TODO: Use TelephonyCapabilities.getDeviceIdLabel() to get the device id label instead of a
    // hard-coded string.
    static boolean handleDeviceIdDisplay(Context context, String input) {
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        if (telephonyManager != null && input.equals(MMI_IMEI_DISPLAY)) {
            int labelResId = (telephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) ?
                    R.string.imei : R.string.meid;

            List<String> deviceIds = new ArrayList<String>();
            if (TelephonyManagerCompat.getPhoneCount(telephonyManager) > 1 &&
                    CompatUtils.isMethodAvailable(TelephonyManagerCompat.TELEPHONY_MANAGER_CLASS,
                            "getDeviceId", Integer.TYPE)) {
                for (int slot = 0; slot < telephonyManager.getPhoneCount(); slot++) {
                    String deviceId = telephonyManager.getDeviceId(slot);
                    if (!TextUtils.isEmpty(deviceId)) {
                        deviceIds.add(deviceId);
                    }
                }
            } else {
                deviceIds.add(telephonyManager.getDeviceId());
            }

            AlertDialog alert = new AlertDialog.Builder(context)
                    .setTitle(labelResId)
                    .setItems(deviceIds.toArray(new String[deviceIds.size()]), null)
                    .setPositiveButton(android.R.string.ok, null)
                    .setCancelable(false)
                    .show();
            return true;
        }
        return false;
    }

    private static boolean handleRegulatoryInfoDisplay(Context context, String input) {
        if (input.equals(MMI_REGULATORY_INFO_DISPLAY)) {
            Log.d(TAG, "handleRegulatoryInfoDisplay() sending intent to settings app");
            Intent showRegInfoIntent = new Intent(Settings.ACTION_SHOW_REGULATORY_INFO);
            try {
                context.startActivity(showRegInfoIntent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "startActivity() failed: " + e);
            }
            return true;
        }
        return false;
    }

    /*******
     * This code is used to handle SIM Contact queries
     *******/
    private static final String ADN_PHONE_NUMBER_COLUMN_NAME = "number";
    private static final String ADN_NAME_COLUMN_NAME = "name";
    private static final int ADN_QUERY_TOKEN = -1;

    /**
     * Cookie object that contains everything we need to communicate to the
     * handler's onQuery Complete, as well as what we need in order to cancel
     * the query (if requested).
     *
     * Note, access to the textField field is going to be synchronized, because
     * the user can request a cancel at any time through the UI.
     */
    private static class SimContactQueryCookie implements DialogInterface.OnCancelListener{
        public ProgressDialog progressDialog;
        public int contactNum;

        // Used to identify the query request.
        private int mToken;
        private QueryHandler mHandler;

        // The text field we're going to update
        private EditText textField;

        public SimContactQueryCookie(int number, QueryHandler handler, int token) {
            contactNum = number;
            mHandler = handler;
            mToken = token;
        }

        /**
         * Synchronized getter for the EditText.
         */
        public synchronized EditText getTextField() {
            return textField;
        }

        /**
         * Synchronized setter for the EditText.
         */
        public synchronized void setTextField(EditText text) {
            textField = text;
        }

        /**
         * Cancel the ADN query by stopping the operation and signaling
         * the cookie that a cancel request is made.
         */
        public synchronized void onCancel(DialogInterface dialog) {
            // close the progress dialog
            if (progressDialog != null) {
                progressDialog.dismiss();
            }

            // setting the textfield to null ensures that the UI does NOT get
            // updated.
            textField = null;

            // Cancel the operation if possible.
            mHandler.cancelOperation(mToken);
        }
    }

    /**
     * Asynchronous query handler that services requests to look up ADNs
     *
     * Queries originate from {@link #handleAdnEntry}.
     */
    private static class QueryHandler extends NoNullCursorAsyncQueryHandler {

        private boolean mCanceled;

        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        /**
         * Override basic onQueryComplete to fill in the textfield when
         * we're handed the ADN cursor.
         */
        @Override
        protected void onNotNullableQueryComplete(int token, Object cookie, Cursor c) {
            try {
                sPreviousAdnQueryHandler = null;
                if (mCanceled) {
                    return;
                }

                SimContactQueryCookie sc = (SimContactQueryCookie) cookie;

                // close the progress dialog.
                sc.progressDialog.dismiss();

                // get the EditText to update or see if the request was cancelled.
                EditText text = sc.getTextField();

                // if the TextView is valid, and the cursor is valid and positionable on the
                // Nth number, then we update the text field and display a toast indicating the
                // caller name.
                if ((c != null) && (text != null) && (c.moveToPosition(sc.contactNum))) {
                    String name = c.getString(c.getColumnIndexOrThrow(ADN_NAME_COLUMN_NAME));
                    String number =
                            c.getString(c.getColumnIndexOrThrow(ADN_PHONE_NUMBER_COLUMN_NAME));

                    // fill the text in.
                    text.getText().replace(0, 0, number);

                    // display the name as a toast
                    Context context = sc.progressDialog.getContext();
                    CharSequence msg = ContactDisplayUtils.getTtsSpannedPhoneNumber(
                            context.getResources(), R.string.menu_callNumber, name);
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                }
            } finally {
                MoreCloseables.closeQuietly(c);
            }
        }

        public void cancel() {
            mCanceled = true;
            // Ask AsyncQueryHandler to cancel the whole request. This will fail when the query is
            // already started.
            cancelOperation(ADN_QUERY_TOKEN);
        }
    }
}
