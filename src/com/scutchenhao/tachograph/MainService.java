package com.scutchenhao.tachograph;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.util.Log;

public class MainService extends Service {
	public static final String TAG = "ScutTachograph:Service";
	public static final boolean DEBUG = MainActivity.DEBUG;
	public static final String URL = "http://datatransfer.duapp.com/hello";
	public static final String FILEDIR = StorageManager.LOG_PATH;
	public static final int SEND_DELTA_TIME = 2000;
	private File gpsFile;
	private FileOutputStream gpsFileStream;
    public boolean sendFlag = true;
    public boolean receiveFlag = true;
    public boolean networkAvailableFlag = false;
    private boolean gpsFlag = false;
    private double latitude = 0;
    private double longitude = 0;
    private double altitude = 0;
    private int firstLocated = 0;
    private long firstLocatedTime = 0;
    private int fire = 0;
    private String log = "";
    private List<MyGpsLocation> locationList = new ArrayList<MyGpsLocation>();
    private final IBinder mBinder = new LocalBinder();
    private boolean hasGpsData = false;
    private boolean isServiceConnected = false;

    public class LocalBinder extends Binder {
        MainService getService() {
        	isServiceConnected = true;
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

		//远程接收数据
        new ReceiveThread().start();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		sendFlag = false;
		try {
			if (!hasGpsData) {
				gpsFileStream.close();
				gpsFile.delete();
			} else {
				sendLog("无数据文件");
			}
		} catch (IOException e) {
			sendLog("文件关闭失败");
		}
		sendLog("程序退出");
    	gpsFlag = false;
    	locationManager.removeGpsStatusListener(gpsStatusListener);
    	locationManager.removeUpdates(gpsListener);
    	locationManager.removeUpdates(gprsListener);
    	
	}
	
