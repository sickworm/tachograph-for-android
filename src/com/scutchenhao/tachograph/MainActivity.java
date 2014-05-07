package com.scutchenhao.tachograph;

import android.os.Bundle;
import android.os.IBinder;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.view.Menu;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.scutchenhao.tachograph.MainService.LocalBinder;

import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnInfoListener;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.GestureDetector.OnGestureListener;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

public class MainActivity extends Activity implements SurfaceHolder.Callback {
	public final static int TURN_LEFT = 1;
	public final static int TURN_RIGHT = 2;
	public final static int DRAG_SENSITIVITY = 3500;
	public final static String DEFAULT_ID = "car1";
	protected final static int TAKE_PICTURE_AMOUNT = 5;
	protected final static int TAKE_PICTURE_DELAY = 1000;
	protected final static String TAG = "ScutTachograph:Activity";
	protected final static boolean DEBUG = true;
	protected final static int LOG_TOAST = 1;
	protected final static int LOG_SHOW_TEXT = 2;
	protected final static int LOG_FROM_SERVICE = 3;
	private Button start;
    private Button stop;
    private ImageButton setting;
    private TextView log;
    private ScrollView scroll;
    private boolean isExpanding  = false;
	private long backTime = 0;
    private MediaRecorder mMediaRecorder;
    private SurfaceView mSurfaceView;
    private boolean isRecording = false;
    private Camera mCamera;
    private StorageManager mStorageManager;
	private int storageSettingPos;
	private int profileSettingPos;
	private int timeSettingPos;
	private int qualitySettingPos;
	private int storageSetting;			//MB
	private int remainStorageSetting;	//MB
	private int timeSetting;			//s
	private int qualitySetting;			//%
	private int widthSetting;
	private int heightSetting;
	private String idSetting = "";
	private View preferenceView;
	private List<CamcorderProfile> profileList = new ArrayList<CamcorderProfile>();
	private List <String> profileNameList = new ArrayList<String>();
	private SensorManager mSensorManager;
    private boolean takedPicture = true;
    private int takedPictureAmount = 0;
	private SensorEventListener mSensorEventListener = new SensorEventListener() {

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}

