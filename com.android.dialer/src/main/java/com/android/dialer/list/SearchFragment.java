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
package com.android.dialer.list;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.DialogFragment;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Space;

import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.common.list.PhoneNumberPickerFragment;
import com.android.contacts.common.util.PermissionsUtil;
import com.android.contacts.common.util.ViewUtil;
import com.android.dialer.R;
import com.android.dialer.dialpad.DialpadFragment.ErrorDialogFragment;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.IntentUtil;
import com.android.dialer.widget.EmptyContentView;
import com.android.phone.common.animation.AnimUtils;

public class SearchFragment extends PhoneNumberPickerFragment {
    private static final String TAG  = SearchFragment.class.getSimpleName();

    private OnListFragmentScrolledListener mActivityScrollListener;
    private View.OnTouchListener mActivityOnTouchListener;

    /*
     * Stores the untouched user-entered string that is used to populate the add to contacts
     * intent.
     */
    private String mAddToContactNumber;
    private int mActionBarHeight;
    private int mShadowHeight;
    private int mPaddingTop;
    private int mShowDialpadDuration;
    private int mHideDialpadDuration;

    /**
     * Used to resize the list view containing search results so that it fits the available space
     * above the dialpad. Does not have a user-visible effect in regular touch usage (since the
     * dialpad hides that portion of the ListView anyway), but improves usability in accessibility
     * mode.
     */
    private Space mSpacer;

    private HostInterface mActivity;

    protected EmptyContentView mEmptyView;

    public interface HostInterface {
        public boolean isActionBarShowing();
        public boolean isDialpadShown();
        public int getDialpadHeight();
        public int getActionBarHideOffset();
        public int getActionBarHeight();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        setQuickContactEnabled(true);
        setAdjustSelectionBoundsEnabled(false);
        setDarkTheme(false);
        setPhotoPosition(ContactListItemView.getDefaultPhotoPosition(false /* opposite */));
        setUseCallableUri(true);

        try {
            mActivityScrollListener = (OnListFragmentScrolledListener) activity;
        } catch (ClassCastException e) {
            Log.d(TAG, activity.toString() + " doesn't implement OnListFragmentScrolledListener. " +
                    "Ignoring.");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (isSearchMode()) {
            getAdapter().setHasHeader(0, false);
        }

        mActivity = (HostInterface) getActivity();

        final Resources res = getResources();
        mActionBarHeight = mActivity.getActionBarHeight();
        mShadowHeight  = res.getDrawable(R.drawable.search_shadow).getIntrinsicHeight();
        mPaddingTop = res.getDimensionPixelSize(R.dimen.search_list_padding_top);
        mShowDialpadDuration = res.getInteger(R.integer.dialpad_slide_in_duration);
        mHideDialpadDuration = res.getInteger(R.integer.dialpad_slide_out_duration);

        final View parentView = getView();

        final ListView listView = getListView();

        if (mEmptyView == null) {
            mEmptyView = new EmptyContentView(getActivity());
            ((ViewGroup) getListView().getParent()).addView(mEmptyView);
            getListView().setEmptyView(mEmptyView);
            setupEmptyView();
        }

        listView.setBackgroundColor(res.getColor(R.color.background_dialer_results));
        listView.setClipToPadding(false);
        setVisibleScrollbarEnabled(false);

        //Turn of accessibility live region as the list constantly update itself and spam messages.
        listView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);
        ContentChangedFilter.addToParent(listView);

