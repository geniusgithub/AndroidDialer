package com.android.dialer.test;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.android.dialer.R;

public class TActivity extends Activity implements View.OnClickListener{

    private final static String TAG = "TActivity";

    private Button mShow;
    private Button mHide;
    protected GlobalDialpadManager mIIdeafriendActivitInterface;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "TActivity onCreate");
        setContentView(R.layout.activity_t);
        init();

        mIIdeafriendActivitInterface = createIdeafriendActivitInterface();
        mIIdeafriendActivitInterface.onCreate(savedInstanceState);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig){
        super.onConfigurationChanged(newConfig);
        Log.i(TAG, "onConfigurationChanged");
        mIIdeafriendActivitInterface.updateDialpadViewShowStatusWhenConfigure();
    }


    private  GlobalDialpadManager createIdeafriendActivitInterface(){
        return new GlobalDialpadManager(this, R.id.dialtacts_container);
    }

    private void init(){
        mShow = (Button) findViewById(R.id.buttonShow);
        mShow.setOnClickListener(this);
        mHide = (Button) findViewById(R.id.buttonHide);
        mHide.setOnClickListener(this);
    }

    public boolean isOrientationPortatit(){
        boolean flag = (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT);
        return flag;
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
        if (isOrientationPortatit()){
            mIIdeafriendActivitInterface.showPortatitFragment(true);
            mIIdeafriendActivitInterface.setPortraitDialpadViewShowStatus(true);
        }else{
            mIIdeafriendActivitInterface.showHorizontalFragment(true);
            mIIdeafriendActivitInterface.setHorizontalDialpadViewShowStatus(true);
        }

    }

    private void hide(){
        if (isOrientationPortatit()){
            mIIdeafriendActivitInterface.hidePortatitFragment(true);
            mIIdeafriendActivitInterface.setPortraitDialpadViewShowStatus(false);
        }else{
            mIIdeafriendActivitInterface.hideHorizontalFragment(true);
            mIIdeafriendActivitInterface.setHorizontalDialpadViewShowStatus(false);
        }
    }






}
