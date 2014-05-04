package com.scutchenhao.tachograph;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

public class StorageManager {
	public final static String TAG = "ScutTachograph:StorageManager";
	public final static boolean DEBUG = MainActivity.DEBUG;
	public final static String ROOT_PATH = Environment.getExternalStorageDirectory().getPath() + "/ScutTachograph/";
	public final static String RECORD_PATH = ROOT_PATH;
	public final static String PICTURE_PATH = ROOT_PATH + "photo/";
	public final static String LOG_PATH = ROOT_PATH + "log/";
	public final static int STORAGE_UNMOUNT = 1;
	public final static int STORAGE_NOT_ENOUGH = 2;
	public final static int STORAGE_AVAILABLE = 3;
	public final static int CREATE_DIR_FAILED = 4;
	public final static boolean TYPE_HAS_SUBDIR = true;
	public final static boolean TYPE_NO_SUBDIR = false;
	private int size;	//MB
	private int remainStorage;	//MB
	private long availStorage = 0;	//MB
	private String fileName = "";
	
	protected StorageManager(int size, int remainStorage) {
		this.size = size;
		this.remainStorage = remainStorage;
	}
	
	protected boolean checkNewRecordFile() {
        File dir = new File(RECORD_PATH);
        if(!dir.isDirectory() || !dir.exists()) {
        	if(!dir.mkdir())
        		return false;
        }
        
        String shortFileName = new java.text.SimpleDateFormat("yyyy-MM-dd-HH:mm:ss", Locale.CHINESE).format(new Date()) + ".mp4";
        fileName = RECORD_PATH + shortFileName;
        File file = new File(fileName);
        if(file.exists())
        	file.delete();
        return true;
	}
	
	protected String getFileName() {
		return fileName;
	}
	
	protected int check() {
		if (!sdCardAvail())
			return STORAGE_UNMOUNT;
		
		long dirSize = 0;
		long recordFileSize = 0;
		try {
			dirSize = getFileSizes(new File(RECORD_PATH), TYPE_HAS_SUBDIR) / 1024 / 1024;
			log("文件夹大小：" + dirSize + "MB");
			recordFileSize = getFileSizes(new File(RECORD_PATH), TYPE_NO_SUBDIR) / 1024 / 1024;
			log("录像文件大小：" + recordFileSize + "MB");
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (size - recordFileSize >= availStorage) {
			return STORAGE_NOT_ENOUGH;
		}
		return STORAGE_AVAILABLE;
	}
	
	protected int refreshDir(Context context, boolean afterRecording) {
		int ret = check();
		if (ret != STORAGE_AVAILABLE)
			return ret;

		if (afterRecording) {
			if (availStorage + remainStorage >= size)
				;//should do something
			fileScan(context);
		}
		return ret;
	}
	
	protected void resetStorage(int size) {
		this.size = size;
	}

	public void fileScan(Context context){
        Uri data = Uri.parse("file:///" + fileName);
        context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, data));
	}

	public void savePhoto(byte[] data) {
		if (data == null)
			return;
		File dir = new File(PICTURE_PATH);
		if (!dir.exists())
			dir.mkdir();
        String shortFileName = new java.text.SimpleDateFormat("yyyy-MM-dd-HH:mm:ss", Locale.CHINESE).format(new Date()) + ".jpg";
		File file = new File(PICTURE_PATH + shortFileName);
		if (file.exists())
			file.delete();
		try {
			file.createNewFile();
	    	FileOutputStream photoData = new FileOutputStream(file);
	    	photoData.write(data);
	    	photoData.close();
		} catch (IOException e) {
			log("保存图片失败");
			return;
		}
		log("保存图片:" + shortFileName);
	}
	
    @SuppressLint("NewApi")
	@SuppressWarnings("deprecation")
	protected boolean sdCardAvail() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            File sdcardDir = Environment.getExternalStorageDirectory();  
            StatFs sf = new StatFs(sdcardDir.getPath());
            long blockSize, availCount;
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.KITKAT) {
	            blockSize = sf.getBlockSize();
	            availCount = sf.getAvailableBlocks();
            } else {
	            blockSize = sf.getBlockSizeLong();
	            availCount = sf.getAvailableBlocksLong();
            } 
            availStorage = availCount * blockSize / 1024 / 1024;
            log("剩余空间:"+ availStorage +"MB");
            return true;
        } else {
        	log("SD未挂载");
        	return false;
        }
    }

    private long getFileSizes(File f, boolean type) throws Exception
    {
	    long size = 0;
	    File flist[] = f.listFiles();
	    for (int i = 0; i < flist.length; i++){
	    	if (flist[i].isDirectory()){
	    		if (type == TYPE_NO_SUBDIR)
	    			continue;
	    		size = size + getFileSizes(flist[i], TYPE_HAS_SUBDIR);
	    	}
	    	else{
	    		size =size + getFileSize(flist[i]);
	    	}
	    }
	    return size;
	}
    
    private long getFileSize(File file) throws Exception
    {
    	long size = 0;
    	if (file.exists()){
    		FileInputStream fis = null;
    		fis = new FileInputStream(file);
    		size = fis.available();
    		fis.close();
    	}
    	else{
    		log("文件不存在!");
    	}
    	return size;
    }
    
	private void log(String log) {
		if(DEBUG)
			Log.i(TAG, log);
	}
}