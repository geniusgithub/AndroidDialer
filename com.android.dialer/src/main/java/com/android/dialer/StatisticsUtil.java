// add by geniusgithub begin
package com.android.dialer;
import android.app.Activity;

import com.umeng.analytics.MobclickAgent;

public class StatisticsUtil {
    public static void onPause(Activity context){
        MobclickAgent.onPause(context);
    }

    public static void onResume(Activity context){
        MobclickAgent.onResume(context);
    }
}
// add by geniusgithub end