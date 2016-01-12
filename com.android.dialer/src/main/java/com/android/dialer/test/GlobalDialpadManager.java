package com.android.dialer.test;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;


public class GlobalDialpadManager implements IIdeafriendActivitInterface{

	private Activity mActivity;
    private int mContainerID;

    private DialpadViewFragment        mDialpadViewFragmentPortrait;
    public static final String         TAG_DIALPADVIEW_FRAGMENT_PORTRAIT = "tag_dialpadview_fragment_portrait";
    
    private DialpadViewFragment        mDialpadViewFragmentLandScape;
    public static final String         TAG_DIALPADVIEW_FRAGMENT_LANDSCAPE = "tag_dialpadview_fragment_landscape";


	private boolean isPortraitDialpadViewShow = false;
	private boolean isHorizontalDialpadViewShow = false;

	public GlobalDialpadManager(Activity parentActivity, int containerID){
		mActivity = parentActivity;
		mContainerID = containerID;
	}
    
	@Override
	public void onCreate(Bundle savedInstanceState) {
		removeDialpadviewFragment();
		addDialpadviewFragment();
	}

	@Override
	public void onResume() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onPause() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onBackPressed() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onNewIntent(Intent intent) {
		// TODO Auto-generated method stub
		
	}
	
	@Override
   public void onConfigurationChanged(Configuration newConfig){

   }
	
	
    public boolean isOrientationPortatit(){
    	boolean flag = (mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT);
    	return flag;
    }

	public void setPortraitDialpadViewShowStatus(boolean bShow){
		isPortraitDialpadViewShow = bShow;
	}

	public void setHorizontalDialpadViewShowStatus(boolean bShow){
		isHorizontalDialpadViewShow = bShow;
	}

	public void updateDialpadViewShowStatusWhenConfigure(){
		if (isOrientationPortatit()){
			hideHorizontalFragment(false);
			if (isPortraitDialpadViewShow){
				showPortatitFragment(false);
			}
		}else{
			hidePortatitFragment(false);
			if (isHorizontalDialpadViewShow){
				showHorizontalFragment(false);
			}
		}
	}


	private void removeDialpadviewFragment(){
		  FragmentManager fragmentManager = mActivity.getFragmentManager();
		  final FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
		  
		
		  mDialpadViewFragmentPortrait = (DialpadViewFragment) fragmentManager.findFragmentByTag(TAG_DIALPADVIEW_FRAGMENT_PORTRAIT); 
		  if (mDialpadViewFragmentPortrait != null){
			  fragmentTransaction.remove(mDialpadViewFragmentPortrait);
			  mDialpadViewFragmentPortrait = null;
		  }

		  mDialpadViewFragmentLandScape = (DialpadViewFragment) fragmentManager.findFragmentByTag(TAG_DIALPADVIEW_FRAGMENT_LANDSCAPE); 
		  if (mDialpadViewFragmentLandScape != null){
			  fragmentTransaction.remove(mDialpadViewFragmentLandScape);
			  mDialpadViewFragmentLandScape = null;
		  }
		
       
          fragmentTransaction.commitAllowingStateLoss();
          fragmentManager.executePendingTransactions();
		
	}

	private void addDialpadviewFragment(){
		FragmentManager fragmentManager = mActivity.getFragmentManager();
		final FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

		if (isOrientationPortatit()){
			mDialpadViewFragmentPortrait = (DialpadViewFragment) fragmentManager.findFragmentByTag(TAG_DIALPADVIEW_FRAGMENT_PORTRAIT);
			if (mDialpadViewFragmentPortrait == null){
				mDialpadViewFragmentPortrait = new DialpadViewFragment();
				fragmentTransaction.add(mContainerID, mDialpadViewFragmentPortrait, TAG_DIALPADVIEW_FRAGMENT_PORTRAIT);
				fragmentTransaction.hide(mDialpadViewFragmentPortrait);
			}
		}else{
			mDialpadViewFragmentLandScape = (DialpadViewFragment) fragmentManager.findFragmentByTag(TAG_DIALPADVIEW_FRAGMENT_LANDSCAPE);
			if (mDialpadViewFragmentLandScape == null){
				mDialpadViewFragmentLandScape = new DialpadViewFragment();
				fragmentTransaction.add(mContainerID, mDialpadViewFragmentLandScape, TAG_DIALPADVIEW_FRAGMENT_LANDSCAPE);
				fragmentTransaction.hide(mDialpadViewFragmentLandScape);
			}
		}

		fragmentTransaction.commitAllowingStateLoss();
		fragmentManager.executePendingTransactions();

	}


	public DialpadViewFragment showPortatitFragment(boolean animate){
		FragmentManager mFragmentManager = mActivity.getFragmentManager();
		FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();
		mDialpadViewFragmentPortrait = (DialpadViewFragment) mFragmentManager.findFragmentByTag(TAG_DIALPADVIEW_FRAGMENT_PORTRAIT);
		if (mDialpadViewFragmentPortrait == null){
			mDialpadViewFragmentPortrait = new DialpadViewFragment();
			fragmentTransaction.add(mContainerID, mDialpadViewFragmentPortrait, TAG_DIALPADVIEW_FRAGMENT_PORTRAIT);
		}
		fragmentTransaction.commitAllowingStateLoss();
		mFragmentManager.executePendingTransactions();

		mDialpadViewFragmentPortrait.showDialpadFragment(animate);

		return mDialpadViewFragmentPortrait;
	}

	public DialpadViewFragment hidePortatitFragment(boolean animate){
		FragmentManager mFragmentManager = mActivity.getFragmentManager();
		mDialpadViewFragmentPortrait = (DialpadViewFragment) mFragmentManager.findFragmentByTag(TAG_DIALPADVIEW_FRAGMENT_PORTRAIT);
		if (mDialpadViewFragmentPortrait != null){
			mDialpadViewFragmentPortrait.hideDialpadFragment(animate);
		}

		return mDialpadViewFragmentPortrait;
	}

	public DialpadViewFragment showHorizontalFragment(boolean animate){
		FragmentManager mFragmentManager = mActivity.getFragmentManager();
		FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();
		mDialpadViewFragmentLandScape = (DialpadViewFragment) mFragmentManager.findFragmentByTag(TAG_DIALPADVIEW_FRAGMENT_LANDSCAPE);
		if (mDialpadViewFragmentLandScape == null){
			mDialpadViewFragmentLandScape = new DialpadViewFragment();
			fragmentTransaction.add(mContainerID, mDialpadViewFragmentLandScape, TAG_DIALPADVIEW_FRAGMENT_LANDSCAPE);
		}
		fragmentTransaction.commitAllowingStateLoss();
		mFragmentManager.executePendingTransactions();

		mDialpadViewFragmentLandScape.showDialpadFragment(animate);

		return mDialpadViewFragmentLandScape;
	}

	public DialpadViewFragment hideHorizontalFragment(boolean animate){
		FragmentManager mFragmentManager = mActivity.getFragmentManager();
		mDialpadViewFragmentLandScape = (DialpadViewFragment) mFragmentManager.findFragmentByTag(TAG_DIALPADVIEW_FRAGMENT_LANDSCAPE);
		if (mDialpadViewFragmentLandScape != null){
			mDialpadViewFragmentLandScape.hideDialpadFragment(animate);
		}

		return mDialpadViewFragmentLandScape;
	}

}