        listView.setOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (mActivityScrollListener != null) {
                    mActivityScrollListener.onListFragmentScrollStateChange(scrollState);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                    int totalItemCount) {
            }
        });
        if (mActivityOnTouchListener != null) {
            listView.setOnTouchListener(mActivityOnTouchListener);
        }

        updatePosition(false /* animate */);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewUtil.addBottomPaddingToListViewForFab(getListView(), getResources());
    }

    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        Animator animator = null;
        if (nextAnim != 0) {
            animator = AnimatorInflater.loadAnimator(getActivity(), nextAnim);
        }
        if (animator != null) {
            final View view = getView();
            final int oldLayerType = view.getLayerType();
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setLayerType(oldLayerType, null);
                }
            });
        }
        return animator;
    }

    @Override
    protected void setSearchMode(boolean flag) {
        super.setSearchMode(flag);
        // This hides the "All contacts with phone numbers" header in the search fragment
        final ContactEntryListAdapter adapter = getAdapter();
        if (adapter != null) {
            adapter.setHasHeader(0, false);
        }
    }

    public void setAddToContactNumber(String addToContactNumber) {
        mAddToContactNumber = addToContactNumber;
    }

    /**
     * Return true if phone number is prohibited by a value -
     * (R.string.config_prohibited_phone_number_regexp) in the config files. False otherwise.
     */
    public boolean checkForProhibitedPhoneNumber(String number) {
        // Regular expression prohibiting manual phone call. Can be empty i.e. "no rule".
        String prohibitedPhoneNumberRegexp = getResources().getString(
            R.string.config_prohibited_phone_number_regexp);

        // "persist.radio.otaspdial" is a temporary hack needed for one carrier's automated
        // test equipment.
        if (number != null
                && !TextUtils.isEmpty(prohibitedPhoneNumberRegexp)
                && number.matches(prohibitedPhoneNumberRegexp)) {
            Log.d(TAG, "The phone number is prohibited explicitly by a rule.");
            if (getActivity() != null) {
                DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
                        R.string.dialog_phone_call_prohibited_message);
                dialogFragment.show(getFragmentManager(), "phone_prohibited_dialog");
            }

            return true;
        }
        return false;
    }

    @Override
    protected ContactEntryListAdapter createListAdapter() {
        DialerPhoneNumberListAdapter adapter = new DialerPhoneNumberListAdapter(getActivity());
        adapter.setDisplayPhotos(true);
        adapter.setUseCallableUri(super.usesCallableUri());
        adapter.setListener(this);
        return adapter;
    }

    @Override
    protected void onItemClick(int position, long id) {
        final DialerPhoneNumberListAdapter adapter = (DialerPhoneNumberListAdapter) getAdapter();
        final int shortcutType = adapter.getShortcutTypeFromPosition(position);
        final OnPhoneNumberPickerActionListener listener;
        final Intent intent;
        final String number;

        Log.i(TAG, "onItemClick: shortcutType=" + shortcutType);

        switch (shortcutType) {
            case DialerPhoneNumberListAdapter.SHORTCUT_INVALID:
                super.onItemClick(position, id);
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CALL:
                number = adapter.getQueryString();
                listener = getOnPhoneNumberPickerListener();
                if (listener != null && !checkForProhibitedPhoneNumber(number)) {
                    listener.onPickPhoneNumber(number, false /* isVideoCall */,
                            getCallInitiationType(false /* isRemoteDirectory */));
                }
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_CREATE_NEW_CONTACT:
                number = TextUtils.isEmpty(mAddToContactNumber) ?
                        adapter.getFormattedQueryString() : mAddToContactNumber;
                intent = IntentUtil.getNewContactIntent(number);
                DialerUtils.startActivityWithErrorToast(getActivity(), intent);
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_ADD_TO_EXISTING_CONTACT:
                number = TextUtils.isEmpty(mAddToContactNumber) ?
                        adapter.getFormattedQueryString() : mAddToContactNumber;
                intent = IntentUtil.getAddToExistingContactIntent(number);
                DialerUtils.startActivityWithErrorToast(getActivity(), intent,
                        R.string.add_contact_not_available);
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_SEND_SMS_MESSAGE:
                number = adapter.getFormattedQueryString();
                intent = IntentUtil.getSendSmsIntent(number);
                DialerUtils.startActivityWithErrorToast(getActivity(), intent);
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_MAKE_VIDEO_CALL:
                number = TextUtils.isEmpty(mAddToContactNumber) ?
                        adapter.getQueryString() : mAddToContactNumber;
                listener = getOnPhoneNumberPickerListener();
                if (listener != null && !checkForProhibitedPhoneNumber(number)) {
                    listener.onPickPhoneNumber(number, true /* isVideoCall */,
                            getCallInitiationType(false /* isRemoteDirectory */));
                }
                break;
        }
    }

    /**
     * Updates the position and padding of the search fragment, depending on whether the dialpad is
     * shown. This can be optionally animated.
     * @param animate
     */
    public void updatePosition(boolean animate) {
        // Use negative shadow height instead of 0 to account for the 9-patch's shadow.
        int startTranslationValue =
                mActivity.isDialpadShown() ? mActionBarHeight - mShadowHeight : -mShadowHeight;
        int endTranslationValue = 0;
        // Prevents ListView from being translated down after a rotation when the ActionBar is up.
        if (animate || mActivity.isActionBarShowing()) {
            endTranslationValue =
                    mActivity.isDialpadShown() ? 0 : mActionBarHeight - mShadowHeight;
        }
        if (animate) {
            // If the dialpad will be shown, then this animation involves sliding the list up.
            final boolean slideUp = mActivity.isDialpadShown();

            Interpolator interpolator = slideUp ? AnimUtils.EASE_IN : AnimUtils.EASE_OUT ;
            int duration = slideUp ? mShowDialpadDuration : mHideDialpadDuration;
            getView().setTranslationY(startTranslationValue);
            getView().animate()
                    .translationY(endTranslationValue)
                    .setInterpolator(interpolator)
                    .setDuration(duration)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            if (!slideUp) {
                                resizeListView();
                            }
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (slideUp) {
                                resizeListView();
                            }
                        }
                    });

        } else {
            getView().setTranslationY(endTranslationValue);
            resizeListView();
        }

        // There is padding which should only be applied when the dialpad is not shown.
        int paddingTop = mActivity.isDialpadShown() ? 0 : mPaddingTop;
        final ListView listView = getListView();
        listView.setPaddingRelative(
                listView.getPaddingStart(),
                paddingTop,
                listView.getPaddingEnd(),
                listView.getPaddingBottom());
    }

    public void resizeListView() {
        if (mSpacer == null) {
            return;
        }
        int spacerHeight = mActivity.isDialpadShown() ? mActivity.getDialpadHeight() : 0;
        if (spacerHeight != mSpacer.getHeight()) {
            final LinearLayout.LayoutParams lp =
                    (LinearLayout.LayoutParams) mSpacer.getLayoutParams();
            lp.height = spacerHeight;
            mSpacer.setLayoutParams(lp);
        }
    }

    @Override
    protected void startLoading() {
        if (getActivity() == null) {
            return;
        }

        if (PermissionsUtil.hasContactsPermissions(getActivity())) {
            super.startLoading();
        } else if (TextUtils.isEmpty(getQueryString())) {
            // Clear out any existing call shortcuts.
            final DialerPhoneNumberListAdapter adapter =
                    (DialerPhoneNumberListAdapter) getAdapter();
            adapter.disableAllShortcuts();
        } else {
            // The contact list is not going to change (we have no results since permissions are
            // denied), but the shortcuts might because of the different query, so update the
            // list.
            getAdapter().notifyDataSetChanged();
        }

        setupEmptyView();
    }

    public void setOnTouchListener(View.OnTouchListener onTouchListener) {
        mActivityOnTouchListener = onTouchListener;
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        final LinearLayout parent = (LinearLayout) super.inflateView(inflater, container);
        final int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            mSpacer = new Space(getActivity());
            parent.addView(mSpacer,
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0));
        }
        return parent;
    }

    protected void setupEmptyView() {}
}
