package com.android.dialer.test;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.android.dialer.R;

public class TActivity extends Activity implements View.OnClickListener{

    private final static String TAG = "TActivity";
    private static final String TAG_DIALPAD_FRAGMENT = "dialpad";

    private Button mShow;
    private Button mHide;
    private DialpadViewFragment mDialpadFragment;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_t);

        Log.i(TAG, "TActivity onCreate");
        init();
    }


    private void init(){
  //      mHorMng = new HorizontalWinManager(this);
        mShow = (Button) findViewById(R.id.buttonShow);
        mShow.setOnClickListener(this);
        mHide = (Button) findViewById(R.id.buttonHide);
        mHide.setOnClickListener(this);

        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment fragment = getFragmentManager().findFragmentByTag(TAG_DIALPAD_FRAGMENT);
        Log.i(TAG, "findFragmentByTag DialpadViewFragment = " + fragment);
        if (fragment != null){
            ft.remove(fragment);
        }
        ft.commit();

        showDialpadFragment(false);
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()){
            case R.id.buttonShow:
                show();
                break;
            case R.id.buttonHide:
                hide();
                break;
        }
    }

    private void show(){
        showDialpadFragment(true);
    }

    private void hide(){
        hideDialpadFragment(true);
    }



    private void showDialpadFragment(boolean animate) {
        Log.i(TAG, "showDialpadFragment");
        if (mDialpadFragment == null) {
            final FragmentTransaction ft = getFragmentManager().beginTransaction();
            mDialpadFragment = new DialpadViewFragment();
            ft.add(R.id.dialtacts_container, mDialpadFragment, TAG_DIALPAD_FRAGMENT);
            ft.commit();
        }

        mDialpadFragment.showDialpadFragment(true);

    }


    public void hideDialpadFragment(boolean animate) {
        Log.i(TAG, "hideDialpadFragment");
        if (mDialpadFragment == null) {
            Log.e(TAG, "mDialpadFragment = null, so return ");
            return ;
        }
        mDialpadFragment.hideDialpadFragment(true);
    }

}
