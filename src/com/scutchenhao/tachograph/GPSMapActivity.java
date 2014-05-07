package com.scutchenhao.tachograph;

import com.scutchenhao.tachograph.MainService.LocalBinder;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.GestureDetector.OnGestureListener;
import android.widget.Toast;

public class GPSMapActivity extends Activity {
	private final static String TAG = "ScutTachograph:GpsMapActivity";
	private boolean firstTime = true;
	private boolean showLocationFlag = false;
	private long backTime = 0;
	private GoogleMap map;
	private Marker marker;
	private Location location = new Location(LocationManager.GPS_PROVIDER);
    private UpdateReceiver receiver = new UpdateReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent) {
	    	String dataType = intent.getStringExtra(DATA_TYPE);
	    	if (dataType.equals(GPS_DATA)) {
	    		if(showLocationFlag)
	    			return;
	    		
		    	location = (Location)intent.getParcelableExtra(DATA);
		    	LatLng newLatLng = new LatLng(location.getLatitude(), location.getLongitude());
		    	log("位置：" + location.getLatitude() + "，" + location.getLongitude());
		    	log("两点距离：" + ((marker == null)?-1 : calculateDistance(newLatLng, marker.getPosition())));
		        if (firstTime) {
			        marker = map.addMarker(new MarkerOptions()
				        .position(newLatLng)
				        .title("我的位置"));
			        map.moveCamera(CameraUpdateFactory.zoomTo(15));
		        	map.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude())));
		        	firstTime = false;
		        } else if (calculateDistance(newLatLng, marker.getPosition()) >= 0.0005) {
			    	marker.remove();
	        		map.addPolyline(new PolylineOptions()
	        				.add(marker.getPosition() , newLatLng)
	        				.width(10)
	        				.color(Color.YELLOW));
			        marker = map.addMarker(new MarkerOptions()
				        .position(newLatLng)
				        .title("我的位置"));
			    	log("画新点");
		    	}
	    	}
	    }
    };

	//RefreshService关联
    @SuppressWarnings("unused")
	private MainService mService;
    private LocalBinder serviceBinder;
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
        		serviceBinder = (LocalBinder)service;
                mService = serviceBinder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();

        //手势识别
		mGestureDetector = new GestureDetector(this, mGestureListener, null);
        mGestureDetector.setIsLongpressEnabled(true);

    }

	@Override
	protected void onStart() {
		super.onStart();

		//绑定并启动Service
        Intent intent = new Intent();
	    intent.setClass(this, MainService.class);
	    bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	
    	//接受Service广播信息
        IntentFilter filter = new IntentFilter();
        filter.addAction(UpdateReceiver.MSG);
        this.registerReceiver(receiver, filter);
		
	}

	@Override
	protected void onStop() {
		super.onStop();
		unregisterReceiver(receiver);
		unbindService(mConnection);
	}

	private static double calculateDistance(LatLng l1, LatLng l2) {
		/*
		 * 纬度1度 = 大约111km
		 * 纬度1分 = 大约1.85km
		 * 纬度1秒 = 大约30.9m 
		 * 200m 约等于 0.002
		 * 50m 约等于 0.0005
		*/
		double delLat = l1.latitude - l2.latitude;
		double delLng = l1.longitude - l2.longitude;
		double distance = Math.pow(Math.pow(delLat, 2) + Math.pow(delLng, 2), 0.5);
		return distance;
	}
	//将触摸事件交给mGestureDetector处理，否则无法识别
	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		mGestureDetector.onTouchEvent(ev);
		return super.dispatchTouchEvent(ev);
	}
	
	//手势识别
	private GestureDetector mGestureDetector;
	private OnGestureListener mGestureListener = new OnGestureListener() {
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {
			if (velocityX < -1500)
				newActivity(MainActivity.TURN_LEFT);
			else if (velocityX > 1500)
				newActivity(MainActivity.TURN_RIGHT);
			return false;
		}

		@Override
		public void onLongPress(MotionEvent e) {
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
				float distanceY) {
			return false;
		}

		@Override
		public void onShowPress(MotionEvent e) {
		}

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			return false;
		}

		@Override
		public boolean onDown(MotionEvent e) {
			return false;
		}
	};
	
	private void newActivity(int dir) {
		Intent intent = new Intent();
		intent.setClass(GPSMapActivity.this, MainActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);

		if (dir == MainActivity.TURN_LEFT)		//设置切换动画，从右边进入，左边退出
			overridePendingTransition(R.animator.in_from_right, R.animator.out_to_left);
		if (dir == MainActivity.TURN_RIGHT)	//设置切换动画，从左边边进入，右边边退出
			overridePendingTransition(R.animator.in_from_left, R.animator.out_to_right);		
		
		finish();
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			long newTime = System.currentTimeMillis();
			if (newTime - backTime <= 5000) {
				return super.onKeyDown(keyCode, event);
			} else {
				backTime = newTime;
				Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show();
				return true;
			}
        }

		return true;
	}
	
	private void log(String log) {
		Log.v(TAG, log + "\n");
	}
} 