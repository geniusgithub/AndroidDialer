package com.android.dialer.test;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;

public interface IIdeafriendActivitInterface {

   public void onCreate(Bundle savedInstanceState);
   public void onResume();
   public void onPause();
   public void onBackPressed();
   public void onNewIntent(Intent intent);
   public void onConfigurationChanged(Configuration newConfig);
}
