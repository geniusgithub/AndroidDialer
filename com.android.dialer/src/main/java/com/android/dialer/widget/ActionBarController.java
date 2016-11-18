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
package com.android.dialer.widget;

import com.google.common.annotations.VisibleForTesting;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.os.Bundle;
import android.util.Log;

import com.android.dialer.DialtactsActivity;
import com.android.phone.common.animation.AnimUtils.AnimationCallback;

/**
 * Controls the various animated properties of the actionBar: showing/hiding, fading/revealing,
 * and collapsing/expanding, and assigns suitable properties to the actionBar based on the
 * current state of the UI.
 */
public class ActionBarController {
    public static final boolean DEBUG = DialtactsActivity.DEBUG;
    public static final String TAG = "ActionBarController";
    private static final String KEY_IS_SLID_UP = "key_actionbar_is_slid_up";
    private static final String KEY_IS_FADED_OUT = "key_actionbar_is_faded_out";
    private static final String KEY_IS_EXPANDED = "key_actionbar_is_expanded";

    private ActivityUi mActivityUi;
    private SearchEditTextLayout mSearchBox;

    private boolean mIsActionBarSlidUp;

    private final AnimationCallback mFadeOutCallback = new AnimationCallback() {
        @Override
        public void onAnimationEnd() {
            slideActionBar(true /* slideUp */, false /* animate */);
        }

        @Override
        public void onAnimationCancel() {
            slideActionBar(true /* slideUp */, false /* animate */);
        }
    };

    public interface ActivityUi {
        public boolean isInSearchUi();
        public boolean hasSearchQuery();
        public boolean shouldShowActionBar();
        public int getActionBarHeight();
        public int getActionBarHideOffset();
        public void setActionBarHideOffset(int offset);
    }

    public ActionBarController(ActivityUi activityUi, SearchEditTextLayout searchBox) {
        mActivityUi = activityUi;
        mSearchBox = searchBox;
    }

    /**
     * @return Whether or not the action bar is currently showing (both slid down and visible)
     */
    public boolean isActionBarShowing() {
        return !mIsActionBarSlidUp && !mSearchBox.isFadedOut();
    }

    /**
     * Called when the user has tapped on the collapsed search box, to start a new search query.
     */
    public void onSearchBoxTapped() {
        if (DEBUG) {
            Log.d(TAG, "OnSearchBoxTapped: isInSearchUi " + mActivityUi.isInSearchUi());
        }
        if (!mActivityUi.isInSearchUi()) {
            mSearchBox.expand(true /* animate */, true /* requestFocus */);
        }
    }

    /**
     * Called when search UI has been exited for some reason.
     */
    public void onSearchUiExited() {
        if (DEBUG) {
            Log.d(TAG, "OnSearchUIExited: isExpanded " + mSearchBox.isExpanded()
                    + " isFadedOut: " + mSearchBox.isFadedOut()
                    + " shouldShowActionBar: " + mActivityUi.shouldShowActionBar());
        }
        if (mSearchBox.isExpanded()) {
            mSearchBox.collapse(true /* animate */);
        }
        if (mSearchBox.isFadedOut()) {
            mSearchBox.fadeIn();
        }

        if (mActivityUi.shouldShowActionBar()) {
            slideActionBar(false /* slideUp */, false /* animate */);
        } else {
            slideActionBar(true /* slideUp */, false /* animate */);
        }
    }

    /**
     * Called to indicate that the user is trying to hide the dialpad. Should be called before
     * any state changes have actually occurred.
     */
    public void onDialpadDown() {
        if (DEBUG) {
            Log.d(TAG, "OnDialpadDown: isInSearchUi " + mActivityUi.isInSearchUi()
                    + " hasSearchQuery: " + mActivityUi.hasSearchQuery()
                    + " isFadedOut: " + mSearchBox.isFadedOut()
                    + " isExpanded: " + mSearchBox.isExpanded());
        }
        if (mActivityUi.isInSearchUi()) {
            if (mActivityUi.hasSearchQuery()) {
                if (mSearchBox.isFadedOut()) {
                    mSearchBox.setVisible(true);
                }
                if (!mSearchBox.isExpanded()) {
                    mSearchBox.expand(false /* animate */, false /* requestFocus */);
                }
                slideActionBar(false /* slideUp */, true /* animate */);
            } else {
                mSearchBox.fadeIn();
            }
        }
    }

