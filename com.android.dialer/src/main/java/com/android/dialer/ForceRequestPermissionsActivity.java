/*
 * Copyright (C) 2015 add by geniusgithub begin
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

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.android.contacts.common.util.PermissionsUtil;

import java.util.ArrayList;
import java.util.Arrays;


@TargetApi(23)
public class ForceRequestPermissionsActivity extends Activity {
	
	 
	 private final static String TAG = "ForceRequestPermissionsActivity";

	private static final int PERMISSIONS_REQUEST_ALL_PERMISSIONS = 1;


/*	 private final int REQUEST_PHONE_PERMISSION =  0X0001;
	 private final int REQUEST_CONTACT_PERMISSION =  0X0002;
	 private final int REQUEST_LOCATION_PERMISSION =  0X0003;
	 private final int REQUEST_STORAGE_PERMISSION =  0X0004;*/

	public static final String PREVIOUS_ACTIVITY_INTENT = "previous_intent";
	private Intent mPreviousActivityIntent;
	private String[] requiredPermissions;
	private ArrayList<String> mForbiddenPermissionList = new ArrayList<String>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		mPreviousActivityIntent = (Intent) intent.getExtras().get(PREVIOUS_ACTIVITY_INTENT);
		requiredPermissions = PermissionsUtil.sRequiredPermissions;

		ActionBar bar = getActionBar();
        if(bar != null){
            bar.setDisplayHomeAsUpEnabled(true);  
        }

        setContentView(R.layout.permission_check_activity);

        requestNecessaryRequiredPermissions();

    }

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	public static boolean startPermissionActivity(Activity activity) {
		if (!PermissionsUtil.hasNecessaryRequiredPermissions(activity)) {
			Intent intent = new Intent(activity, ForceRequestPermissionsActivity.class);
			intent.putExtra(PREVIOUS_ACTIVITY_INTENT, activity.getIntent());
			activity.startActivity(intent);
			activity.finish();
			return true;
		}
		return false;
	}

	private void redirect() {
		mPreviousActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
		startActivity(mPreviousActivityIntent);
		finish();
		overridePendingTransition(0, 0);
	}


	private void requestNecessaryRequiredPermissions(){

		final ArrayList<String> unsatisfiedPermissions = new ArrayList<>();
		for (String permission : requiredPermissions) {
			if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
				unsatisfiedPermissions.add(permission);
			}
		}
		if (unsatisfiedPermissions.size() == 0) {
			finish();
			return;
		}
		requestPermissions(unsatisfiedPermissions.toArray(new String[unsatisfiedPermissions
						.size()]), PERMISSIONS_REQUEST_ALL_PERMISSIONS);
	}

	

/*
	private void requestSpecialPermissions(String permission, int requestCode){
		String []permissions = new String[]{permission};
		requestPermissions(permissions, requestCode);
	}
*/

	
	@Override
	public void onRequestPermissionsResult(int requestCode,
										   String[] permissions, int[] grantResults) {
		
		switch(requestCode){
/*			case REQUEST_PHONE_PERMISSION:
				doPhonePermission(grantResults);
				break;
			case REQUEST_CONTACT_PERMISSION:
				doContactPermission(grantResults);
				break;
			case REQUEST_LOCATION_PERMISSION:
				doLocationPermission(grantResults);
				break;
			case REQUEST_STORAGE_PERMISSION:
				doStoragePermission(grantResults);
				break;*/
			case PERMISSIONS_REQUEST_ALL_PERMISSIONS: {
					mForbiddenPermissionList.clear();
					if (permissions != null && permissions.length > 0
							&& isAllGranted(permissions, grantResults, mForbiddenPermissionList)) {
						redirect();
					} else {
						Dialog dialog = PermissionsUtil.createPermissionSettingDialog(this, mForbiddenPermissionList.toString());
						dialog.show();
					}
				}
				break;
			default:
				super.onRequestPermissionsResult(requestCode, permissions, grantResults);
				break;
		}


	}


	private boolean isAllGranted(String permissions[], int[] grantResult, ArrayList<String> forbiddenPermissionList ) {
		boolean isAllGrand = true;
		for (int i = 0; i < permissions.length; i++) {
			if (grantResult[i] != PackageManager.PERMISSION_GRANTED
					&& isPermissionRequired(permissions[i])) {
				forbiddenPermissionList.add(permissions[i]);
				isAllGrand = false;
			}
		}
		return isAllGrand;
	}

	private boolean isPermissionRequired(String p) {
		return Arrays.asList(requiredPermissions).contains(p);
	}

/*	private void doPhonePermission(int[] grantResults){
		if (grantResults[0] == PackageManager.PERMISSION_DENIED){
			Dialog dialog = PermissionsUtil.createPermissionSettingDialog(this, "电话权限");
			dialog.show();
		}else if (grantResults[0] == PackageManager.PERMISSION_GRANTED){
			requestSpecialPermissions(PermissionsUtil.CONTACTS, REQUEST_CONTACT_PERMISSION);
		}
	
	}

	private void doContactPermission(int[] grantResults){
		if (grantResults[0] == PackageManager.PERMISSION_DENIED){
			Dialog dialog = PermissionsUtil.createPermissionSettingDialog(this, "通讯录权限");
			dialog.show();
		}else if (grantResults[0] == PackageManager.PERMISSION_GRANTED){
			requestSpecialPermissions(PermissionsUtil.LOCATION, REQUEST_LOCATION_PERMISSION);
		}

	}
	
	
	private void doLocationPermission(int[] grantResults){
		if (grantResults[0] == PackageManager.PERMISSION_DENIED){
			Dialog dialog = PermissionsUtil.createPermissionSettingDialog(this, "位置权限");
			dialog.show();
		}else if (grantResults[0] == PackageManager.PERMISSION_GRANTED){
			requestSpecialPermissions(PermissionsUtil.STORAGE, REQUEST_STORAGE_PERMISSION);
		}
	
	}
	
	
	private void doStoragePermission(int[] grantResults){
		if (grantResults[0] == PackageManager.PERMISSION_DENIED){
			Dialog dialog = PermissionsUtil.createPermissionSettingDialog(this, "存储权限");
			dialog.show();
		}else if (grantResults[0] == PackageManager.PERMISSION_GRANTED){
			redirect();
		}
	
	}*/
}
