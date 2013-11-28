package com.scutchenhao.tachograph;

import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.view.Menu;
import java.io.IOException; 
import android.graphics.PixelFormat; 
import android.media.MediaRecorder; 
import android.view.SurfaceHolder; 
import android.view.SurfaceView; 
import android.view.View; 
import android.view.View.OnClickListener; 
import android.widget.Button; 

public class MainActivity extends Activity implements SurfaceHolder.Callback {
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

        init(); 
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

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
