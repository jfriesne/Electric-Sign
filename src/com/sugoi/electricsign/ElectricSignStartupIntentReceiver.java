package com.sugoi.electricsign;

import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.SharedPreferences;

import com.sugoi.electricsign.ElectricSignActivity;

public class ElectricSignStartupIntentReceiver extends BroadcastReceiver {
	public void onReceive(Context context, Intent intent) {		
		SharedPreferences s = context.getSharedPreferences(ElectricSignActivity.PREFS_NAME, 0);
		if ((s.getBoolean("launchAtStartup", false))&&(s.getBoolean("selfstart", false))) {
			Intent myStarterIntent = new Intent(context, ElectricSignActivity.class);
			myStarterIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
			context.startActivity(myStarterIntent);
		}
	}
}