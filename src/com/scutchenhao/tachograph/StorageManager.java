package com.scutchenhao.tachograph;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;

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
	private MainActivity mMainActivity;	//MB
	private int storage;	//MB
	private int remainStorage;	//MB
	private long availStorage = 0;	//MB
	private String fileName = "";
	
	protected StorageManager(MainActivity mMainActivity) {
		this.mMainActivity = mMainActivity;
	}
	protected StorageManager(MainActivity mMainActivity, int storage, int remainStorage) {
		this.mMainActivity = mMainActivity;
		this.storage = storage;
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
			log("文件夹大小：" + dirSize + "MB", MainActivity.LOG_SHOW_TEXT);
			recordFileSize = getFileSizes(new File(RECORD_PATH), TYPE_NO_SUBDIR) / 1024 / 1024;
			log("录像文件大小：" + recordFileSize + "MB", MainActivity.LOG_SHOW_TEXT);
			log("日志及图片文件大小：" + (dirSize - recordFileSize) + "MB", MainActivity.LOG_SHOW_TEXT);
		} catch (Exception e) {
			e.printStackTrace();
		}
		checkAvailStorage();
		if (storage - recordFileSize >= availStorage) {
			return STORAGE_NOT_ENOUGH;
		}
		return STORAGE_AVAILABLE;
	}
	
	protected int refreshDir(boolean afterRecording) {
		int ret = check();
		if (ret != STORAGE_AVAILABLE)
			return ret;

		if (afterRecording) {
			checkAvailStorage();
	        log("剩余空间:"+ availStorage +"MB");
			while (availStorage + remainStorage <= storage) {
				File file = findOldestFile(new File(ROOT_PATH));
				if(file == null) {	
					return STORAGE_NOT_ENOUGH;
				} else {
					log("空间不足，删除文件：" + file.getName());
					file.delete();
				}
				checkAvailStorage();
			}
	        log("剩余空间:"+ availStorage +"MB");
			fileScan(fileName);
		}
		return ret;
	}
	
	public File findOldestFile(File dir) {
		File fList[] = dir.listFiles();
		int oldestFilePos = 0;
		for (int i = 1; i < fList.length; i++) {
			if(fList[i].isDirectory()) {
				File subDirFile = findOldestFile(fList[i]);
				if (subDirFile == null)
					continue;
				long timeNumber = fileNameToNumber(subDirFile);
				if(timeNumber < fileNameToNumber(fList[oldestFilePos]))
					oldestFilePos = i;
			} else {
				long timeNumber = fileNameToNumber(fList[i]);
				long oldestTimeNumber = fileNameToNumber(fList[oldestFilePos]);
				if(timeNumber < oldestTimeNumber)
					oldestFilePos = i;
			}
		}
		if(fList.length == 0)
			return null;
		else
			return fList[oldestFilePos];
	}
	
	private long fileNameToNumber(File file) {
		String fileName = file.getName();
		int classIndex = fileName.indexOf('.');
		if(classIndex <= 0)
			return -1;					//delete the unformat file first
		fileName = fileName.substring(0, classIndex);
		fileName = fileName.replace("-", "");
		fileName = fileName.replace(":", "");
		try {
			long timeNumber = Long.parseLong(fileName);
			return timeNumber;
		} catch (NumberFormatException e) {
			return -1;					//delete the unformat file first
		}
	}
	
	protected void reset(int storage, int remainStorage) {
		this.storage = storage;
		this.remainStorage = remainStorage;
	}

	public void fileScan(String file){
        Uri data = Uri.parse("file:///" + file);
        mMainActivity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, data));
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
	    	fileScan(file.getPath());
	    	photoData.close();
		} catch (IOException e) {
			log("快照：保存照片失败", MainActivity.LOG_SHOW_TEXT);
			return;
		}
		log("保存图片:" + shortFileName, MainActivity.LOG_SHOW_TEXT);
	}
	
	private boolean sdCardAvail() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        } else {
        	log("SD卡未挂载", MainActivity.LOG_SHOW_TEXT);
        	return false;
        }
    }

    @SuppressLint("NewApi")
	@SuppressWarnings("deprecation")
    private void checkAvailStorage() {
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
    }
    
    private long getFileSizes(File f, boolean type) throws Exception
    {
	    long size = 0;
	    File fList[] = f.listFiles();
	    for (int i = 0; i < fList.length; i++){
	    	if (fList[i].isDirectory()){
	    		if (type == TYPE_NO_SUBDIR)
	    			continue;
	    		size = size + getFileSizes(fList[i], TYPE_HAS_SUBDIR);
	    	}
	    	else{
	    		size =size + getFileSize(fList[i]);
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
    	return size;
    }

	private void log(String log) {
		mMainActivity.log(log);
	}

	private void log(String log,int type) {
		mMainActivity.log(log, type);
	}
}