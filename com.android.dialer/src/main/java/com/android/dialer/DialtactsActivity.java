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

package com.android.dialer;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Trace;
import android.provider.CallLog.Calls;
import android.speech.RecognizerIntent;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.telecom.PhoneAccount;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnDragListener;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView.OnScrollListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.common.dialog.ClearFrequentsDialog;
import com.android.contacts.common.interactions.ImportExportDialogFragment;
import com.android.contacts.common.interactions.TouchPointManager;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.common.util.PermissionsUtil;
import com.android.contacts.common.widget.FloatingActionButtonController;
import com.android.dialer.calllog.CallLogActivity;
import com.android.dialer.calllog.CallLogFragment;
import com.android.dialer.database.DialerDatabaseHelper;
import com.android.dialer.dialpad.DialpadFragment;
import com.android.dialer.dialpad.SmartDialNameMatcher;
import com.android.dialer.dialpad.SmartDialPrefix;
import com.android.dialer.interactions.PhoneNumberInteraction;
import com.android.dialer.list.DragDropController;
import com.android.dialer.list.ListsFragment;
import com.android.dialer.list.OnDragDropListener;
import com.android.dialer.list.OnListFragmentScrolledListener;
import com.android.dialer.list.PhoneFavoriteSquareTileView;
import com.android.dialer.list.RegularSearchFragment;
import com.android.dialer.list.SearchFragment;
import com.android.dialer.list.SmartDialSearchFragment;
import com.android.dialer.list.SpeedDialFragment;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ScreenEvent;
import com.android.dialer.settings.DialerSettingsActivity;
import com.android.dialer.util.Assert;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.IntentUtil;
import com.android.dialer.util.IntentUtil.CallIntentBuilder;
import com.android.dialer.util.TelecomUtil;
import com.android.dialer.voicemail.VoicemailArchiveActivity;
import com.android.dialer.widget.ActionBarController;
import com.android.dialer.widget.SearchEditTextLayout;
import com.android.dialerbind.DatabaseHelperManager;
import com.android.dialerbind.ObjectFactory;
import com.android.phone.common.animation.AnimUtils;
import com.android.phone.common.animation.AnimationListenerAdapter;
import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/**
 * The dialer tab's title is 'phone', a more common name (see strings.xml).
 */