    /**
     * Called to indicate that the user is trying to show the dialpad. Should be called before
     * any state changes have actually occurred.
     */
    public void onDialpadUp() {
        if (DEBUG) {
            Log.d(TAG, "OnDialpadUp: isInSearchUi " + mActivityUi.isInSearchUi());
        }
        if (mActivityUi.isInSearchUi()) {
            slideActionBar(true /* slideUp */, true /* animate */);
        } else {
            // From the lists fragment
            mSearchBox.fadeOut(mFadeOutCallback);
        }
    }

    public void slideActionBar(boolean slideUp, boolean animate) {
        if (DEBUG) {
            Log.d(TAG, "Sliding actionBar - up: " + slideUp + " animate: " + animate);
        }
        if (animate) {
            ValueAnimator animator =
                    slideUp ? ValueAnimator.ofFloat(0, 1) : ValueAnimator.ofFloat(1, 0);
            animator.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    final float value = (float) animation.getAnimatedValue();
                    setHideOffset(
                            (int) (mActivityUi.getActionBarHeight() * value));
                }
            });
            animator.start();
        } else {
           setHideOffset(slideUp ? mActivityUi.getActionBarHeight() : 0);
        }
        mIsActionBarSlidUp = slideUp;
    }

    public void setAlpha(float alphaValue) {
        mSearchBox.animate().alpha(alphaValue).start();
    }

    public void setHideOffset(int offset) {
        mIsActionBarSlidUp = offset >= mActivityUi.getActionBarHeight();
        mActivityUi.setActionBarHideOffset(offset);
    }

    /**
     * @return The offset the action bar is being translated upwards by
     */
    public int getHideOffset() {
        return mActivityUi.getActionBarHideOffset();
    }

    public int getActionBarHeight() {
        return mActivityUi.getActionBarHeight();
    }

    /**
     * Saves the current state of the action bar into a provided {@link Bundle}
     */
    public void saveInstanceState(Bundle outState) {
        outState.putBoolean(KEY_IS_SLID_UP, mIsActionBarSlidUp);
        outState.putBoolean(KEY_IS_FADED_OUT, mSearchBox.isFadedOut());
        outState.putBoolean(KEY_IS_EXPANDED, mSearchBox.isExpanded());
    }

    /**
     * Restores the action bar state from a provided {@link Bundle}.
     */
    public void restoreInstanceState(Bundle inState) {
        mIsActionBarSlidUp = inState.getBoolean(KEY_IS_SLID_UP);

        final boolean isSearchBoxFadedOut = inState.getBoolean(KEY_IS_FADED_OUT);
        if (isSearchBoxFadedOut) {
            if (!mSearchBox.isFadedOut()) {
                mSearchBox.setVisible(false);
            }
        } else if (mSearchBox.isFadedOut()) {
                mSearchBox.setVisible(true);
        }

        final boolean isSearchBoxExpanded = inState.getBoolean(KEY_IS_EXPANDED);
        if (isSearchBoxExpanded) {
            if (!mSearchBox.isExpanded()) {
                mSearchBox.expand(false, false);
            }
        } else if (mSearchBox.isExpanded()) {
                mSearchBox.collapse(false);
        }
    }

    /**
     * This should be called after onCreateOptionsMenu has been called, when the actionbar has
     * been laid out and actually has a height.
     */
    public void restoreActionBarOffset() {
        slideActionBar(mIsActionBarSlidUp /* slideUp */, false /* animate */);
    }

    @VisibleForTesting
    public boolean getIsActionBarSlidUp() {
        return mIsActionBarSlidUp;
    }
}