		@Override
		public void onSensorChanged(SensorEvent event) {
			int sensorType = event.sensor.getType();
			//values[0]:X轴，values[1]：Y轴，values[2]：Z轴
			float[] values = event.values;
			float y = values[1];
			float z = values[2];
			
			if(sensorType == Sensor.TYPE_ACCELEROMETER){
				int value = 15;
				if(y >= value || y <= -value || z >= value || z <= -value){
					if (takedPicture) {
						log("takePicture");
						new Thread() {
							public void run() {
								while(takedPictureAmount != TAKE_PICTURE_AMOUNT) {
									takedPicture = false;
									mCamera.takePicture(null, null, jpeg);
									try {
										sleep(TAKE_PICTURE_DELAY);
									} catch (InterruptedException e) {
										e.printStackTrace();
									}
									while(!takedPicture);
									log("takedPictureMount:" + takedPictureAmount);
								}
								takedPictureAmount = 0;
							}
						}.start();
					}
				}
			}
		}
		
	};
	private Camera.PictureCallback jpeg = new Camera.PictureCallback() {

		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			log("onPictureTaken");
			mCamera.startPreview();
			mStorageManager.savePhoto(data);
			takedPicture = true;
			takedPictureAmount++;
		}
		
	};
    private UpdateReceiver receiver = new UpdateReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent) {
	    	String dataType = intent.getStringExtra(DATA_TYPE);
	    	if (dataType.equals(LOG_DATA)) {
	    		String data = intent.getStringExtra(DATA);
	    		log(data, LOG_FROM_SERVICE);
	    	}
	    }
    };
    OnItemSelectedListener calculateTimeListener = new OnItemSelectedListener() {

		@Override
		public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2,
				long arg3) {
			Spinner recordProfile = (Spinner) preferenceView.findViewById(R.id.profile_select);
			Spinner recordStorage = (Spinner) preferenceView.findViewById(R.id.storage_select);
			Spinner recordQuality = (Spinner) preferenceView.findViewById(R.id.time_select);
			int pos = recordProfile.getSelectedItemPosition();
			int videoBitRate = profileList.get(pos).videoBitRate;
			int audioBitRate = profileList.get(pos).audioBitRate;
			pos = recordStorage.getSelectedItemPosition();
			int storage = getResources().getIntArray(R.array.storage)[pos];
			pos = recordQuality.getSelectedItemPosition();
			int quality = getResources().getIntArray(R.array.quality)[pos];
			
			long storageB = (long)storage * 1024 * 1024;
			int storagePerSec = (videoBitRate * quality / 100 + audioBitRate) / 8;
			int availTime = (int)(storageB / storagePerSec);
			int hour = availTime / 3600;
			int min = availTime % 3600 / 60;
			String sTime = ((hour == 0)?"" : (hour + "小时")) + min + "分钟";
			TextView availTimeView = (TextView) preferenceView.findViewById(R.id.record_avail_time);
			availTimeView.setText(sTime);
		}

		@Override
		public void onNothingSelected(AdapterView<?> arg0) {
		}
    	
    };
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        setContentView(R.layout.activity_main);

        start = (Button) this.findViewById(R.id.start);
        stop = (Button) this.findViewById(R.id.stop);
        setting = (ImageButton) this.findViewById(R.id.setting);
		log = (TextView) findViewById(R.id.log);
        scroll = (ScrollView) findViewById(R.id.scroll);
        start.setOnClickListener(new TestVideoListener());
        stop.setOnClickListener(new TestVideoListener());
        setting.setOnClickListener(new TestVideoListener());
        log.setOnClickListener(new TestVideoListener());
        preferenceView = LayoutInflater.from(this).inflate(R.layout.settings_dialog, null);
        
        setRecordState(false);
        mSurfaceView = (SurfaceView) this.findViewById(R.id.surfaceview);
        mSurfaceView.getHolder().addCallback(this);

		mGestureDetector = new GestureDetector(this, mGestureListener, null);
        mGestureDetector.setIsLongpressEnabled(true);

        mStorageManager = new StorageManager(this);
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setOnInfoListener(new OnInfoListener() {
        	@Override
        	public void onInfo(MediaRecorder mr, int what, int extra) {
        		switch (what) {
        		case MediaRecorder.MEDIA_RECORDER_INFO_UNKNOWN:
                    log("录像出错，暂停录制", LOG_TOAST);
            		stopRecording();
            		setRecordState(false);
        			break;
        		case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED:
                    log("录像大小到，重新录像");
        			break;
        		case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
                    log("录像时间到，重新录像");
        			restartRecording();
        			break;
        		default:
        			break;
        		}
        	}
        });
        getQualityProfile();
    	loadSettings();
        
        int ret = mStorageManager.check();
        switch(ret) {
        case StorageManager.STORAGE_AVAILABLE:
        	log("储存空间充足", LOG_SHOW_TEXT);
        	break;
        case StorageManager.STORAGE_UNMOUNT:
        case StorageManager.STORAGE_NOT_ENOUGH:
        default:
        	log("储存空间不足", LOG_SHOW_TEXT);
        	start.setEnabled(false);
        	break;
        }
	}

	@Override
	protected void onStart() {
		super.onStart();

		//绑定并启动Service
        Intent intent = new Intent();
	    intent.setClass(this, MainService.class);
	    bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

	    //加速度检测
	    mSensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        Sensor mAccelerateSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(mSensorEventListener, mAccelerateSensor, SensorManager.SENSOR_DELAY_NORMAL);


        IntentFilter filter = new IntentFilter();
        filter.addAction(UpdateReceiver.MSG);
        registerReceiver(receiver, filter);

        mCamera = Camera.open();
		Camera.Parameters parameters = mCamera.getParameters();
    	parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
    	parameters.setPreviewSize(widthSetting, heightSetting);
    	mCamera.setParameters(parameters);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		log("onPause");
	}

	@Override
	protected void onStop() {
		super.onStop();
		log("onStop");
		if(isRecording)
			stopRecording();
		
		mCamera.stopPreview();
		mCamera.unlock();
		mCamera.release();
		mCamera = null;
		mMediaRecorder.release();
		mMediaRecorder = null;

        unregisterReceiver(receiver);
		mSensorManager.unregisterListener(mSensorEventListener);
		unbindService(mConnection);
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
	
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
    	log("surfaceChanged");
		try {
			mCamera.setPreviewDisplay(holder);
		} catch (IOException e) {
			e.printStackTrace();
		}
    	mCamera.startPreview();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
    	log("surfaceCreated");
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
    	log("surfaceDestroyed");
	}
	
	private void setRecordState(boolean bStart) {
		if(bStart) {
			start.setEnabled(false);
	        stop.setEnabled(true);
	        setting.setEnabled(false);
	        isRecording = true;
		} else {
			start.setEnabled(true);
	        stop.setEnabled(false);
	        setting.setEnabled(true);
	        isRecording = false;
		}
	}
	
	private boolean mediaRecorderConfig() {
		mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        CamcorderProfile profile = profileList.get(profileSettingPos);
        int bitRate = profile.videoBitRate * qualitySetting / 100;
        mMediaRecorder.setProfile(profile);
		mMediaRecorder.setVideoEncodingBitRate(bitRate);
        mMediaRecorder.setMaxDuration(timeSetting * 1000);
        
        if (!mStorageManager.checkNewRecordFile()) {
            log("录像文件错误", LOG_TOAST);
            return false;
        } else {
            mMediaRecorder.setOutputFile(mStorageManager.getFileName());
        }
        return true;
	}
	
	class TestVideoListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            if (v == start) {
            	startRecording();
            } else if (v == stop) {
            	stopRecording();
            } else if (v == setting) {
            	settings();
            } else if (v == log) {
            	extendLogView();
            }
        }
    }
	
	private void startRecording() {
		int ret = mStorageManager.refreshDir(false);
		switch(ret) {
        case StorageManager.STORAGE_AVAILABLE:
        	log("储存空间可用");
        	break;
        case StorageManager.STORAGE_UNMOUNT:
        case StorageManager.STORAGE_NOT_ENOUGH:
        default:
        	log("储存空间异常");
        	start.setEnabled(false);
        	return;
        }
    	setRecordState(true);
    	if (!mediaRecorderConfig()) {
    		stop.setEnabled(false);
    		start.setEnabled(false);
    		return;
    	}

        try {
            mMediaRecorder.prepare();
            mMediaRecorder.start();
            log("开始录像", LOG_TOAST);
        } catch (Exception e) {
            e.printStackTrace();
            log("录像初始化失败", LOG_TOAST);
        	setRecordState(false);
            start.setEnabled(false);
        }
        
        CamcorderProfile profile = profileList.get(profileSettingPos);
        int bitRate = profile.videoBitRate * (qualitySetting / 100);
        log("录像分辨率：" + widthSetting + "x" + heightSetting, LOG_SHOW_TEXT);
        log("录像分段时长：" + (timeSetting/60) + "分钟", LOG_SHOW_TEXT);
        log("录像比特率：" + bitRate, LOG_SHOW_TEXT);
	}
	
	private void stopRecording() {
		//stop when start time less than 1s may lead software crash:stop failed: -1007
		//should delay some time
    	setRecordState(false);
        if (mMediaRecorder != null) {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
            mStorageManager.refreshDir(true);
            log("停止录像，保存文件", LOG_TOAST);
        }
	}
	
	private void restartRecording() {
        try {
	        if (mMediaRecorder != null) {
	            mMediaRecorder.stop();
	            int ret = mStorageManager.refreshDir(true);
	            switch(ret) {
	            case StorageManager.STORAGE_AVAILABLE:
	            	log("储存空间充足");
	            	break;
	            case StorageManager.STORAGE_UNMOUNT:
	            case StorageManager.STORAGE_NOT_ENOUGH:
	            default:
	            	log("储存空间不足", LOG_TOAST);
	            	start.setEnabled(false);
	            	return;
	            }
	        	if (!mediaRecorderConfig()) {
	        		stop.setEnabled(false);
	        		start.setEnabled(false);
	        		return;
	        	}
	            mMediaRecorder.prepare();
	            mMediaRecorder.start();
	        }
            log("开始录像", LOG_TOAST);
        } catch (Exception e) {
            e.printStackTrace();
        }
	}
	
	protected void log(String log) {
		if(DEBUG)
			Log.v(TAG, log);
	}

	protected void log(String data, int type) {
		switch(type) {
		case LOG_TOAST:
			Toast.makeText(MainActivity.this, data, Toast.LENGTH_SHORT).show();
			log(data);
			break;
		case LOG_SHOW_TEXT:
			log(data);
			break;
		case LOG_FROM_SERVICE:
			break;
		default:
			break;
		}
		log.append(data + "\n");		//if you set android:maxlines, it can not work
		scroll.post(new Runnable() {
	        public void run() {
	        	scroll.fullScroll(ScrollView.FOCUS_DOWN);
	        }
		});
	}

	private void extendLogView() {
		LayoutParams params = scroll.getLayoutParams();
		float d = getResources().getDisplayMetrics().density;
		if (isExpanding)
			params.height = (int)(60 * d);
		else
			params.height = (int)(180 * d);
		isExpanding = !isExpanding;
		scroll.setLayoutParams(params);
		scroll.post(new Runnable() {
	        public void run() {
	        	scroll.fullScroll(ScrollView.FOCUS_DOWN);
	        }
		});
	}
	
	private void loadSettings() {
		SharedPreferences settings = getSharedPreferences("setting", MODE_PRIVATE);
		storageSettingPos = settings.getInt("storage_pos", 0);
		profileSettingPos = settings.getInt("profile_pos", 0);
		timeSettingPos = settings.getInt("time_pos", 0);
		qualitySettingPos = settings.getInt("quality_pos", 0);
		storageSetting = settings.getInt("storage", getResources().getIntArray(R.array.storage)[0]);
		timeSetting = settings.getInt("time", getResources().getIntArray(R.array.time)[0]);
		qualitySetting = settings.getInt("quality", getResources().getIntArray(R.array.quality)[0]);
		CamcorderProfile profile = profileList.get(profileSettingPos);
		widthSetting = settings.getInt("width", profile.videoFrameWidth);
		heightSetting = settings.getInt("height", profile.videoFrameHeight);
		idSetting = settings.getString("id", DEFAULT_ID);

        //文件体积=时间X码率/8
		int quality = getResources().getIntArray(R.array.quality)[qualitySettingPos];
		remainStorageSetting = (profile.videoBitRate * quality / 100 + profile.audioBitRate) * timeSetting / 8;
		remainStorageSetting = (int) (remainStorageSetting * 1.2);
		mStorageManager.reset(storageSetting, remainStorageSetting);
	}
	
	private void settings() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(preferenceView);
        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				SharedPreferences settings = getSharedPreferences("setting", MODE_PRIVATE);
		        Editor mEditor = settings.edit();
		
		        Spinner recordProfile = (Spinner) preferenceView.findViewById(R.id.profile_select);
		        int pos = recordProfile.getSelectedItemPosition();
		        CamcorderProfile profile = profileList.get(pos);
		        mEditor.putInt("profile_pos", pos);
		        mEditor.putInt("width", profile.videoFrameWidth);
		        mEditor.putInt("height", profile.videoFrameHeight);

		        Spinner recordQuality = (Spinner) preferenceView.findViewById(R.id.quality_select);
		        pos = recordQuality.getSelectedItemPosition();
		        mEditor.putInt("quality_pos", pos);
		        int[] quality = getResources().getIntArray(R.array.quality);
		        mEditor.putInt("quality", quality[pos]);
		        
		        Spinner recordStorage = (Spinner) preferenceView.findViewById(R.id.storage_select);
		        pos = recordStorage.getSelectedItemPosition();
		        mEditor.putInt("storage_pos", pos);
		        int[] storage = getResources().getIntArray(R.array.storage);
		        mEditor.putInt("storage", storage[pos]);

		        Spinner recordTime = (Spinner) preferenceView.findViewById(R.id.time_select);
		        pos = recordTime.getSelectedItemPosition();
		        mEditor.putInt("time_pos", pos);
		        int[] time = getResources().getIntArray(R.array.time);
		        mEditor.putInt("time", time[pos]);
		
		        EditText id = (EditText) preferenceView.findViewById(R.id.id);
		        String idName = id.getText().toString();
		        mEditor.putString("id", idName);
		        
		        mEditor.commit();
		
		        loadSettings();
				mCamera.stopPreview();
				Camera.Parameters parameters = mCamera.getParameters();
		    	parameters.setPreviewSize(widthSetting, heightSetting);
		    	mCamera.setParameters(parameters);
		    	mCamera.startPreview();
			}
        });
        loadDialogPreferences(preferenceView);
        builder.show();
	}
	
	private void loadDialogPreferences(View preferenceView) {
        loadSettings();
        Spinner recordProfile = (Spinner) preferenceView.findViewById(R.id.profile_select);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, profileNameList);
        recordProfile.setAdapter(adapter);
        recordProfile.setSelection(profileSettingPos);

        Spinner recordStorage = (Spinner) preferenceView.findViewById(R.id.storage_select);
        recordStorage.setSelection(storageSettingPos);

        Spinner recordTime = (Spinner) preferenceView.findViewById(R.id.time_select);
        recordTime.setSelection(timeSettingPos);

        Spinner recordQuality = (Spinner) preferenceView.findViewById(R.id.quality_select);
        recordQuality.setSelection(qualitySettingPos);
        
        EditText id = (EditText) preferenceView.findViewById(R.id.id);
        id.setText(idSetting);

        recordProfile.setOnItemSelectedListener(calculateTimeListener);
        recordStorage.setOnItemSelectedListener(calculateTimeListener);
        recordQuality.setOnItemSelectedListener(calculateTimeListener);
	}
	
	@SuppressLint("InlinedApi")
	private void getQualityProfile() {
		if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_1080P)) {
			profileList.add(CamcorderProfile.get(CamcorderProfile.QUALITY_1080P));
			profileNameList.add("1080P(1920*1080)");
		}
		if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_720P)) {
			profileList.add(CamcorderProfile.get(CamcorderProfile.QUALITY_720P));
			profileNameList.add("720P(180*720)");
		}
		if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_480P)) {
			profileList.add(CamcorderProfile.get(CamcorderProfile.QUALITY_480P));
			profileNameList.add("480P(720*480)");
		}
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
			if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_QVGA)) {
				profileList.add(CamcorderProfile.get(CamcorderProfile.QUALITY_QVGA));
				profileNameList.add("QVGA(320*240)");
			}
		}
	}

	//RefreshService关联
	private MainService mService;
    private LocalBinder serviceBinder;
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
        		serviceBinder = (LocalBinder)service;
                mService = serviceBinder.getService();
                log.append(mService.getLog());
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        }
    };


	/*
	 * 手势识别
	 */
	private GestureDetector mGestureDetector;
	private OnGestureListener mGestureListener = new OnGestureListener() {
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {
			if (velocityX < -DRAG_SENSITIVITY)
				newActivity(MainActivity.TURN_LEFT);
			else if (velocityX > DRAG_SENSITIVITY)
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
	
	//将触摸事件交给mGestureDetector处理，否则无法识别
	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		mGestureDetector.onTouchEvent(ev);
		return super.dispatchTouchEvent(ev);
	}

	private void newActivity(int dir) {
		Intent intent = new Intent();
		intent.setClass(MainActivity.this, GpsMapActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
		if (dir == MainActivity.TURN_LEFT)		//设置切换动画，从右边进入，左边退出
			overridePendingTransition(R.animator.in_from_right, R.animator.out_to_left);
		if (dir == MainActivity.TURN_RIGHT)		//设置切换动画，从左边边进入，右边边退出
			overridePendingTransition(R.animator.in_from_left, R.animator.out_to_right);
		finish();
	}
}