public class DialtactsActivity extends TransactionSafeActivity implements View.OnClickListener,
        DialpadFragment.OnDialpadQueryChangedListener,
        OnListFragmentScrolledListener,
        CallLogFragment.HostInterface,
        DialpadFragment.HostInterface,
        ListsFragment.HostInterface,
        SpeedDialFragment.HostInterface,
        SearchFragment.HostInterface,
        OnDragDropListener,
        OnPhoneNumberPickerActionListener,
        PopupMenu.OnMenuItemClickListener,
        ViewPager.OnPageChangeListener,
        ActionBarController.ActivityUi {
    private static final String TAG = "DialtactsActivity";

    public static final boolean DEBUG = false;

    public static final String SHARED_PREFS_NAME = "com.android.dialer_preferences";

    private static final String KEY_IN_REGULAR_SEARCH_UI = "in_regular_search_ui";
    private static final String KEY_IN_DIALPAD_SEARCH_UI = "in_dialpad_search_ui";
    private static final String KEY_SEARCH_QUERY = "search_query";
    private static final String KEY_FIRST_LAUNCH = "first_launch";
    private static final String KEY_IS_DIALPAD_SHOWN = "is_dialpad_shown";

    @VisibleForTesting
    public static final String TAG_DIALPAD_FRAGMENT = "dialpad";
    private static final String TAG_REGULAR_SEARCH_FRAGMENT = "search";
    private static final String TAG_SMARTDIAL_SEARCH_FRAGMENT = "smartdial";
    private static final String TAG_FAVORITES_FRAGMENT = "favorites";

    /**
     * Just for backward compatibility. Should behave as same as {@link Intent#ACTION_DIAL}.
     */
    private static final String ACTION_TOUCH_DIALER = "com.android.phone.action.TOUCH_DIALER";
    public static final String EXTRA_SHOW_TAB = "EXTRA_SHOW_TAB";

    private static final int ACTIVITY_REQUEST_CODE_VOICE_SEARCH = 1;

    private static final int FAB_SCALE_IN_DELAY_MS = 300;

    private CoordinatorLayout mParentLayout;

    /**
     * Fragment containing the dialpad that slides into view
     */
    protected DialpadFragment mDialpadFragment;

    /**
     * Fragment for searching phone numbers using the alphanumeric keyboard.
     */
    private RegularSearchFragment mRegularSearchFragment;

    /**
     * Fragment for searching phone numbers using the dialpad.
     */
    private SmartDialSearchFragment mSmartDialSearchFragment;

    /**
     * Animation that slides in.
     */
    private Animation mSlideIn;

    /**
     * Animation that slides out.
     */
    private Animation mSlideOut;

    AnimationListenerAdapter mSlideInListener = new AnimationListenerAdapter() {
        @Override
        public void onAnimationEnd(Animation animation) {
            maybeEnterSearchUi();
        }
    };

    /**
     * Listener for after slide out animation completes on dialer fragment.
     */
    AnimationListenerAdapter mSlideOutListener = new AnimationListenerAdapter() {
        @Override
        public void onAnimationEnd(Animation animation) {
            commitDialpadFragmentHide();
        }
    };

    /**
     * Fragment containing the speed dial list, call history list, and all contacts list.
     */
    private ListsFragment mListsFragment;

    /**
     * Tracks whether onSaveInstanceState has been called. If true, no fragment transactions can
     * be commited.
     */
    private boolean mStateSaved;
    private boolean mIsRestarting;
    private boolean mInDialpadSearch;
    private boolean mInRegularSearch;
    private boolean mClearSearchOnPause;
    private boolean mIsDialpadShown;
    private boolean mShowDialpadOnResume;

    /**
     * Whether or not the device is in landscape orientation.
     */
    private boolean mIsLandscape;

    /**
     * True if the dialpad is only temporarily showing due to being in call
     */
    private boolean mInCallDialpadUp;

    /**
     * True when this activity has been launched for the first time.
     */
    private boolean mFirstLaunch;

    /**
     * Search query to be applied to the SearchView in the ActionBar once
     * onCreateOptionsMenu has been called.
     */
    private String mPendingSearchViewQuery;

    private PopupMenu mOverflowMenu;
    private EditText mSearchView;
    private View mVoiceSearchButton;

    private String mSearchQuery;
    private String mDialpadQuery;

    private DialerDatabaseHelper mDialerDatabaseHelper;
    private DragDropController mDragDropController;
    private ActionBarController mActionBarController;

    private FloatingActionButtonController mFloatingActionButtonController;

    private int mActionBarHeight;
    private int mPreviouslySelectedTabIndex;

    /**
     * The text returned from a voice search query.  Set in {@link #onActivityResult} and used in
     * {@link #onResume()} to populate the search box.
     */
    private String mVoiceSearchQuery;

    protected class OptionsPopupMenu extends PopupMenu {
        public OptionsPopupMenu(Context context, View anchor) {
            super(context, anchor, Gravity.END);
        }

        @Override
        public void show() {
            final boolean hasContactsPermission =
                    PermissionsUtil.hasContactsPermissions(DialtactsActivity.this);
            final Menu menu = getMenu();
            final MenuItem clearFrequents = menu.findItem(R.id.menu_clear_frequents);
            clearFrequents.setVisible(mListsFragment != null &&
                    mListsFragment.getSpeedDialFragment() != null &&
                    mListsFragment.getSpeedDialFragment().hasFrequents() && hasContactsPermission);

            menu.findItem(R.id.menu_import_export).setVisible(hasContactsPermission);
            menu.findItem(R.id.menu_add_contact).setVisible(hasContactsPermission);

            menu.findItem(R.id.menu_history).setVisible(
                    PermissionsUtil.hasPhonePermissions(DialtactsActivity.this));
            super.show();
        }
    }

    /**
     * Listener that listens to drag events and sends their x and y coordinates to a
     * {@link DragDropController}.
     */
    private class LayoutOnDragListener implements OnDragListener {
        @Override
        public boolean onDrag(View v, DragEvent event) {
            if (event.getAction() == DragEvent.ACTION_DRAG_LOCATION) {
                mDragDropController.handleDragHovered(v, (int) event.getX(), (int) event.getY());
            }
            return true;
        }
    }

    /**
     * Listener used to send search queries to the phone search fragment.
     */
    private final TextWatcher mPhoneSearchQueryTextListener = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            final String newText = s.toString();
            if (newText.equals(mSearchQuery)) {
                // If the query hasn't changed (perhaps due to activity being destroyed
                // and restored, or user launching the same DIAL intent twice), then there is
                // no need to do anything here.
                return;
            }
            if (DEBUG) {
                Log.d(TAG, "onTextChange for mSearchView called with new query: " + newText);
                Log.d(TAG, "Previous Query: " + mSearchQuery);
            }
            mSearchQuery = newText;

            // Show search fragment only when the query string is changed to non-empty text.
            if (!TextUtils.isEmpty(newText)) {
                // Call enterSearchUi only if we are switching search modes, or showing a search
                // fragment for the first time.
                final boolean sameSearchMode = (mIsDialpadShown && mInDialpadSearch) ||
                        (!mIsDialpadShown && mInRegularSearch);
                if (!sameSearchMode) {
                    enterSearchUi(mIsDialpadShown, mSearchQuery, true /* animate */);
                }
            }

            if (mSmartDialSearchFragment != null && mSmartDialSearchFragment.isVisible()) {
                mSmartDialSearchFragment.setQueryString(mSearchQuery, false /* delaySelection */);
            } else if (mRegularSearchFragment != null && mRegularSearchFragment.isVisible()) {
                mRegularSearchFragment.setQueryString(mSearchQuery, false /* delaySelection */);
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };


    /**
     * Open the search UI when the user clicks on the search box.
     */
    private final View.OnClickListener mSearchViewOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!isInSearchUi()) {
                mActionBarController.onSearchBoxTapped();
                enterSearchUi(false /* smartDialSearch */, mSearchView.getText().toString(),
                        true /* animate */);
            }
        }
    };

    /**
     * Handles the user closing the soft keyboard.
     */
    private final View.OnKeyListener mSearchEditTextLayoutListener = new View.OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
                if (TextUtils.isEmpty(mSearchView.getText().toString())) {
                    // If the search term is empty, close the search UI.
                    maybeExitSearchUi();
                } else {
                    // If the search term is not empty, show the dialpad fab.
                    showFabInSearchUi();
                }
            }
            return false;
        }
    };

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            TouchPointManager.getInstance().setPoint((int) ev.getRawX(), (int) ev.getRawY());
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Trace.beginSection(TAG + " onCreate");
        super.onCreate(savedInstanceState);

        // add by geniusgithub
        boolean hasStartPermissionActivity = ForceRequestPermissionsActivity.startPermissionActivity(this);

        // add by geniusgithub

        mFirstLaunch = true;

        final Resources resources = getResources();
        mActionBarHeight = resources.getDimensionPixelSize(R.dimen.action_bar_height_large);

        Trace.beginSection(TAG + " setContentView");
        setContentView(R.layout.dialtacts_activity);
        Trace.endSection();
        getWindow().setBackgroundDrawable(null);

        Trace.beginSection(TAG + " setup Views");
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setCustomView(R.layout.search_edittext);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setBackgroundDrawable(null);

        SearchEditTextLayout searchEditTextLayout = (SearchEditTextLayout) actionBar
                .getCustomView().findViewById(R.id.search_view_container);
        searchEditTextLayout.setPreImeKeyListener(mSearchEditTextLayoutListener);

        mActionBarController = new ActionBarController(this, searchEditTextLayout);

        mSearchView = (EditText) searchEditTextLayout.findViewById(R.id.search_view);
        mSearchView.addTextChangedListener(mPhoneSearchQueryTextListener);
        mVoiceSearchButton = searchEditTextLayout.findViewById(R.id.voice_search_button);
        searchEditTextLayout.findViewById(R.id.search_magnifying_glass)
                .setOnClickListener(mSearchViewOnClickListener);
        searchEditTextLayout.findViewById(R.id.search_box_start_search)
                .setOnClickListener(mSearchViewOnClickListener);
        searchEditTextLayout.setOnClickListener(mSearchViewOnClickListener);
        searchEditTextLayout.setCallback(new SearchEditTextLayout.Callback() {
            @Override
            public void onBackButtonClicked() {
                onBackPressed();
            }

            @Override
            public void onSearchViewClicked() {
                // Hide FAB, as the keyboard is shown.
                mFloatingActionButtonController.scaleOut();
            }
        });

        mIsLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        mPreviouslySelectedTabIndex = ListsFragment.TAB_INDEX_SPEED_DIAL;
        final View floatingActionButtonContainer = findViewById(
                R.id.floating_action_button_container);
        ImageButton floatingActionButton = (ImageButton) findViewById(R.id.floating_action_button);
        floatingActionButton.setOnClickListener(this);
        mFloatingActionButtonController = new FloatingActionButtonController(this,
                floatingActionButtonContainer, floatingActionButton);

        ImageButton optionsMenuButton =
                (ImageButton) searchEditTextLayout.findViewById(R.id.dialtacts_options_menu_button);
        optionsMenuButton.setOnClickListener(this);
        mOverflowMenu = buildOptionsMenu(searchEditTextLayout);
        optionsMenuButton.setOnTouchListener(mOverflowMenu.getDragToOpenListener());

        // Add the favorites fragment but only if savedInstanceState is null. Otherwise the
        // fragment manager is responsible for recreating it.
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.dialtacts_frame, new ListsFragment(), TAG_FAVORITES_FRAGMENT)
                    .commit();
        } else {
            mSearchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY);
            mInRegularSearch = savedInstanceState.getBoolean(KEY_IN_REGULAR_SEARCH_UI);
            mInDialpadSearch = savedInstanceState.getBoolean(KEY_IN_DIALPAD_SEARCH_UI);
            mFirstLaunch = savedInstanceState.getBoolean(KEY_FIRST_LAUNCH);
            mShowDialpadOnResume = savedInstanceState.getBoolean(KEY_IS_DIALPAD_SHOWN);
            mActionBarController.restoreInstanceState(savedInstanceState);
        }

        final boolean isLayoutRtl = DialerUtils.isRtl();
        if (mIsLandscape) {
            mSlideIn = AnimationUtils.loadAnimation(this,
                    isLayoutRtl ? R.anim.dialpad_slide_in_left : R.anim.dialpad_slide_in_right);
            mSlideOut = AnimationUtils.loadAnimation(this,
                    isLayoutRtl ? R.anim.dialpad_slide_out_left : R.anim.dialpad_slide_out_right);
        } else {
            mSlideIn = AnimationUtils.loadAnimation(this, R.anim.dialpad_slide_in_bottom);
            mSlideOut = AnimationUtils.loadAnimation(this, R.anim.dialpad_slide_out_bottom);
        }

        mSlideIn.setInterpolator(AnimUtils.EASE_IN);
        mSlideOut.setInterpolator(AnimUtils.EASE_OUT);

        mSlideIn.setAnimationListener(mSlideInListener);
        mSlideOut.setAnimationListener(mSlideOutListener);

        mParentLayout = (CoordinatorLayout) findViewById(R.id.dialtacts_mainlayout);
        mParentLayout.setOnDragListener(new LayoutOnDragListener());
        floatingActionButtonContainer.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        final ViewTreeObserver observer =
                                floatingActionButtonContainer.getViewTreeObserver();
                        if (!observer.isAlive()) {
                            return;
                        }
                        observer.removeOnGlobalLayoutListener(this);
                        int screenWidth = mParentLayout.getWidth();
                        mFloatingActionButtonController.setScreenWidth(screenWidth);
                        mFloatingActionButtonController.align(
                                getFabAlignment(), false /* animate */);
                    }
                });

        Trace.endSection();

        Trace.beginSection(TAG + " initialize smart dialing");
        mDialerDatabaseHelper = DatabaseHelperManager.getDatabaseHelper(this);
        SmartDialPrefix.initializeNanpSettings(this);
        Trace.endSection();
        Trace.endSection();
    }

    @Override
    protected void onResume() {
        Trace.beginSection(TAG + " onResume");
        super.onResume();

        mStateSaved = false;
        if (mFirstLaunch) {
            displayFragment(getIntent());
        } else if (!phoneIsInUse() && mInCallDialpadUp) {
            hideDialpadFragment(false, true);
            mInCallDialpadUp = false;
        } else if (mShowDialpadOnResume) {
            showDialpadFragment(false);
            mShowDialpadOnResume = false;
        }

        // If there was a voice query result returned in the {@link #onActivityResult} callback, it
        // will have been stashed in mVoiceSearchQuery since the search results fragment cannot be
        // shown until onResume has completed.  Active the search UI and set the search term now.
        if (!TextUtils.isEmpty(mVoiceSearchQuery)) {
            mActionBarController.onSearchBoxTapped();
            mSearchView.setText(mVoiceSearchQuery);
            mVoiceSearchQuery = null;
        }

        mFirstLaunch = false;

        if (mIsRestarting) {
            // This is only called when the activity goes from resumed -> paused -> resumed, so it
            // will not cause an extra view to be sent out on rotation
            if (mIsDialpadShown) {
                Logger.logScreenView(ScreenEvent.DIALPAD, this);
            }
            mIsRestarting = false;
        }

        prepareVoiceSearchButton();
        mDialerDatabaseHelper.startSmartDialUpdateThread();
        mFloatingActionButtonController.align(getFabAlignment(), false /* animate */);

        if (Calls.CONTENT_TYPE.equals(getIntent().getType())) {
            // Externally specified extras take precedence to EXTRA_SHOW_TAB, which is only
            // used internally.
            final Bundle extras = getIntent().getExtras();
            if (extras != null
                    && extras.getInt(Calls.EXTRA_CALL_TYPE_FILTER) == Calls.VOICEMAIL_TYPE) {
                mListsFragment.showTab(ListsFragment.TAB_INDEX_VOICEMAIL);
            } else {
                mListsFragment.showTab(ListsFragment.TAB_INDEX_HISTORY);
            }
        } else if (getIntent().hasExtra(EXTRA_SHOW_TAB)) {
            int index = getIntent().getIntExtra(EXTRA_SHOW_TAB, ListsFragment.TAB_INDEX_SPEED_DIAL);
            if (index < mListsFragment.getTabCount()) {
                mListsFragment.showTab(index);
            }
        }

        setSearchBoxHint();

        Trace.endSection();
        StatisticsUtil.onResume(this);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        mIsRestarting = true;
    }

    @Override
    protected void onPause() {
        // Only clear missed calls if the pause was not triggered by an orientation change
        // (or any other confirguration change)
        if (!isChangingConfigurations()) {
            updateMissedCalls();
        }
        if (mClearSearchOnPause) {
            hideDialpadAndSearchUi();
            mClearSearchOnPause = false;
        }
        if (mSlideOut.hasStarted() && !mSlideOut.hasEnded()) {
            commitDialpadFragmentHide();
        }
        StatisticsUtil.onPause(this);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_SEARCH_QUERY, mSearchQuery);
        outState.putBoolean(KEY_IN_REGULAR_SEARCH_UI, mInRegularSearch);
        outState.putBoolean(KEY_IN_DIALPAD_SEARCH_UI, mInDialpadSearch);
        outState.putBoolean(KEY_FIRST_LAUNCH, mFirstLaunch);
        outState.putBoolean(KEY_IS_DIALPAD_SHOWN, mIsDialpadShown);
        mActionBarController.saveInstanceState(outState);
        mStateSaved = true;
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof DialpadFragment) {
            mDialpadFragment = (DialpadFragment) fragment;
            if (!mIsDialpadShown && !mShowDialpadOnResume) {
                final FragmentTransaction transaction = getFragmentManager().beginTransaction();
                transaction.hide(mDialpadFragment);
                transaction.commit();
            }
        } else if (fragment instanceof SmartDialSearchFragment) {
            mSmartDialSearchFragment = (SmartDialSearchFragment) fragment;
            mSmartDialSearchFragment.setOnPhoneNumberPickerActionListener(this);
            if (!TextUtils.isEmpty(mDialpadQuery)) {
                mSmartDialSearchFragment.setAddToContactNumber(mDialpadQuery);
            }
        } else if (fragment instanceof SearchFragment) {
            mRegularSearchFragment = (RegularSearchFragment) fragment;
            mRegularSearchFragment.setOnPhoneNumberPickerActionListener(this);
        } else if (fragment instanceof ListsFragment) {
            mListsFragment = (ListsFragment) fragment;
            mListsFragment.addOnPageChangeListener(this);
        }
    }

    protected void handleMenuSettings() {
        final Intent intent = new Intent(this, DialerSettingsActivity.class);
        startActivity(intent);
    }

    @Override
    public void onClick(View view) {
        int resId = view.getId();
        if (resId == R.id.floating_action_button) {
            if (mListsFragment.getCurrentTabIndex()
                    == ListsFragment.TAB_INDEX_ALL_CONTACTS && !mInRegularSearch) {
                DialerUtils.startActivityWithErrorToast(
                        this,
                        IntentUtil.getNewContactIntent(),
                        R.string.add_contact_not_available);
            } else if (!mIsDialpadShown) {
                mInCallDialpadUp = false;
                showDialpadFragment(true);
            }
        } else if (resId == R.id.voice_search_button) {
            try {
                startActivityForResult(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
                        ACTIVITY_REQUEST_CODE_VOICE_SEARCH);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(DialtactsActivity.this, R.string.voice_search_not_available,
                        Toast.LENGTH_SHORT).show();
            }
        } else if (resId == R.id.dialtacts_options_menu_button) {
            mOverflowMenu.show();
        } else {
            Log.wtf(TAG, "Unexpected onClick event from " + view);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (!isSafeToCommitTransactions()) {
            return true;
        }

        int resId = item.getItemId();
        if (resId == R.id.menu_history) {// Use explicit CallLogActivity intent instead of ACTION_VIEW +
            // CONTENT_TYPE, so that we always open our call log from our dialer
            final Intent intent = new Intent(this, CallLogActivity.class);
            startActivity(intent);
        } else if (resId == R.id.menu_add_contact) {
            DialerUtils.startActivityWithErrorToast(
                    this,
                    IntentUtil.getNewContactIntent(),
                    R.string.add_contact_not_available);
        } else if (resId == R.id.menu_import_export) {// We hard-code the "contactsAreAvailable" argument because doing it properly would
            // involve querying a {@link ProviderStatusLoader}, which we don't want to do right
            // now in Dialtacts for (potential) performance reasons. Compare with how it is
            // done in {@link PeopleActivity}.
            if (mListsFragment.getCurrentTabIndex() == ListsFragment.TAB_INDEX_SPEED_DIAL) {
                ImportExportDialogFragment.show(getFragmentManager(), true,
                        DialtactsActivity.class, ImportExportDialogFragment.EXPORT_MODE_FAVORITES);
            } else {
                ImportExportDialogFragment.show(getFragmentManager(), true,
                        DialtactsActivity.class, ImportExportDialogFragment.EXPORT_MODE_DEFAULT);
            }
            Logger.logScreenView(ScreenEvent.IMPORT_EXPORT_CONTACTS, this);
            return true;
        } else if (resId == R.id.menu_clear_frequents) {
            ClearFrequentsDialog.show(getFragmentManager());
            Logger.logScreenView(ScreenEvent.CLEAR_FREQUENTS, this);
            return true;
        } else if (resId == R.id.menu_call_settings) {
            handleMenuSettings();
            Logger.logScreenView(ScreenEvent.SETTINGS, this);
            return true;
        } else if (resId == R.id.menu_archive) {
            final Intent intent = new Intent(this, VoicemailArchiveActivity.class);
            startActivity(intent);
            return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ACTIVITY_REQUEST_CODE_VOICE_SEARCH) {
            if (resultCode == RESULT_OK) {
                final ArrayList<String> matches = data.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS);
                if (matches.size() > 0) {
                    final String match = matches.get(0);
                    mVoiceSearchQuery = match;
                } else {
                    Log.e(TAG, "Voice search - nothing heard");
                }
            } else {
                Log.e(TAG, "Voice search failed");
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Update the number of unread voicemails (potentially other tabs) displayed next to the tab
     * icon.
     */
    public void updateTabUnreadCounts() {
        mListsFragment.updateTabUnreadCounts();
    }

    /**
     * Initiates a fragment transaction to show the dialpad fragment. Animations and other visual
     * updates are handled by a callback which is invoked after the dialpad fragment is shown.
     * @see #onDialpadShown
     */
    private void showDialpadFragment(boolean animate) {
        if (mIsDialpadShown || mStateSaved) {
            return;
        }
        mIsDialpadShown = true;

        mListsFragment.setUserVisibleHint(false);

        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        if (mDialpadFragment == null) {
            mDialpadFragment = new DialpadFragment();
            ft.add(R.id.dialtacts_container, mDialpadFragment, TAG_DIALPAD_FRAGMENT);
        } else {
            ft.show(mDialpadFragment);
        }

        mDialpadFragment.setAnimate(animate);
        Logger.logScreenView(ScreenEvent.DIALPAD, this);
        ft.commit();

        if (animate) {
            mFloatingActionButtonController.scaleOut();
        } else {
            mFloatingActionButtonController.setVisible(false);
            maybeEnterSearchUi();
        }
        mActionBarController.onDialpadUp();

        mListsFragment.getView().animate().alpha(0).withLayer();

        //adjust the title, so the user will know where we're at when the activity start/resumes.
        setTitle(R.string.launcherDialpadActivityLabel);
    }

    /**
     * Callback from child DialpadFragment when the dialpad is shown.
     */
    public void onDialpadShown() {
        Assert.assertNotNull(mDialpadFragment);
        if (mDialpadFragment.getAnimate()) {
            mDialpadFragment.getView().startAnimation(mSlideIn);
        } else {
            mDialpadFragment.setYFraction(0);
        }

        updateSearchFragmentPosition();
    }

    /**
     * Initiates animations and other visual updates to hide the dialpad. The fragment is hidden in
     * a callback after the hide animation ends.
     * @see #commitDialpadFragmentHide
     */
    public void hideDialpadFragment(boolean animate, boolean clearDialpad) {
        if (mDialpadFragment == null || mDialpadFragment.getView() == null) {
            return;
        }
        if (clearDialpad) {
            // Temporarily disable accessibility when we clear the dialpad, since it should be
            // invisible and should not announce anything.
            mDialpadFragment.getDigitsWidget().setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            mDialpadFragment.clearDialpad();
            mDialpadFragment.getDigitsWidget().setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }
        if (!mIsDialpadShown) {
            return;
        }
        mIsDialpadShown = false;
        mDialpadFragment.setAnimate(animate);
        mListsFragment.setUserVisibleHint(true);
        mListsFragment.sendScreenViewForCurrentPosition();

        updateSearchFragmentPosition();

        mFloatingActionButtonController.align(getFabAlignment(), animate);
        if (animate) {
            mDialpadFragment.getView().startAnimation(mSlideOut);
        } else {
            commitDialpadFragmentHide();
        }

        mActionBarController.onDialpadDown();

        if (isInSearchUi()) {
            if (TextUtils.isEmpty(mSearchQuery)) {
                exitSearchUi();
            }
        }
        //reset the title to normal.
        setTitle(R.string.launcherActivityLabel);
    }

    /**
     * Finishes hiding the dialpad fragment after any animations are completed.
     */
    private void commitDialpadFragmentHide() {
        if (!mStateSaved && mDialpadFragment != null && !mDialpadFragment.isHidden()) {
            final FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.hide(mDialpadFragment);
            ft.commit();
        }
        mFloatingActionButtonController.scaleIn(AnimUtils.NO_DELAY);
    }

    private void updateSearchFragmentPosition() {
        SearchFragment fragment = null;
        if (mSmartDialSearchFragment != null && mSmartDialSearchFragment.isVisible()) {
            fragment = mSmartDialSearchFragment;
        } else if (mRegularSearchFragment != null && mRegularSearchFragment.isVisible()) {
            fragment = mRegularSearchFragment;
        }
        if (fragment != null && fragment.isVisible()) {
            fragment.updatePosition(true /* animate */);
        }
    }

    @Override
    public boolean isInSearchUi() {
        return mInDialpadSearch || mInRegularSearch;
    }

    @Override
    public boolean hasSearchQuery() {
        return !TextUtils.isEmpty(mSearchQuery);
    }

    @Override
    public boolean shouldShowActionBar() {
        return mListsFragment.shouldShowActionBar();
    }

    private void setNotInSearchUi() {
        mInDialpadSearch = false;
        mInRegularSearch = false;
    }

    private void hideDialpadAndSearchUi() {
        if (mIsDialpadShown) {
            hideDialpadFragment(false, true);
        } else {
            exitSearchUi();
        }
    }

    private void prepareVoiceSearchButton() {
        final Intent voiceIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        if (canIntentBeHandled(voiceIntent)) {
            mVoiceSearchButton.setVisibility(View.VISIBLE);
            mVoiceSearchButton.setOnClickListener(this);
        } else {
            mVoiceSearchButton.setVisibility(View.GONE);
        }
    }

    public boolean isNearbyPlacesSearchEnabled() {
        return false;
    }

    protected int getSearchBoxHint () {
        return R.string.dialer_hint_find_contact;
    }

    /**
     * Sets the hint text for the contacts search box
     */
    private void setSearchBoxHint() {
        SearchEditTextLayout searchEditTextLayout = (SearchEditTextLayout) getSupportActionBar()
                .getCustomView().findViewById(R.id.search_view_container);
        ((TextView) searchEditTextLayout.findViewById(R.id.search_box_start_search))
                .setHint(getSearchBoxHint());
    }

    protected OptionsPopupMenu buildOptionsMenu(View invoker) {
        final OptionsPopupMenu popupMenu = new OptionsPopupMenu(this, invoker);
        popupMenu.inflate(R.menu.dialtacts_options);
        if (ObjectFactory.isVoicemailArchiveEnabled(this)) {
            popupMenu.getMenu().findItem(R.id.menu_archive).setVisible(true);
        }
        popupMenu.setOnMenuItemClickListener(this);
        return popupMenu;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mPendingSearchViewQuery != null) {
            mSearchView.setText(mPendingSearchViewQuery);
            mPendingSearchViewQuery = null;
        }
        if (mActionBarController != null) {
            mActionBarController.restoreActionBarOffset();
        }
        return false;
    }

    /**
     * Returns true if the intent is due to hitting the green send key (hardware call button:
     * KEYCODE_CALL) while in a call.
     *
     * @param intent the intent that launched this activity
     * @return true if the intent is due to hitting the green send key while in a call
     */
    private boolean isSendKeyWhileInCall(Intent intent) {
        // If there is a call in progress and the user launched the dialer by hitting the call
        // button, go straight to the in-call screen.
        final boolean callKey = Intent.ACTION_CALL_BUTTON.equals(intent.getAction());

        if (callKey) {
            TelecomUtil.showInCallScreen(this, false);
            return true;
        }

        return false;
    }

    /**
     * Sets the current tab based on the intent's request type
     *
     * @param intent Intent that contains information about which tab should be selected
     */
    private void displayFragment(Intent intent) {
        // If we got here by hitting send and we're in call forward along to the in-call activity
        if (isSendKeyWhileInCall(intent)) {
            finish();
            return;
        }

        final boolean showDialpadChooser = phoneIsInUse() && !DialpadFragment.isAddCallMode(intent);
        if (showDialpadChooser || (intent.getData() != null && isDialIntent(intent))) {
            showDialpadFragment(false);
            mDialpadFragment.setStartedFromNewIntent(true);
            if (showDialpadChooser && !mDialpadFragment.isVisible()) {
                mInCallDialpadUp = true;
            }
        }
    }

    @Override
    public void onNewIntent(Intent newIntent) {
        setIntent(newIntent);

        mStateSaved = false;
        displayFragment(newIntent);

        invalidateOptionsMenu();
    }

    /** Returns true if the given intent contains a phone number to populate the dialer with */
    private boolean isDialIntent(Intent intent) {
        final String action = intent.getAction();
        if (Intent.ACTION_DIAL.equals(action) || ACTION_TOUCH_DIALER.equals(action)) {
            return true;
        }
        if (Intent.ACTION_VIEW.equals(action)) {
            final Uri data = intent.getData();
            if (data != null && PhoneAccount.SCHEME_TEL.equals(data.getScheme())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Shows the search fragment
     */
    private void enterSearchUi(boolean smartDialSearch, String query, boolean animate) {
        if (mStateSaved || getFragmentManager().isDestroyed()) {
            // Weird race condition where fragment is doing work after the activity is destroyed
            // due to talkback being on (b/10209937). Just return since we can't do any
            // constructive here.
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "Entering search UI - smart dial " + smartDialSearch);
        }

        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        if (mInDialpadSearch && mSmartDialSearchFragment != null) {
            transaction.remove(mSmartDialSearchFragment);
        } else if (mInRegularSearch && mRegularSearchFragment != null) {
            transaction.remove(mRegularSearchFragment);
        }

        final String tag;
        if (smartDialSearch) {
            tag = TAG_SMARTDIAL_SEARCH_FRAGMENT;
        } else {
            tag = TAG_REGULAR_SEARCH_FRAGMENT;
        }
        mInDialpadSearch = smartDialSearch;
        mInRegularSearch = !smartDialSearch;

        mFloatingActionButtonController.scaleOut();

        SearchFragment fragment = (SearchFragment) getFragmentManager().findFragmentByTag(tag);
        if (animate) {
            transaction.setCustomAnimations(android.R.animator.fade_in, 0);
        } else {
            transaction.setTransition(FragmentTransaction.TRANSIT_NONE);
        }
        if (fragment == null) {
            if (smartDialSearch) {
                fragment = new SmartDialSearchFragment();
            } else {
                fragment = ObjectFactory.newRegularSearchFragment();
                fragment.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        // Show the FAB when the user touches the lists fragment and the soft
                        // keyboard is hidden.
                        hideDialpadFragment(true, false);
                        showFabInSearchUi();
                        return false;
                    }
                });
            }
            transaction.add(R.id.dialtacts_frame, fragment, tag);
        } else {
            transaction.show(fragment);
        }
        // DialtactsActivity will provide the options menu
        fragment.setHasOptionsMenu(false);
        fragment.setShowEmptyListForNullQuery(true);
        if (!smartDialSearch) {
            fragment.setQueryString(query, false /* delaySelection */);
        }
        transaction.commit();

        if (animate) {
            mListsFragment.getView().animate().alpha(0).withLayer();
        }
        mListsFragment.setUserVisibleHint(false);

        if (smartDialSearch) {
            Logger.logScreenView(ScreenEvent.SMART_DIAL_SEARCH, this);
        } else {
            Logger.logScreenView(ScreenEvent.REGULAR_SEARCH, this);
        }
    }

    /**
     * Hides the search fragment
     */
    private void exitSearchUi() {
        // See related bug in enterSearchUI();
        if (getFragmentManager().isDestroyed() || mStateSaved) {
            return;
        }

        mSearchView.setText(null);

        if (mDialpadFragment != null) {
            mDialpadFragment.clearDialpad();
        }

        setNotInSearchUi();

        // Restore the FAB for the lists fragment.
        if (getFabAlignment() != FloatingActionButtonController.ALIGN_END) {
            mFloatingActionButtonController.setVisible(false);
        }
        mFloatingActionButtonController.scaleIn(FAB_SCALE_IN_DELAY_MS);
        onPageScrolled(mListsFragment.getCurrentTabIndex(), 0 /* offset */, 0 /* pixelOffset */);
        onPageSelected(mListsFragment.getCurrentTabIndex());

        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        if (mSmartDialSearchFragment != null) {
            transaction.remove(mSmartDialSearchFragment);
        }
        if (mRegularSearchFragment != null) {
            transaction.remove(mRegularSearchFragment);
        }
        transaction.commit();

        mListsFragment.getView().animate().alpha(1).withLayer();

        if (mDialpadFragment == null || !mDialpadFragment.isVisible()) {
            // If the dialpad fragment wasn't previously visible, then send a screen view because
            // we are exiting regular search. Otherwise, the screen view will be sent by
            // {@link #hideDialpadFragment}.
            mListsFragment.sendScreenViewForCurrentPosition();
            mListsFragment.setUserVisibleHint(true);
        }

        mActionBarController.onSearchUiExited();
    }

    @Override
    public void onBackPressed() {
        if (mStateSaved) {
            return;
        }
        if (mIsDialpadShown) {
            if (TextUtils.isEmpty(mSearchQuery) ||
                    (mSmartDialSearchFragment != null && mSmartDialSearchFragment.isVisible()
                            && mSmartDialSearchFragment.getAdapter().getCount() == 0)) {
                exitSearchUi();
            }
            hideDialpadFragment(true, false);
        } else if (isInSearchUi()) {
            exitSearchUi();
            DialerUtils.hideInputMethod(mParentLayout);
        } else {
            super.onBackPressed();
        }
    }

    private void maybeEnterSearchUi() {
        if (!isInSearchUi()) {
            enterSearchUi(true /* isSmartDial */, mSearchQuery, false);
        }
    }

    /**
     * @return True if the search UI was exited, false otherwise
     */
    private boolean maybeExitSearchUi() {
        if (isInSearchUi() && TextUtils.isEmpty(mSearchQuery)) {
            exitSearchUi();
            DialerUtils.hideInputMethod(mParentLayout);
            return true;
        }
        return false;
    }

    private void showFabInSearchUi() {
        mFloatingActionButtonController.changeIcon(
                getResources().getDrawable(R.drawable.fab_ic_dial),
                getResources().getString(R.string.action_menu_dialpad_button));
        mFloatingActionButtonController.align(getFabAlignment(), false /* animate */);
        mFloatingActionButtonController.scaleIn(FAB_SCALE_IN_DELAY_MS);
    }

    @Override
    public void onDialpadQueryChanged(String query) {
        mDialpadQuery = query;
        if (mSmartDialSearchFragment != null) {
            mSmartDialSearchFragment.setAddToContactNumber(query);
        }
        final String normalizedQuery = SmartDialNameMatcher.normalizeNumber(query,
                SmartDialNameMatcher.LATIN_SMART_DIAL_MAP);

        if (!TextUtils.equals(mSearchView.getText(), normalizedQuery)) {
            if (DEBUG) {
                Log.d(TAG, "onDialpadQueryChanged - new query: " + query);
            }
            if (mDialpadFragment == null || !mDialpadFragment.isVisible()) {
                // This callback can happen if the dialpad fragment is recreated because of
                // activity destruction. In that case, don't update the search view because
                // that would bring the user back to the search fragment regardless of the
                // previous state of the application. Instead, just return here and let the
                // fragment manager correctly figure out whatever fragment was last displayed.
                if (!TextUtils.isEmpty(normalizedQuery)) {
                    mPendingSearchViewQuery = normalizedQuery;
                }
                return;
            }
            mSearchView.setText(normalizedQuery);
        }

        try {
            if (mDialpadFragment != null && mDialpadFragment.isVisible()) {
                mDialpadFragment.process_quote_emergency_unquote(normalizedQuery);
            }
        } catch (Exception ignored) {
            // Skip any exceptions for this piece of code
        }
    }

    @Override
    public boolean onDialpadSpacerTouchWithEmptyQuery() {
        if (mInDialpadSearch && mSmartDialSearchFragment != null
                && !mSmartDialSearchFragment.isShowingPermissionRequest()) {
            hideDialpadFragment(true /* animate */, true /* clearDialpad */);
            return true;
        }
        return false;
    }

    @Override
    public void onListFragmentScrollStateChange(int scrollState) {
        if (scrollState == OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
            hideDialpadFragment(true, false);
            DialerUtils.hideInputMethod(mParentLayout);
        }
    }

    @Override
    public void onListFragmentScroll(int firstVisibleItem, int visibleItemCount,
                                     int totalItemCount) {
        // TODO: No-op for now. This should eventually show/hide the actionBar based on
        // interactions with the ListsFragments.
    }

    private boolean phoneIsInUse() {
        return TelecomUtil.isInCall(this);
    }

    private boolean canIntentBeHandled(Intent intent) {
        final PackageManager packageManager = getPackageManager();
        final List<ResolveInfo> resolveInfo = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return resolveInfo != null && resolveInfo.size() > 0;
    }

    /**
     * Called when the user has long-pressed a contact tile to start a drag operation.
     */
    @Override
    public void onDragStarted(int x, int y, PhoneFavoriteSquareTileView view) {
        mListsFragment.showRemoveView(true);
    }

    @Override
    public void onDragHovered(int x, int y, PhoneFavoriteSquareTileView view) {
    }

    /**
     * Called when the user has released a contact tile after long-pressing it.
     */
    @Override
    public void onDragFinished(int x, int y) {
        mListsFragment.showRemoveView(false);
    }

    @Override
    public void onDroppedOnRemove() {}

    /**
     * Allows the SpeedDialFragment to attach the drag controller to mRemoveViewContainer
     * once it has been attached to the activity.
     */
    @Override
    public void setDragDropController(DragDropController dragController) {
        mDragDropController = dragController;
        mListsFragment.getRemoveView().setDragDropController(dragController);
    }

    /**
     * Implemented to satisfy {@link SpeedDialFragment.HostInterface}
     */
    @Override
    public void showAllContactsTab() {
        if (mListsFragment != null) {
            mListsFragment.showTab(ListsFragment.TAB_INDEX_ALL_CONTACTS);
        }
    }

    /**
     * Implemented to satisfy {@link CallLogFragment.HostInterface}
     */
    @Override
    public void showDialpad() {
        showDialpadFragment(true);
    }

    @Override
    public void onPickDataUri(Uri dataUri, boolean isVideoCall, int callInitiationType) {
        mClearSearchOnPause = true;
        PhoneNumberInteraction.startInteractionForPhoneCall(
                DialtactsActivity.this, dataUri, isVideoCall, callInitiationType);
    }

    @Override
    public void onPickPhoneNumber(String phoneNumber, boolean isVideoCall, int callInitiationType) {
        if (phoneNumber == null) {
            // Invalid phone number, but let the call go through so that InCallUI can show
            // an error message.
            phoneNumber = "";
        }

        final Intent intent = new CallIntentBuilder(phoneNumber)
                .setIsVideoCall(isVideoCall)
                .setCallInitiationType(callInitiationType)
                .build();

        DialerUtils.startActivityWithErrorToast(this, intent);
        mClearSearchOnPause = true;
    }

    @Override
    public void onShortcutIntentCreated(Intent intent) {
        Log.w(TAG, "Unsupported intent has come (" + intent + "). Ignoring.");
    }

    @Override
    public void onHomeInActionBarSelected() {
        exitSearchUi();
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        int tabIndex = mListsFragment.getCurrentTabIndex();

        // Scroll the button from center to end when moving from the Speed Dial to Call History tab.
        // In RTL, scroll when the current tab is Call History instead, since the order of the tabs
        // is reversed and the ViewPager returns the left tab position during scroll.
        boolean isRtl = DialerUtils.isRtl();
        if (!isRtl && tabIndex == ListsFragment.TAB_INDEX_SPEED_DIAL && !mIsLandscape) {
            mFloatingActionButtonController.onPageScrolled(positionOffset);
        } else if (isRtl && tabIndex == ListsFragment.TAB_INDEX_HISTORY && !mIsLandscape) {
            mFloatingActionButtonController.onPageScrolled(1 - positionOffset);
        } else if (tabIndex != ListsFragment.TAB_INDEX_SPEED_DIAL) {
            mFloatingActionButtonController.onPageScrolled(1);
        }
    }

    @Override
    public void onPageSelected(int position) {
        updateMissedCalls();
        int tabIndex = mListsFragment.getCurrentTabIndex();
        mPreviouslySelectedTabIndex = tabIndex;
        if (tabIndex == ListsFragment.TAB_INDEX_ALL_CONTACTS) {
            mFloatingActionButtonController.changeIcon(
                    getResources().getDrawable(R.drawable.ic_person_add_24dp),
                    getResources().getString(R.string.search_shortcut_create_new_contact));
        } else {
            mFloatingActionButtonController.changeIcon(
                    getResources().getDrawable(R.drawable.fab_ic_dial),
                    getResources().getString(R.string.action_menu_dialpad_button));
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    @Override
    public boolean isActionBarShowing() {
        return mActionBarController.isActionBarShowing();
    }

    @Override
    public ActionBarController getActionBarController() {
        return mActionBarController;
    }

    @Override
    public boolean isDialpadShown() {
        return mIsDialpadShown;
    }

    @Override
    public int getDialpadHeight() {
        if (mDialpadFragment != null) {
            return mDialpadFragment.getDialpadHeight();
        }
        return 0;
    }

    @Override
    public int getActionBarHideOffset() {
        return getSupportActionBar().getHideOffset();
    }

    @Override
    public void setActionBarHideOffset(int offset) {
        getSupportActionBar().setHideOffset(offset);
    }

    @Override
    public int getActionBarHeight() {
        return mActionBarHeight;
    }

    private int getFabAlignment() {
        if (!mIsLandscape && !isInSearchUi() &&
                mListsFragment.getCurrentTabIndex() == ListsFragment.TAB_INDEX_SPEED_DIAL) {
            return FloatingActionButtonController.ALIGN_MIDDLE;
        }
        return FloatingActionButtonController.ALIGN_END;
    }

    private void updateMissedCalls() {
        if (mPreviouslySelectedTabIndex == ListsFragment.TAB_INDEX_HISTORY) {
            mListsFragment.markMissedCallsAsReadAndRemoveNotifications();
        }
    }
}
