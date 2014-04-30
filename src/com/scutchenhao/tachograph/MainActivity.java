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
import android.hardware.Camera.Size;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnInfoListener;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.GestureDetector.OnGestureListener;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import android.util.Log;

public class MainActivity extends Activity implements SurfaceHolder.Callback {
	public final static int TURN_LEFT = 1;
	public final static int TURN_RIGHT = 2;
	protected final static String TAG = "ScutTachograph:Activity";
	protected final static boolean DEBUG = true;
	private final static int LOG_TOAST = 1;
	private Button start;
    private Button stop;
    private MediaRecorder mMediaRecorder;
    private SurfaceView mSurfaceView;
    private boolean isRecording;
    private Camera mCamera;
    private StorageManager mStorageManager;
	private int storageSettingPos = 0;
	private int qualitySettingPos = 0;
	private int timeSettingPos = 0;
	private int storageSetting = 1024;	//MB
	private int remainStorage = 10;		//MB
	private int timeSetting = 10;		//s
	private int widthSetting = 320;
	private int heightSetting = 240;
	private List<Size> supportedVideoSizes;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        setContentView(R.layout.activity_main);

        start = (Button) this.findViewById(R.id.start);
        stop = (Button) this.findViewById(R.id.stop);
        start.setOnClickListener(new TestVideoListener());
        stop.setOnClickListener(new TestVideoListener());
        setRecordState(false);
        mSurfaceView = (SurfaceView) this.findViewById(R.id.surfaceview);
        mSurfaceView.getHolder().addCallback(this);

		mGestureDetector = new GestureDetector(this, mGestureListener, null);
        mGestureDetector.setIsLongpressEnabled(true);

        mMediaRecorder = new MediaRecorder();
        
        mStorageManager = new StorageManager(storageSetting, remainStorage);
        int ret = mStorageManager.check();
        switch(ret) {
        case StorageManager.STORAGE_AVAILABLE:
        	log("储存空间可用");
        	break;
        case StorageManager.STORAGE_UNMOUNT:
        case StorageManager.STORAGE_NOT_ENOUGH:
        default:
        	log("储存空间异常");
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
		else
			stopPreview();

		unbindService(mConnection);
	}
	
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
    	log("surfaceChanged");
		if (mSurfaceView.getHolder().getSurface() == null){   
	          return;   
        }
		
    	startPreview(holder);

