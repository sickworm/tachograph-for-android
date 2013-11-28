package com.scutchenhao.tachograph;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.widget.Toast;

public class MainService extends Service {
	public static final String ID = "3";
	public static final String FILEDIR = Environment.getExternalStorageDirectory().getPath() + "/SerialPortData/";
	private FileOutputStream dataFile;
    private BluetoothAdapter mBluetoothAdapter = null;
    public boolean bluetoothFlag = true;
    public boolean receiveFlag = true;
    public boolean networkAvailableFlag = false;
    private boolean gpsFlag = false;
    private double latitude = 0;
    private double longitude = 0;
    private double altitude = 0;
    private int firstLocated = 0;
    private String log = "";
//    private boolean aRound = false;
    //蓝牙串口服务UUID
//    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    
	// 实例化自定义的Binder类  
    private final IBinder mBinder = new LocalBinder();  
    
    public class LocalBinder extends Binder {  
        MainService getService() {  
            // 返回Activity所关联的Service对象，这样在Activity里，就可调用Service里的一些公用方法和公用属性  
            return MainService.this;  
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
    	return mBinder;  
    }

	@Override
	public void onCreate() {
		super.onCreate();
    	sendLog("程序已启动");

        initSdcard();  
        	
        initNetwork();

        initLocation(); 
        
	}
    
	@Override
	public void onDestroy() {
		super.onDestroy();
		bluetoothFlag = false;
		try {
			if (dataFile != null) {
				sendLog("关闭文件");
				dataFile.close();
			} else {
				sendLog("无数据文件");
			}
		} catch (IOException e) {
			sendLog("文件关闭失败");
		}
		sendLog("程序退出");
    	if (mBluetoothAdapter != null) {
    		while(mBluetoothAdapter.getState() == BluetoothAdapter.STATE_TURNING_ON);
    		mBluetoothAdapter.disable();
    	}
    	gpsFlag = false;
    	locationManager.removeGpsStatusListener(gpsStatusListener);
    	locationManager.removeUpdates(gpsListener);
    	locationManager.removeUpdates(gprsListener);
    	
	}
	
    private void sendLog(String msg){  
    	log = log.concat(msg + '\n');
    	Intent mIntent = new Intent(UpdateReceiver.MSG);
    	mIntent.putExtra(UpdateReceiver.DATA_TYPE, UpdateReceiver.LOG_DATA);
    	mIntent.putExtra(UpdateReceiver.DATA, msg);
        this.sendBroadcast(mIntent);
    }
     
    private void sendGPS(Location location){  
    	Intent mIntent = new Intent(UpdateReceiver.MSG);
    	mIntent.putExtra(UpdateReceiver.DATA_TYPE, UpdateReceiver.GPS_DATA);
    	mIntent.putExtra(UpdateReceiver.DATA, location);
        this.sendBroadcast(mIntent);  
    }
    

    /**
     * SD card
     */
    private void initSdcard() {
        //创建数据文件
        if (!hasSdcard()) {
        	sendLog("未找到sd卡，放弃储存数据");
		} else {
			File dir = new File(FILEDIR);
				if (!dir.exists()) {
					if (dir.mkdir())
						sendLog("创建目录" + FILEDIR);
				}
	        Calendar c = Calendar.getInstance();
	        int year = c.get(Calendar.YEAR); 
	        int month = c.get(Calendar.MONTH) + 1; 
	        int date = c.get(Calendar.DATE); 
	        int hour = c.get(Calendar.HOUR_OF_DAY); 
	        int minute = c.get(Calendar.MINUTE); 
	        int second = c.get(Calendar.SECOND);  
	        try {
	        	File file = new File(FILEDIR + year + "-" + month + "-" + date + " " +hour + "-" +minute + "-" + second + ".txt");
	        	if (!file.exists()){    
	                 try {
						file.createNewFile();
						sendLog("创建记录文件" + file.toString());
					} catch (IOException e) {
						sendLog("创建文件失败");
						return;
					}    
	            }    
	        	dataFile = new FileOutputStream(file);
			} catch (FileNotFoundException e) {
				sendLog("创建文件失败");
			}

        }

    }
    
    public static boolean hasSdcard() {
        String status = Environment.getExternalStorageState();
        if (status.equals(Environment.MEDIA_MOUNTED)) {
            return true;
        } else {
            return false;
        }
    }
    
    
    /**
     * Network
     */
    private void initNetwork() {
    	if (isConnect(this)) {
    		networkAvailableFlag = true;
    		sendLog("网络正常");
    	} else {
    		networkAvailableFlag = false;
    		sendLog("网络未连接，无法发送接收数据");
    	}
    }

    private boolean isConnect(Context context) { 
        // 获取手机所有连接管理对象（包括对wi-fi,net等连接的管理） 
	    try { 
	        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE); 
	        if (connectivity != null) { 
	            // 获取网络连接管理的对象 
	            NetworkInfo info = connectivity.getActiveNetworkInfo(); 
	            if (info != null&& info.isConnected()) { 
	                // 判断当前网络是否已经连接 
	                if (info.getState() == NetworkInfo.State.CONNECTED) { 
	                    return true; 
	                } 
	            } 
	        } 
	    } catch (Exception e) { 
	    	sendLog("获取网络状态出错");
	    } 
        return false; 
    } 
    
