package com.scutchenhao.tachograph;

import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;

import java.io.File;
import java.util.Date;
import java.util.Locale;

import android.graphics.PixelFormat;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnInfoListener;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.GestureDetector.OnGestureListener;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {
	public final static String PATH = Environment.getExternalStorageDirectory().getPath() + "/ScutTachograph/";
	public final static int TURN_LEFT = 1;
	public final static int TURN_RIGHT = 2;
	private Button start;// 开始录制按钮 
    private Button stop;// 停止录制按钮 
    private MediaRecorder mediaRecorder;// 录制视频的类 
    private SurfaceView surfaceView;// 显示视频的控件 
    // 用来显示视频的一个接口，我靠不用还不行，也就是说用mediaRecorder录制视频还得给个界面看 
    private boolean isRecording;
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
        // 选择支持半透明模式,在有surfaceView的activity中使用。 
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        setContentView(R.layout.activity_main);
        
        //手势识别
		mGestureDetector = new GestureDetector(this, mGestureListener, null);
        mGestureDetector.setIsLongpressEnabled(true);
        
        layoutInit();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
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

	protected Class<?> rightClass() {
		return GPSMapActivity.class;
	}

	protected Class<?> leftClass() {
		return GPSMapActivity.class;
	}

	protected Activity my() {
		return MainActivity.this;
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
	
	
	private void layoutInit() { 
        start = (Button) this.findViewById(R.id.start);
        stop = (Button) this.findViewById(R.id.stop);
        start.setOnClickListener(new TestVideoListener());
        stop.setOnClickListener(new TestVideoListener());
        setRecordState(false);
        surfaceView = (SurfaceView) this.findViewById(R.id.surfaceview);

    } 
	
	@Override
	protected void onStop() {
		super.onStop();
		if(isRecording)
			stopRecording();
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
	
	private void mediaRecorderInit() {
        mediaRecorder = new MediaRecorder();// 创建mediaRecorder对象 
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        // 设置视频录制的分辨率。必须放在设置编码和格式的后面，否则报错 
        mediaRecorder.setVideoSize(320, 240);
        mediaRecorder.setVideoFrameRate(5);
        mediaRecorder.setPreviewDisplay(surfaceView.getHolder().getSurface());
        // 检测存储目录环境
        File file = new File(PATH);
        if(!file.isDirectory() || !file.exists()) {
        	if(!file.mkdir())
        		Toast.makeText(this, "目录初始化失败", Toast.LENGTH_SHORT).show();
        }
        // 设置视频文件输出的路径 
        String fileName = new java.text.SimpleDateFormat("yyyy-MM-dd-HH:mm:ss", Locale.US).format(new Date()) + ".3gp";
        mediaRecorder.setOutputFile(PATH  + fileName);
        
        mediaRecorder.setOnInfoListener(new OnInfoListener() {                 
        	@Override
        	public void onInfo(MediaRecorder mr, int what, int extra) {
        		// 发生错误，停止录制
        		mediaRecorder.stop();
        		mediaRecorder.release();
        		mediaRecorder = null;
        		setRecordState(false);
        		String err = "";
        		switch (what) {
        		case MediaRecorder.MEDIA_RECORDER_INFO_UNKNOWN:
        			err = "MEDIA_RECORDER_INFO_UNKNOWN";
        			break;
        		case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED:
        			err = "MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED";
        			break;
        		case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
        			err = "MEDIA_RECORDER_INFO_MAX_DURATION_REACHED";
        			break;
        		default:
        			break;
        		}
        		Toast.makeText(MainActivity.this, "录制出错:" + err, Toast.LENGTH_SHORT).show();
                 }
             });
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
    	setRecordState(true);
        mediaRecorderInit();
        try { 
            mediaRecorder.prepare();
            mediaRecorder.start();
            Toast.makeText(MainActivity.this, "开始录像", Toast.LENGTH_SHORT).show();
        } catch (IllegalStateException e) { 
            e.printStackTrace();
        } catch (Exception e) { 
            e.printStackTrace();
        } 
	}
	
	private void stopRecording() {
    	setRecordState(false);
        if (mediaRecorder != null) { 
            // 停止录制 
            mediaRecorder.stop();
            // 释放资源 
            mediaRecorder.release();
            mediaRecorder = null;
            Toast.makeText(MainActivity.this, "停止录像，并保存文件", Toast.LENGTH_SHORT).show();
        } 
	}
}
