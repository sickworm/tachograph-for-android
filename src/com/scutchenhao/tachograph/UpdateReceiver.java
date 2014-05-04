package com.scutchenhao.tachograph;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class UpdateReceiver extends BroadcastReceiver{
	public static final String DATA_TYPE = "TYPE";
	public static final String LOG_DATA = "LOG_DATA";
	public static final String GPS_DATA = "GPS_DATA";
	public static final String DATA = "DATA";
	public static final String MSG = "com.scutchenhao.bluetoothmodule.msg";
	
	@Override
	public void onReceive(Context arg0, Intent arg1) {
		
	}
	
}