    /**
     * GPS
     */
    private boolean recordFlag = false;
    private LocationManager locationManager;
    private LocationListener gpsListener = new LocationListener() {
		@Override
		public void onLocationChanged(Location location) {
	        updateToNewLocation(location);
		}

		@Override
		public void onProviderDisabled(String provider) {
			if (firstLocated != 0)
				sendLog("GPS关闭");
		}

		@Override
		public void onProviderEnabled(String provider) {
			sendLog("GPS打开");
		}

		@Override
		public void onStatusChanged(String provider, int status,
				Bundle extras) {
		}
    	
    };
    private LocationListener gprsListener = new LocationListener() {
		@Override
		public void onLocationChanged(Location location) {
	        updateToNewLocation(location);
		}

		@Override
		public void onProviderDisabled(String provider) {
			if (firstLocated != 0)
				sendLog("GPS关闭");
		}

		@Override
		public void onProviderEnabled(String provider) {
			sendLog("GPS打开");
		}

		@Override
		public void onStatusChanged(String provider, int status,
				Bundle extras) {
		}
    	
    };    
    
    private GpsStatus.Listener gpsStatusListener = new GpsStatus.Listener() {
    	private int count = -1;
		@Override
		public void onGpsStatusChanged(int event) {
            switch (event) {
            //第一次定位
            case GpsStatus.GPS_EVENT_FIRST_FIX:
            	sendLog("第一次定位");
                break;
            //卫星状态改变
            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
//                sendLog("卫星状态改变");
                //获取当前状态
                GpsStatus gpsStatus=locationManager.getGpsStatus(null);
                //获取卫星颗数的默认最大值
                int maxSatellites = gpsStatus.getMaxSatellites();
                //创建一个迭代器保存所有卫星 
                Iterator<GpsSatellite> iters = gpsStatus.getSatellites().iterator();
                int newCount = 0;     
                while (iters.hasNext() && newCount <= maxSatellites) {     
                	newCount++;     
                }   
                if (count != newCount) {
                	sendLog("搜索到：" + newCount + "颗卫星");
                	count = newCount;
                }
                
                break;
            //定位启动
            case GpsStatus.GPS_EVENT_STARTED:
            	sendLog("定位启动");
                //得到最近的Location
                Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location == null)
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (location == null)
                	break;
                latitude = location.getLatitude();
                longitude = location.getLongitude();
                altitude = location.getAltitude();
                break;
            //定位结束
            case GpsStatus.GPS_EVENT_STOPPED:
            	sendLog("定位结束");
                break;
            }
		}
    };
    
    private void initLocation() {
    	gpsFlag = true;
	    locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
	    if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
            sendLog("GPS模块正常");
        } else {
        	sendLog("GPS模块关闭，请手动打开");
        }
        
        // 设置监听器，自动更新的最小时间为间隔N秒(1秒为1*1000，这样写主要为了方便)或最小位移变化超过N米
        locationManager.addGpsStatusListener(gpsStatusListener);
        List<String> list = locationManager.getAllProviders();
        
		//gprs定位
        if (list.contains(LocationManager.NETWORK_PROVIDER))
        	locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 500, 0, gprsListener);
		//GPS 定位
        if (list.contains(LocationManager.GPS_PROVIDER))
        	locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, gpsListener);

        
        Thread gpsThread = new Thread() {
			@Override
			public void run() {
				while(gpsFlag) {
					try {
						sleep(500);
					} catch (InterruptedException e1) {
						sendLog("GPS写线程出错");
					}
					if (recordFlag) {
						Date now = new Date();
			            String time = DateFormat.getDateTimeInstance().format(now);
			            String gpsData = "GPS: " + latitude + "," + longitude + "," + altitude + "\r\n";
			        	try {
			        		if (dataFile != null)
			        			dataFile.write((time + '\t' + gpsData).getBytes());
						} catch (IOException e) {
							sendLog("写入GPS数据失败");
						}
		        	}
				}
			}
        };
        gpsThread.start();
    }
	
    private void updateToNewLocation(Location location) {
        if (location != null) {
        	latitude = location.getLatitude();
            longitude = location.getLongitude();
            altitude = location.getAltitude();
            if (firstLocated == 1) {
            	sendLog("首次定位成功，维度：" +  latitude + "，经度：" + longitude + "，海拔：" + altitude);
        		Toast.makeText(MainService.this, "首次定位成功，维度：" +  latitude + "，经度：" + longitude + "，海拔：" + altitude, Toast.LENGTH_SHORT).show();
            	locationManager.removeUpdates(gprsListener);
            	firstLocated++;
            } else {
            	firstLocated++;
            }
            sendGPS(location);
            
            if (firstLocated > 1) {
            	if(altitude == 0)		//gprs定位数据，不够准确，忽略掉
            		return;
	            
            }
            
        } else {
        	sendLog("无法获取GPS信息");
        }
    }
    

    /**
     * Activity调用
     */
	protected double getAltitude() {
		return altitude;
	}

	protected double getLatitude() {
		return latitude;
	}
	
	protected double getLongitude() {
		return longitude;
	}
	
    protected boolean getRecordFlag() {
    	return recordFlag;
    }

    protected String getLog() {
    	return log;
    }    
    
    
}