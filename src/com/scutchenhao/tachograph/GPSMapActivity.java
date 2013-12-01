package com.scutchenhao.tachograph;

import java.io.File;
import java.util.List;

import com.scutchenhao.tachograph.MainService.LocalBinder;
import com.scutchenhao.tachograph.MainService.MyGpsLocation;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import android.app.Activity;  
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;  
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Handler.Callback;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.GestureDetector.OnGestureListener;
import android.view.View;
import android.widget.Toast;
  
public class GPSMapActivity extends Activity {  
	private boolean firstTime = true;
	private boolean showLocationFlag = false;
	private long backTime = 0;
	private GoogleMap map;
	private List<MyGpsLocation> locationList;
	private Thread drawLocationThread = null;
	private Location location = new Location(LocationManager.GPS_PROVIDER);
    private UpdateReceiver receiver = new UpdateReceiver() {
	    @Override  
	    public void onReceive(Context context, Intent intent) {
	    	String dataType = intent.getStringExtra(DATA_TYPE);
	    	if (dataType.equals(GPS_DATA)) {
	    		if(showLocationFlag)
	    			return;
	    		
		    	location = (Location)intent.getParcelableExtra(DATA);
		        map.clear();
		        //添加地图标记，设置经纬度。设置点击标记显示的名称，信息，图片
		        map.addMarker(new MarkerOptions()
		        .position(new LatLng(location.getLatitude(), location.getLongitude()))
		        .title("我的位置"));
		        if (firstTime) {
			        map.moveCamera(CameraUpdateFactory.zoomTo(18));
		        	map.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude())));
		        	firstTime = false;
		        }
	    	}
	    }
    }; 
    private Handler mHandler = new Handler(new Callback() {
        public boolean handleMessage(Message msg) {
        	int order = msg.getData().getInt("order");
    		MyGpsLocation current = locationList.get(order);
    		if(current.fire == 1) {
	    		map.addCircle(new CircleOptions()
					.center(new LatLng(current.latitude, current.longitude))
	    			.fillColor(Color.YELLOW)
	    			.strokeColor(0xddffff00)
	    			.radius(3));
    		} else {
    			map.addCircle(new CircleOptions()
				.center(new LatLng(current.latitude, current.longitude))
    			.fillColor(Color.RED)
    			.strokeColor(0xddff0000)
    			.radius(3));
    		}
    		
        	if(order != 0) {
        		MyGpsLocation past = locationList.get(order - 1);
        		map.addPolyline(new PolylineOptions()
        				.add(new LatLng(past.latitude, past.longitude), new LatLng(current.latitude, current.longitude))
        				.width(10)
        				.color(Color.YELLOW));
        	}
        	
        	if(order == locationList.size())
        		Toast.makeText(GPSMapActivity.this, "重绘完成", Toast.LENGTH_SHORT).show();
            return true;
        }
    });

	//RefreshService关联
    private MainService mService; 
    private LocalBinder serviceBinder;
    private ServiceConnection mConnection = new ServiceConnection() {  
        @Override  
        public void onServiceConnected(ComponentName className,  
                IBinder service) {
        		serviceBinder = (LocalBinder)service;  
                mService = serviceBinder.getService();
                double latitude = mService.getLatitude();
                double longitude = mService.getLongitude();
                if (latitude == 0 || longitude == 0) {
                	return;
                } else {
                	location.setLatitude(latitude);
                	location.setLongitude(longitude);
    		        map.clear();
    		        //添加地图标记，设置经纬度。设置点击标记显示的名称，信息，图片
    		        map.addMarker(new MarkerOptions()
    		        .position(new LatLng(location.getLatitude(), location.getLongitude()))
    		        .title("我的位置"));
    		        if (firstTime) {
    			        map.moveCamera(CameraUpdateFactory.zoomTo(18));
    		        	map.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude())));
    		        	firstTime = false;
    		        }
                }
                	
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
	
	
	
	protected Class<?> rightClass() {
		return MainActivity.class;
	}

	protected Class<?> leftClass() {
		return MainActivity.class;
	}

	protected Activity my() {
		return GPSMapActivity.this;
	}
    
	private void newActivity(int dir) {
		Class<?> nextClass;
		
		if (dir == MainActivity.TURN_LEFT)
			nextClass = rightClass();
		else 
			nextClass = leftClass();
		
		if (nextClass == null)
			return;
		
		Intent intent = new Intent();
		intent.setClass(my(), nextClass);
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
	
	public void showLocation(View view) {
		map.clear();
		showLocationFlag = true;
		locationList = mService.loadLocation();

        map.moveCamera(CameraUpdateFactory.zoomTo(18));
    	map.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(locationList.get(0).latitude, locationList.get(0).longitude)));
        
    	if(drawLocationThread != null)
    		drawLocationThread.interrupt();
		drawLocationThread = new Thread() {
			@Override
			public void run() {
				long time = System.currentTimeMillis();
				int order = 0;
				for (MyGpsLocation i : locationList) {
					while(System.currentTimeMillis() - time < i.time);
					Message msg = mHandler.obtainMessage();
					Bundle data = new Bundle();
					data.putInt("order", order);
					msg.setData(data);
					mHandler.sendMessage(msg);
					order++;
				}
			}
		};
		Toast.makeText(this, "开始重绘", Toast.LENGTH_SHORT).show();
		drawLocationThread.start();
	}
	
	public void saveLocation(View view) {
		if(!MainService.hasSdcard()) {
			Toast.makeText(this, "未找到sdcard，储存失败", Toast.LENGTH_SHORT).show();
			return;
		}
	    
		File file = new File(MainService.FILEDIR + "location_list.txt");
		if (file.exists()){
			AlertDialog dialog = new AlertDialog.Builder(this)
				.setTitle("记录已存在")
				.setMessage("是否覆盖？")
				.setPositiveButton("是", new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						mService.saveLocation();
					}
				})
				.setNegativeButton("否", new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				})
				.create();
			dialog.show();
		} else {
			mService.saveLocation();
		}
		
	}
} 