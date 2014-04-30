package com.scutchenhao.tachograph;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

public class StorageManager {
	public final static String TAG = MainActivity.TAG;
	public final static boolean DEBUG = MainActivity.DEBUG;
	public final static String PATH = Environment.getExternalStorageDirectory().getPath() + "/ScutTachograph/";
	public final static int STORAGE_UNMOUNT = 1;
	public final static int STORAGE_NOT_ENOUGH = 2;
	public final static int STORAGE_AVAILABLE = 3;
	public final static int CREATE_DIR_FAILED = 4;
	private int size;	//MB
	private int remainStorage;	//MB
	private long availStorage = 0;	//MB
	private String fileName = "";
	
	protected StorageManager(int size, int remainStorage) {
		this.size = size;
		this.remainStorage = remainStorage;
	}
	
	protected boolean createRecordFile() {
        File dir = new File(PATH);
        if(!dir.isDirectory() || !dir.exists()) {
        	if(!dir.mkdir())
        		return false;
        }
        
        String shortFileName = new java.text.SimpleDateFormat("yyyy-MM-dd-HH:mm:ss", Locale.CHINESE).format(new Date()) + ".mp4";
        fileName = PATH + shortFileName;
        File file = new File(fileName);
        if(file.exists())
        	file.delete();
        try {
			file.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
	        return false;
		}
        return true;
	}
	
	protected String getFileName() {
		return fileName;
	}
	
	protected int check() {
		if (!sdCardAvail())
			return STORAGE_UNMOUNT;
		
		long dirSize = 0;
		try {
			dirSize = getFileSizes(new File(PATH)) / 1024 / 1024;
			log("文件夹大小：" + dirSize + "MB");
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (size - dirSize >= availStorage) {
			return STORAGE_NOT_ENOUGH;
		}
		return STORAGE_AVAILABLE;
	}
	
	protected int refreshDir() {
		int ret = check();
		if (ret != STORAGE_AVAILABLE)
			return ret;
		if (availStorage + remainStorage >= size)
			;
		return ret;
	}
	
	protected void resetStorage(int size) {
		this.size = size;
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
    
    private long getFileSizes(File f) throws Exception
    {
	    long size = 0;
	    File flist[] = f.listFiles();
	    for (int i = 0; i < flist.length; i++){
	    	if (flist[i].isDirectory()){
	    		size = size + getFileSizes(flist[i]);
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
    		file.createNewFile();
    		log("文件不存在!");
    	}
    	return size;
    }
    
	private void log(String log) {
		if(DEBUG)
			Log.v(TAG, log);
	}
}