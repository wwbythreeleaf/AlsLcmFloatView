package com.leaf.alslcmfloatview;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageView;
import android.widget.TextView;

public class DataServer extends Service implements SensorEventListener {
	private SensorManager sensorManager = null;
	private static final String TAG = "LightsMgr";
	static final String LCD_FILE = "/sys/class/leds/lcd-backlight/brightness";

	private final IBinder mBinder = new LocalBinder();
	private float als_value = 0;
	private int olux;
	private float step;
	private int strength;
	/**
	 * Class for clients to access. Because we know this service always runs in
	 * the same process as its clients, we don't need to deal with IPC.
	 */

	private WindowManager windowManager;
	private Thread uiUpdate;
	private Handler uiHandler;
	
	private TextView tvALS;
	private TextView tvBLK;
	View view;
	public class LocalBinder extends Binder {
		DataServer getService() {
			return DataServer.this;
		}
	}

	@SuppressLint("HandlerLeak")
	@Override
	public void onCreate() {
		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		sensorManager.registerListener(this,
				sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT),
				SensorManager.SENSOR_DELAY_NORMAL);
		step = (float) ((0x8000 - 0x40) / 10240.0);

		LayoutInflater inflater=(LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		view = inflater.inflate(R.layout.float_view, null);
		view.setBackgroundColor(Color.parseColor("#80888888"));
		
		tvALS = (TextView)view.findViewById(R.id.textView1);
		tvBLK = (TextView)view.findViewById(R.id.textView2);
		tvALS.setText("wait...");
		tvBLK.setText("wait...");
		tvALS.setTextColor(Color.parseColor("#ffF6EB1C"));
		tvBLK.setTextColor(Color.parseColor("#ffF6EB1C"));
		
		windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
		final LayoutParams myParams = new WindowManager.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
				LayoutParams.TYPE_PHONE, LayoutParams.FLAG_NOT_FOCUSABLE,
				PixelFormat.TRANSLUCENT);
		myParams.gravity = Gravity.CENTER | Gravity.LEFT;
		myParams.x = 0;
		myParams.y = 100;
		
		windowManager.addView(view,myParams);
		try {
			view.setOnTouchListener(new View.OnTouchListener() {
				private int initialX;
				private int initialY;
				private float initialTouchX;
				private float initialTouchY;
				private long touchStartTime = 0;
				
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					if (System.currentTimeMillis() - touchStartTime > ViewConfiguration
							.getLongPressTimeout()
							&& Math.abs(initialTouchX - event.getRawX())<6) {
						windowManager.removeView(view);
						stopSelf();
						return false;
					}
					switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						touchStartTime = System.currentTimeMillis();
						initialX = myParams.x;
						initialY = myParams.y;
						initialTouchX = event.getRawX();
						initialTouchY = event.getRawY();
						break;
					case MotionEvent.ACTION_UP:
						break;
					case MotionEvent.ACTION_MOVE:
						myParams.x = initialX
								+ (int) (event.getRawX() - initialTouchX);
						myParams.y = initialY
								+ (int) (event.getRawY() - initialTouchY);
						windowManager.updateViewLayout(v, myParams);
						break;
					}
					return false;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
		uiUpdate = new Thread(new Runnable() {

			@Override
			public void run() {
				while (true) {
					if(getLux() != 0)
						uiHandler.sendEmptyMessage(1);
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		});
		uiHandler = new Handler(){
			@Override
	        public void handleMessage(Message msg) {
				tvALS.setText("ALS:"+getLux());
				try {
					tvBLK.setText("BLK:"+getHwInfos());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};
		uiUpdate.setPriority(Thread.MIN_PRIORITY);
		uiUpdate.start();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i("DataServer", "Received start id " + startId + ": " + intent);
		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	// This is the object that receives interactions from clients. See
	// RemoteService for a more complete example.
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
			float[] values = event.values;
			als_value = values[0];

			if (als_value < 40)
				olux = 14;
			else if (als_value >= 40 && als_value < 225)
				olux = 0;
			else if (als_value >= 255 && als_value < 930)
				olux = 1;
			else if (als_value >= 930 && als_value < 2600)
				olux = 2;
			else if (als_value >= 2600 && als_value >= 10240)
				olux = 3;


			Log.d(TAG, als_value + "*" + step + "=" + olux + "[]");
			setCABC(olux, strength);
		}
	}

	void setStrength(int p) {
		strength = p * 0x7F / 100;
	}

	private void setCABC(int lux, int sth) {
		File f = new File("/sys/bus/platform/devices/als_ps.40/driver/als_cabc");
		FileWriter fw;
		if (f.exists()) {
			try {
				fw = new FileWriter(f);
				fw.write((lux + "").toString() + "^0");
				fw.close();
				SystemClock.sleep(500);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	String path = LCD_FILE;
	File file = new File(path);
	
	String getHwInfos() throws IOException {
		InputStream inputStream = null;
		BufferedReader buffer = null;
		InputStreamReader in = null;
		String line = null;

		inputStream = new FileInputStream(file);

		in = new InputStreamReader(inputStream, "GBK");
		buffer = new BufferedReader(in);
		line = buffer.readLine();
		if (buffer != null) {
			buffer.close();
		}
		if (inputStream != null) {
			inputStream.close();
		}
		if (in != null) {
			in.close();
		}

		return line;
	}

	float getLux() {
		return als_value;
	}

	float getOLux() {
		return olux;
	}
}