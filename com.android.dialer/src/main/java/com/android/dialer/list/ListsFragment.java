package com.android.dialer.list;

import android.animation.LayoutTransition;
import android.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Trace;
import android.preference.PreferenceManager;
import android.provider.CallLog.Calls;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;

import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.list.ViewPagerTabs;
import com.android.contacts.commonbind.analytics.AnalyticsUtil;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.calllog.CallLogFragment;
import com.android.dialer.calllog.CallLogQueryHandler;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.voicemail.VoicemailStatusHelper;
import com.android.dialer.voicemail.VoicemailStatusHelperImpl;
import com.android.dialer.widget.ActionBarController;
import com.android.dialerbind.ObjectFactory;

import java.util.ArrayList;

/**
 * Fragment that is used as the main screen of the Dialer.
 *
 * Contains a ViewPager that contains various contact lists like the Speed Dial list and the
 * All Contacts list. This will also eventually contain the logic that allows sliding the
 * ViewPager containing the lists up above the search bar and pin it against the top of the
 * screen.
 */
public class ListsFragment extends Fragment
        implements ViewPager.OnPageChangeListener, CallLogQueryHandler.Listener {

    private static final boolean DEBUG = DialtactsActivity.DEBUG;
    private static final String TAG = "ListsFragment";

    public static final int TAB_INDEX_SPEED_DIAL = 0;
    public static final int TAB_INDEX_RECENTS = 1;
    public static final int TAB_INDEX_ALL_CONTACTS = 2;
    public static final int TAB_INDEX_VOICEMAIL = 3;

    public static final int TAB_COUNT_DEFAULT = 3;
    public static final int TAB_COUNT_WITH_VOICEMAIL = 4;

    private static final int MAX_RECENTS_ENTRIES = 20;
    // Oldest recents entry to display is 2 weeks old.
    private static final long OLDEST_RECENTS_DATE = 1000L * 60 * 60 * 24 * 14;

    private static final String PREF_KEY_HAS_ACTIVE_VOICEMAIL_PROVIDER =
            "has_active_voicemail_provider";

    public interface HostInterface {
        public ActionBarController getActionBarController();
    }

    private ActionBar mActionBar;
    private ViewPager mViewPager;
    private ViewPagerTabs mViewPagerTabs;
    private ViewPagerAdapter mViewPagerAdapter;
    private RemoveView mRemoveView;
    private View mRemoveViewContent;

    private SpeedDialFragment mSpeedDialFragment;
    private CallLogFragment mRecentsFragment;
    private AllContactsFragment mAllContactsFragment;
    private CallLogFragment mVoicemailFragment;

    private SharedPreferences mPrefs;
    private boolean mHasActiveVoicemailProvider;
    private boolean mHasFetchedVoicemailStatus;
    private boolean mShowVoicemailTabAfterVoicemailStatusIsFetched;

    private VoicemailStatusHelper mVoicemailStatusHelper;
    private ArrayList<OnPageChangeListener> mOnPageChangeListeners =
            new ArrayList<OnPageChangeListener>();

    private String[] mTabTitles;
    private int[] mTabIcons;

    /**
     * The position of the currently selected tab.
     */
    private int mTabIndex = TAB_INDEX_SPEED_DIAL;

    public class ViewPagerAdapter extends FragmentPagerAdapter {
        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public long getItemId(int position) {
            return getRtlPosition(position);
        }

        @Override
        public Fragment getItem(int position) {
            switch (getRtlPosition(position)) {
                case TAB_INDEX_SPEED_DIAL:
                    mSpeedDialFragment = new SpeedDialFragment();
                    return mSpeedDialFragment;
                case TAB_INDEX_RECENTS:
                    mRecentsFragment = new CallLogFragment(CallLogQueryHandler.CALL_TYPE_ALL,
                            MAX_RECENTS_ENTRIES, System.currentTimeMillis() - OLDEST_RECENTS_DATE);
                    return mRecentsFragment;
                case TAB_INDEX_ALL_CONTACTS:
                    mAllContactsFragment = new AllContactsFragment();
                    return mAllContactsFragment;
                case TAB_INDEX_VOICEMAIL:
                    mVoicemailFragment = new CallLogFragment(Calls.VOICEMAIL_TYPE);
                    return mVoicemailFragment;
            }
            throw new IllegalStateException("No fragment at position " + position);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            // On rotation the FragmentManager handles rotation. Therefore getItem() isn't called.
            // Copy the fragments that the FragmentManager finds so that we can store them in
            // instance variables for later.
            final Fragment fragment =
                    (Fragment) super.instantiateItem(container, position);
            if (fragment instanceof SpeedDialFragment) {
                mSpeedDialFragment = (SpeedDialFragment) fragment;
            } else if (fragment instanceof CallLogFragment && position == TAB_INDEX_RECENTS) {
                mRecentsFragment = (CallLogFragment) fragment;
            } else if (fragment instanceof AllContactsFragment) {
                mAllContactsFragment = (AllContactsFragment) fragment;
            } else if (fragment instanceof CallLogFragment && position == TAB_INDEX_VOICEMAIL) {
                mVoicemailFragment = (CallLogFragment) fragment;
            }
            return fragment;
        }

        @Override
        public int getCount() {
            return mHasActiveVoicemailProvider ? TAB_COUNT_WITH_VOICEMAIL : TAB_COUNT_DEFAULT;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mTabTitles[position];
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Trace.beginSection(TAG + " onCreate");
        super.onCreate(savedInstanceState);

        Trace.beginSection(TAG + " getCurrentCountryIso");
        final String currentCountryIso = GeoUtil.getCurrentCountryIso(getActivity());
        Trace.endSection();

        mVoicemailStatusHelper = new VoicemailStatusHelperImpl();
        mHasFetchedVoicemailStatus = false;

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mHasActiveVoicemailProvider = mPrefs.getBoolean(
                PREF_KEY_HAS_ACTIVE_VOICEMAIL_PROVIDER, false);

        Trace.endSection();
    }

    @Override
    public void onResume() {
        Trace.beginSection(TAG + " onResume");
        super.onResume();
        mActionBar = getActivity().getActionBar();
        if (getUserVisibleHint()) {
            sendScreenViewForCurrentPosition();
        }

        // Fetch voicemail status to determine if we should show the voicemail tab.
        CallLogQueryHandler callLogQueryHandler =
                new CallLogQueryHandler(getActivity(), getActivity().getContentResolver(), this);
        callLogQueryHandler.fetchVoicemailStatus();
        Trace.endSection();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Trace.beginSection(TAG + " onCreateView");
        Trace.beginSection(TAG + " inflate view");
        final View parentView = inflater.inflate(R.layout.lists_fragment, container, false);
        Trace.endSection();
        Trace.beginSection(TAG + " setup views");
        mViewPager = (ViewPager) parentView.findViewById(R.id.lists_pager);
        mViewPagerAdapter = new ViewPagerAdapter(getChildFragmentManager());
        mViewPager.setAdapter(mViewPagerAdapter);
        mViewPager.setOffscreenPageLimit(TAB_COUNT_WITH_VOICEMAIL - 1);
        mViewPager.setOnPageChangeListener(this);
        showTab(TAB_INDEX_SPEED_DIAL);

        mTabTitles = new String[TAB_COUNT_WITH_VOICEMAIL];
        mTabTitles[TAB_INDEX_SPEED_DIAL] = getResources().getString(R.string.tab_speed_dial);
        mTabTitles[TAB_INDEX_RECENTS] = getResources().getString(R.string.tab_recents);
        mTabTitles[TAB_INDEX_ALL_CONTACTS] = getResources().getString(R.string.tab_all_contacts);
        mTabTitles[TAB_INDEX_VOICEMAIL] = getResources().getString(R.string.tab_voicemail);

        mTabIcons = new int[TAB_COUNT_WITH_VOICEMAIL];
        mTabIcons[TAB_INDEX_SPEED_DIAL] = R.drawable.tab_speed_dial;
        mTabIcons[TAB_INDEX_RECENTS] = R.drawable.tab_recents;
        mTabIcons[TAB_INDEX_ALL_CONTACTS] = R.drawable.tab_contacts;
        mTabIcons[TAB_INDEX_VOICEMAIL] = R.drawable.tab_voicemail;

        mViewPagerTabs = (ViewPagerTabs) parentView.findViewById(R.id.lists_pager_header);
        mViewPagerTabs.setTabIcons(mTabIcons);
        mViewPagerTabs.setViewPager(mViewPager);
        addOnPageChangeListener(mViewPagerTabs);

        mRemoveView = (RemoveView) parentView.findViewById(R.id.remove_view);
        mRemoveViewContent = parentView.findViewById(R.id.remove_view_content);

        Trace.endSection();
        Trace.endSection();
        return parentView;
    }

    public void addOnPageChangeListener(OnPageChangeListener onPageChangeListener) {
        if (!mOnPageChangeListeners.contains(onPageChangeListener)) {
            mOnPageChangeListeners.add(onPageChangeListener);
        }
    }

    /**
     * Shows the tab with the specified index. If the voicemail tab index is specified, but the
     * voicemail status hasn't been fetched, it will try to show the tab after the voicemail status
     * has been fetched.
     */
    public void showTab(int index) {
        if (index == TAB_INDEX_VOICEMAIL) {
            if (mHasActiveVoicemailProvider) {
                mViewPager.setCurrentItem(getRtlPosition(TAB_INDEX_VOICEMAIL));
            } else if (!mHasFetchedVoicemailStatus) {
                // Try to show the voicemail tab after the voicemail status returns.
                mShowVoicemailTabAfterVoicemailStatusIsFetched = true;
            }
        } else {
            mViewPager.setCurrentItem(getRtlPosition(index));
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        mTabIndex = getRtlPosition(position);

        final int count = mOnPageChangeListeners.size();
        for (int i = 0; i < count; i++) {
            mOnPageChangeListeners.get(i).onPageScrolled(position, positionOffset,
                    positionOffsetPixels);
        }
    }

    @Override
    public void onPageSelected(int position) {
        mTabIndex = getRtlPosition(position);

        // Show the tab which has been selected instead.
        mShowVoicemailTabAfterVoicemailStatusIsFetched = false;

        final int count = mOnPageChangeListeners.size();
        for (int i = 0; i < count; i++) {
            mOnPageChangeListeners.get(i).onPageSelected(position);
        }
        sendScreenViewForCurrentPosition();
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        final int count = mOnPageChangeListeners.size();
        for (int i = 0; i < count; i++) {
            mOnPageChangeListeners.get(i).onPageScrollStateChanged(state);
        }
    }

    @Override
    public void onVoicemailStatusFetched(Cursor statusCursor) {
        mHasFetchedVoicemailStatus = true;

        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }

        // Update mHasActiveVoicemailProvider, which controls the number of tabs displayed.
        boolean hasActiveVoicemailProvider =
                mVoicemailStatusHelper.getNumberActivityVoicemailSources(statusCursor) > 0;
        if (hasActiveVoicemailProvider != mHasActiveVoicemailProvider) {
            mHasActiveVoicemailProvider = hasActiveVoicemailProvider;
            mViewPagerAdapter.notifyDataSetChanged();
            mViewPagerTabs.setViewPager(mViewPager);

            mPrefs.edit()
                    .putBoolean(PREF_KEY_HAS_ACTIVE_VOICEMAIL_PROVIDER, hasActiveVoicemailProvider)
                    .commit();
        }

        if (mHasActiveVoicemailProvider && mShowVoicemailTabAfterVoicemailStatusIsFetched) {
            mShowVoicemailTabAfterVoicemailStatusIsFetched = false;
            showTab(TAB_INDEX_VOICEMAIL);
        }
    }

    @Override
    public boolean onCallsFetched(Cursor statusCursor) {
        // Return false; did not take ownership of cursor
        return false;
    }

    public int getCurrentTabIndex() {
        return mTabIndex;
    }

    public void showRemoveView(boolean show) {
        mRemoveViewContent.setVisibility(show ? View.VISIBLE : View.GONE);
        mRemoveView.setAlpha(show ? 0 : 1);
        mRemoveView.animate().alpha(show ? 1 : 0).start();
    }

    public boolean shouldShowActionBar() {
        // TODO: Update this based on scroll state.
        return mActionBar != null;
    }

    public SpeedDialFragment getSpeedDialFragment() {
        return mSpeedDialFragment;
    }

    public RemoveView getRemoveView() {
        return mRemoveView;
    }

    public int getTabCount() {
        return mViewPagerAdapter.getCount();
    }

    private int getRtlPosition(int position) {
        if (DialerUtils.isRtl()) {
            return mViewPagerAdapter.getCount() - 1 - position;
        }
        return position;
    }

    public void sendScreenViewForCurrentPosition() {
        if (!isResumed()) {
            return;
        }

        String fragmentName;
        switch (getCurrentTabIndex()) {
            case TAB_INDEX_SPEED_DIAL:
                fragmentName = SpeedDialFragment.class.getSimpleName();
                break;
            case TAB_INDEX_RECENTS:
                fragmentName = CallLogFragment.class.getSimpleName() + "#Recents";
                break;
            case TAB_INDEX_ALL_CONTACTS:
                fragmentName = AllContactsFragment.class.getSimpleName();
                break;
            case TAB_INDEX_VOICEMAIL:
                fragmentName = CallLogFragment.class.getSimpleName() + "#Voicemail";
            default:
                return;
        }
        AnalyticsUtil.sendScreenView(fragmentName, getActivity(), null);
    }
}