    private void sendLog(String msg){
    	if (DEBUG)
    		Log.v(TAG, msg);
    	log = log.concat(msg + '\n');
    	if (!isServiceConnected)		//avoid repeat log in log textview due to the delay time of boardcast
    		return;
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
	        try {
	            String fileName = new java.text.SimpleDateFormat("yyyy-MM-dd-HH:mm:ss", Locale.CHINESE).format(new Date()) + ".txt";
	        	gpsFile = new File(FILEDIR + fileName);
	        	if (!gpsFile.exists()){
	                 try {
	                	 gpsFile.createNewFile();
						sendLog("创建记录文件" + gpsFile.toString());
					} catch (IOException e) {
						sendLog("创建文件失败");
						return;
					}
	            }
	        	gpsFileStream = new FileOutputStream(gpsFile);
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

    public class SendLoopThread extends Thread {
	    @Override
	    public void run() {
	    	long deltaTime = System.currentTimeMillis();
	        while(sendFlag){
				if (System.currentTimeMillis() - deltaTime >= SEND_DELTA_TIME) {
					deltaTime = System.currentTimeMillis();
					new SendThread(":" + latitude + ":" + longitude).start();
				}
	        }
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

    public class ReceiveThread extends Thread {
    	private long time = 0;
	    @Override
	    public void run() {
	    	while(receiveFlag && networkAvailableFlag) {
	    		long newTime = System.currentTimeMillis();
	    		if (newTime - time < 300)
	    			continue;
	    		time = newTime;
	    		
	    		String data = getData();

	    		if (data.contains("n"))
	    			return;
	    		
	    		int i = data.indexOf(':');
	    		int j = data.indexOf(':', data.indexOf(':') + 1);
	    		latitude = Double.parseDouble(data.substring(i + 1, j - 1));
	    		longitude = Double.parseDouble(data.substring(j + 1));
	    		Location location = new Location(LocationManager.GPS_PROVIDER);
	    		location.setLatitude(latitude);
	    		location.setLongitude(longitude);
	    		sendGPS(location);
	    	}
	    }
    }

    public class SendThread extends Thread {
    	String data;
    	public SendThread(String data) {
    		this.data = data;
    	}
    	
	    @Override
	    public void run() {
	    	if (networkAvailableFlag) {
	    		String result = sendData(data);
	    		
	    		if (!result.equals("ok"))
	    			sendLog("数据发送失败：" + result);
	    	}
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
        	locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, SEND_DELTA_TIME, 0, gprsListener);
		//GPS 定位
        if (list.contains(LocationManager.GPS_PROVIDER))
        	locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, SEND_DELTA_TIME, 0, gpsListener);


        Thread gpsFileThread = new Thread() {
			@Override
			public void run() {
				while(gpsFlag) {
					try {
						sleep(SEND_DELTA_TIME);
					} catch (InterruptedException e1) {
						sendLog("GPS写线程出错");
					}
					if (recordFlag) {
						Date now = new Date();
			            String time = DateFormat.getDateTimeInstance().format(now);
			            String gpsData = "GPS: " + latitude + "," + longitude + "\r\n";
			        	try {
			        		if (gpsFileStream != null)
			        			gpsFileStream.write((time + '\t' + gpsData).getBytes());
			        			hasGpsData = true;
						} catch (IOException e) {
							sendLog("写入GPS数据失败");
						}
		        	}
				}
			}
        };
        gpsFileThread.start();
    }
	
    private void updateToNewLocation(Location location) {
        if (location != null) {
        	latitude = location.getLatitude();
            longitude = location.getLongitude();
            altitude = location.getAltitude();
            if (firstLocated == 1) {
                firstLocatedTime = System.currentTimeMillis();
            	sendLog("首次定位成功，维度：" +  latitude + "，经度：" + longitude);
            	locationManager.removeUpdates(gprsListener);
        		locationList.clear();
            	firstLocated++;
                new SendLoopThread().start();
                recordFlag = true;
            } else {
            	firstLocated++;
            }
            sendGPS(location);

            if (firstLocated > 1) {
            	if(altitude == 0)		//基站定位数据，不够准确，忽略掉
            		return;
            		
            	long time = System.currentTimeMillis() - firstLocatedTime;
	            locationList.add(new MyGpsLocation(latitude, longitude, time, fire));
            }

        } else {
        	sendLog("无法获取GPS信息");
        }
    }

    /**
     * Internet
     */
	private String getData() {
		SharedPreferences settings = getSharedPreferences("setting", MODE_PRIVATE);
		String id = settings.getString("id", MainActivity.DEFAULT_ID);
		try {
			HttpURLConnection connection;
			URL server;
			server = new URL(URL + "?id=" + id);
			connection = (HttpURLConnection)server.openConnection();
			connection.setReadTimeout(10 * 1000);
			connection.setRequestMethod("GET");
			InputStream inStream = connection.getInputStream();
			ByteArrayOutputStream data = new ByteArrayOutputStream();		//新建一字节数组输出流
			byte[] buffer = new byte[1024];		//在内存中开辟一段缓冲区，接受网络输入流
			int len=0;
			while((len = inStream.read(buffer)) != -1) {
				data.write(buffer, 0, len);		//缓冲区满了之后将缓冲区的内容写到输出流
			}
			inStream.close();
			return new String(data.toByteArray(),"utf-8");		//最后可以将得到的输出流转成utf-8编码的字符串，便可进一步处理
		} catch (MalformedURLException e) {
			sendLog("URL出错");
			return "null";
		} catch (IOException e) {
			sendLog("远程数据获取失败");
			return "null";
		}
	}

	private String sendData(String content) {
		SharedPreferences settings = getSharedPreferences("setting", MODE_PRIVATE);
		String id = settings.getString("id", MainActivity.DEFAULT_ID);
		try {
			HttpURLConnection connection;
			URL server;
			server = new URL(URL + "?id=" + id + "&data=" + content);
			connection = (HttpURLConnection)server.openConnection();
			connection.setReadTimeout(10 * 1000);
			connection.setRequestMethod("GET");
			InputStream inStream = connection.getInputStream();
			ByteArrayOutputStream data = new ByteArrayOutputStream();//新建一字节数组输出流
			byte[] buffer = new byte[1024];//在内存中开辟一段缓冲区，接受网络输入流
			int len=0;
			while((len=inStream.read(buffer))!=-1){
				data.write(buffer, 0, len);//缓冲区满了之后将缓冲区的内容写到输出流
			}
			inStream.close();
			return new String(data.toByteArray(),"utf-8");//最后可以将得到的输出流转成utf-8编码的字符串，便可进一步处理
		} catch (MalformedURLException e) {
			sendLog("URL出错");
			return "";
		} catch (IOException e) {
			sendLog("远程数据获取失败");
			return "";
		}
	}

    /**
     * Draw Location
     */
	public class MyGpsLocation {
		public double latitude;
    	public double longitude;
    	public long time;
    	
    	MyGpsLocation(double latitude, double longitude, long time, int fire) {
    		this.latitude = latitude;
    		this.longitude = longitude;
    		this.time = time;
    	}   	
    }
	
    /**
     * Activity调用
     */
	protected double getLatitude() {
		return latitude;
	}
	
	protected double getLongitude() {
		return longitude;
	}
	
    protected String getLog() {
    	return log;
    }

}