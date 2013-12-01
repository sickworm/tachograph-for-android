package com.scutchenhao.tachograph;

import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import java.io.IOException; 
import android.graphics.PixelFormat; 
import android.media.MediaRecorder; 
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder; 
import android.view.SurfaceView; 
import android.view.View; 
import android.view.GestureDetector.OnGestureListener;
import android.view.View.OnClickListener; 
import android.widget.Button; 

public class MainActivity extends Activity implements SurfaceHolder.Callback {
	public final static int TURN_LEFT = 1;
	public final static int TURN_RIGHT = 2;
	private Button start;// 开始录制按钮 
    private Button stop;// 停止录制按钮 
    private MediaRecorder mediarecorder;// 录制视频的类 
    private SurfaceView surfaceview;// 显示视频的控件 
    // 用来显示视频的一个接口，我靠不用还不行，也就是说用mediaRecorder录制视频还得给个界面看 
    private SurfaceHolder surfaceHolder;
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
        // 选择支持半透明模式,在有surfaceview的activity中使用。 
        getWindow().setFormat(PixelFormat.TRANSLUCENT); 
        setContentView(R.layout.activity_main); 
        
        //手势识别
		mGestureDetector = new GestureDetector(this, mGestureListener, null);
        mGestureDetector.setIsLongpressEnabled(true);
        
        init(); 
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
	
	
	/*
	 * surface capture
	 */
	@Override
	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
		// 将holder，这个holder为开始在oncreat里面取得的holder，将它赋给surfaceHolder 
        surfaceHolder = arg0; 
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		// 将holder，这个holder为开始在onCreat里面取得的holder，将它赋给surfaceHolder 	
        surfaceHolder = holder; 
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// surfaceDestroyed的时候同时对象设置为null 
        surfaceview = null; 
        surfaceHolder = null; 
        mediarecorder = null; 
	}
	
	@SuppressWarnings("deprecation")
	private void init() { 
        start = (Button) this.findViewById(R.id.start); 
        stop = (Button) this.findViewById(R.id.stop); 
        start.setOnClickListener(new TestVideoListener()); 
        stop.setOnClickListener(new TestVideoListener()); 
        surfaceview = (SurfaceView) this.findViewById(R.id.surfaceview); 
        SurfaceHolder holder = surfaceview.getHolder();// 取得holder 
        holder.addCallback(this); // holder加入回调接口 
        //setType必须设置，要不出错. 
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS); 

    } 
	
	class TestVideoListener implements OnClickListener { 
        @Override 
        public void onClick(View v) { 
            if (v == start) { 
                mediarecorder = new MediaRecorder();// 创建mediarecorder对象 
                // 设置录制视频源为Camera(相机) 
                mediarecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA); 
                // 设置录制完成后视频的封装格式THREE_GPP为3gp.MPEG_4为mp4 
                mediarecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP); 
                // 设置录制的视频编码h263 h264 
                mediarecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264); 
                // 设置视频录制的分辨率。必须放在设置编码和格式的后面，否则报错 
                mediarecorder.setVideoSize(176, 144); 
                // 设置录制的视频帧率。必须放在设置编码和格式的后面，否则报错 
                mediarecorder.setVideoFrameRate(20); 
                mediarecorder.setPreviewDisplay(surfaceHolder.getSurface()); 
                // 设置视频文件输出的路径 
                mediarecorder.setOutputFile(Environment.getExternalStorageDirectory().getPath() + "/love.3gp"); 

                try { 
                    mediarecorder.prepare(); 
                    mediarecorder.start(); 

                } catch (IllegalStateException e) { 
                    e.printStackTrace(); 

                } catch (IOException e) { 
                    e.printStackTrace(); 
                } 
            } 
            if (v == stop) { 
                if (mediarecorder != null) { 
                    // 停止录制 
                    mediarecorder.stop(); 
                    // 释放资源 
                    mediarecorder.release(); 
                    mediarecorder = null; 
                } 
            } 
        } 

    } 
}