		Camera.Parameters parameters = mCamera.getParameters();
		supportedVideoSizes = parameters.getSupportedVideoSizes();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
    	log("surfaceCreated");
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
    	log("surfaceDestroyed");
	}
	
	private void setRecordState(boolean state) {
		if(state) {
			start.setEnabled(false);
	        stop.setEnabled(true);
	        isRecording = true;
		} else {
			start.setEnabled(true);
	        stop.setEnabled(false);
	        isRecording = false;
		}
	}
	
	@SuppressLint("NewApi")
	private boolean mediaRecorderConfig() {
        loadSettings();
        mCamera.unlock();
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.JELLY_BEAN_MR1)
        	mCamera.enableShutterSound(false);
        mMediaRecorder.setCamera(mCamera);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setMaxDuration(timeSetting * 1000);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        // 设置视频录制的分辨率。必须放在设置编码和格式的后面，否则报错
        mMediaRecorder.setVideoSize(widthSetting, heightSetting);
        log("录像格式：" + widthSetting + "x" + heightSetting);
        log("录像时间：" + timeSetting + "s");
        
        if (!mStorageManager.checkNewRecordFile()) {
            log("录像文件错误", LOG_TOAST);
            return false;
        } else {
            mMediaRecorder.setOutputFile(mStorageManager.getFileName());
        }

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
        
        return true;
	}
	
	class TestVideoListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            if (v == start) {
            	startRecording();
            }
            if (v == stop) {
            	stopRecording();
            }
        }

    }
	
	private void startRecording() {
		int ret = mStorageManager.refreshDir(this, false);
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
	}
	
	private void stopRecording() {		
		//stop when start time less than 1s may lead software crash:stop failed: -1007
		//should delay some time
    	setRecordState(false);
        if (mMediaRecorder != null) {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
            mStorageManager.refreshDir(this, true);
            log("停止录像，保存文件", LOG_TOAST);
        }
	}
	
	private void restartRecording() {
        try {
	        if (mMediaRecorder != null) {
	            mMediaRecorder.stop();
	            mMediaRecorder.reset();
	            int ret = mStorageManager.refreshDir(this, true);
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
	
	private void startPreview(SurfaceHolder holder) {
		log("startPreview");
		try {
            mCamera = Camera.open();
            mCamera.setPreviewDisplay(holder);  
    		mCamera.startPreview();
    		mCamera.autoFocus(null);
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
	
	private void stopPreview() {
		log("stopPreview");
		if(mCamera == null)
			return;
		mCamera.stopPreview();
		mCamera.release();
		mCamera = null;
	}
	
	private void log(String log) {
		if(DEBUG)
			Log.v(TAG, log);
	}

	private void log(String log, int type) {
		switch(type) {
		case LOG_TOAST:
			Toast.makeText(MainActivity.this, log, Toast.LENGTH_SHORT).show();
			log(log);
			break;
		default:
			break;
		}
	}

	
	/*
	 * 手势识别
	 */
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
	
	//将触摸事件交给mGestureDetector处理，否则无法识别
	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		mGestureDetector.onTouchEvent(ev);
		return super.dispatchTouchEvent(ev);
	}

	private void newActivity(int dir) {
		Intent intent = new Intent();
		intent.setClass(MainActivity.this, GPSMapActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
		if (dir == MainActivity.TURN_LEFT)		//设置切换动画，从右边进入，左边退出
			overridePendingTransition(R.animator.in_from_right, R.animator.out_to_left);
		if (dir == MainActivity.TURN_RIGHT)		//设置切换动画，从左边边进入，右边边退出
			overridePendingTransition(R.animator.in_from_left, R.animator.out_to_right);
		finish();
	}
	
	public void loadSettings() {
		SharedPreferences settings = this.getPreferences(MODE_PRIVATE);
		storageSettingPos = settings.getInt("storage_pos", storageSettingPos);
		qualitySettingPos = settings.getInt("quality_pos", qualitySettingPos);
		timeSettingPos = settings.getInt("time_pos", timeSettingPos);
		storageSetting = settings.getInt("storage", storageSetting);
		remainStorage = settings.getInt("remain", remainStorage);
		timeSetting = settings.getInt("time", timeSetting);
		widthSetting = settings.getInt("width", widthSetting);
		heightSetting = settings.getInt("height", heightSetting);
	}
	
	public void settings(View view) {
        final View preferenceView = LayoutInflater.from(this).inflate(  
                R.layout.settings_dialog, null); 
        final AlertDialog.Builder builder = new AlertDialog.Builder(this); 
        builder.setView(preferenceView);
        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				SharedPreferences settings = getPreferences(MODE_PRIVATE);
		        Editor mEditor = settings.edit();
		        
		        Spinner recordQuality = (Spinner) preferenceView.findViewById(R.id.quality_select);
		        int pos = recordQuality.getSelectedItemPosition();
		        Size size = supportedVideoSizes.get(pos);
		        mEditor.putInt("quality_pos", pos);
		        mEditor.putInt("width", size.width);
		        mEditor.putInt("height", size.height);

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
		        
		        mEditor.commit();
			}
        });
        loadDialogPreferences(preferenceView);
        builder.show();
	}
	
	private void loadDialogPreferences(View preferenceView) {
        loadSettings();
		List<String> sizeList = new ArrayList<String>();
		for(Size s : supportedVideoSizes) {
			sizeList.add(s.width + "x" + s.height);
		}
        Spinner recordQuality = (Spinner) preferenceView.findViewById(R.id.quality_select);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, sizeList);
        recordQuality.setAdapter(adapter);
        recordQuality.setSelection(qualitySettingPos);
        
        Spinner recordStorage = (Spinner) preferenceView.findViewById(R.id.storage_select);
        recordStorage.setSelection(storageSettingPos);
        
        Spinner recordTime = (Spinner) preferenceView.findViewById(R.id.time_select);
        recordTime.setSelection(timeSettingPos);
	}

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
    
    
}
