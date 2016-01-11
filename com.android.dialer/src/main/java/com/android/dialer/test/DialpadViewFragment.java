package com.android.dialer.test;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ListView;

import com.android.dialer.R;
import com.android.dialer.util.DialerUtils;
import com.android.phone.common.animation.AnimUtils;
import com.android.phone.common.animation.AnimationListenerAdapter;
import com.android.phone.common.dialpad.DialpadView;

public class DialpadViewFragment extends Fragment{

    private final static String TAG = "DialpadViewFragment";

    private boolean mAnimate = false;
    private DialpadView mDialpadView;


    private Animation mSlideIn;
    private Animation mSlideOut;

    AnimationListenerAdapter mSlideInListener = new AnimationListenerAdapter() {
        @Override
        public void onAnimationEnd(Animation animation) {
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
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);


    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        final View fragmentView = inflater.inflate(R.layout.dialpad_fragment, container, false);

        ListView mDialpadChooser = (ListView) fragmentView.findViewById(R.id.dialpadChooser);
        mDialpadChooser.setVisibility(View.GONE);

        mDialpadView = (DialpadView)fragmentView.findViewById(R.id.dialpad_view);

        return fragmentView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initAnim(getActivity());
    }


    private void initAnim(Context context){
         boolean mIsLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

        final boolean isLayoutRtl = DialerUtils.isRtl();
        if (mIsLandscape) {
            mSlideIn = AnimationUtils.loadAnimation(context,
                    isLayoutRtl ? R.anim.dialpad_slide_in_left : R.anim.dialpad_slide_in_right);
            mSlideOut = AnimationUtils.loadAnimation(context,
                    isLayoutRtl ? R.anim.dialpad_slide_out_left : R.anim.dialpad_slide_out_right);
        } else {
            mSlideIn = AnimationUtils.loadAnimation(context, R.anim.dialpad_slide_in_bottom);
            mSlideOut = AnimationUtils.loadAnimation(context, R.anim.dialpad_slide_out_bottom);
        }

        mSlideIn.setInterpolator(AnimUtils.EASE_IN);
        mSlideOut.setInterpolator(AnimUtils.EASE_OUT);

        mSlideIn.setAnimationListener(mSlideInListener);
        mSlideOut.setAnimationListener(mSlideOutListener);
    }
    public void setAnimate(boolean value) {
        mAnimate = value;
    }

    public boolean getAnimate() {
        return mAnimate;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        final Activity parentActivity = getActivity();
        Log.i(TAG, "onHiddenChanged parentActivity = " + parentActivity);
        if (parentActivity == null) return;

        if (!hidden) {
            if (mAnimate) {
                mDialpadView.animateShow();
                getView().startAnimation(mSlideIn);
            }else{

            }
        }
    }

    public void showDialpadFragment(boolean animate) {
    final FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.show(this);
        setAnimate(animate);
        ft.commit();
    }

    public void hideDialpadFragment(boolean animate) {
        if (animate){
            getView().startAnimation(mSlideOut);
        }else{
            commitDialpadFragmentHide();
        }
    }

    private void commitDialpadFragmentHide() {
        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.hide(this);
        ft.commit();
    }
}
