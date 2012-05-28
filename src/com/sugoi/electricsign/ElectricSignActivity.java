package com.sugoi.electricsign;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Picture;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.webkit.WebView.PictureListener;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

public class ElectricSignActivity extends Activity implements TextWatcher
{
	public void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);

		DoLogInfo("Electric Sign starting up!");
		requestWindowFeature(Window.FEATURE_NO_TITLE);  // might as well save some space

		DisplayMetrics displaymetrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
		_width = displaymetrics.widthPixels;
		_height = displaymetrics.heightPixels;
		//System.out.println("_width="+_width+", height="+_height);

		LinearLayout linLayout = new LinearLayout(this);
		linLayout.setOrientation(LinearLayout.VERTICAL);

		// Set up the settings view (that comes up at launch)
		_settingsView = new RelativeLayout(this);
		{
			if (_height >= 400)
			{
				String versionString = "";
				PackageManager pm = getPackageManager();
				try {
					//---get the package info---
					PackageInfo pi = pm.getPackageInfo("com.sugoi.electricsign", 0);
					versionString = pi.versionName;
				} catch (NameNotFoundException e) {
					e.printStackTrace();
				}

				TextView title = new TextView(this);
				title.setText("Electric Sign "+versionString);
				title.setGravity(Gravity.CENTER);
				title.setTextSize((_width>=600)?48:24);
				
				RelativeLayout.LayoutParams tlp = new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
				tlp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
				tlp.addRule(RelativeLayout.CENTER_HORIZONTAL);
				_settingsView.addView(title, tlp);
			}
			
			LinearLayout topArea = new LinearLayout(this);
			topArea.setOrientation(LinearLayout.VERTICAL);
			{
				LinearLayout urlLine = new LinearLayout(this);
				{
					TextView urlText = new TextView(this);
					urlText.setText("Load: ");
					urlLine.addView(urlText);

					_urlSetting = new EditText(this);
					_urlSetting.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
					_urlSetting.addTextChangedListener(this);
					urlLine.addView(_urlSetting, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
				}
				topArea.addView(urlLine);

				addSpacing(topArea);
				LinearLayout freqLine = new LinearLayout(this);
				{
					TextView everyText = new TextView(this);
					everyText.setText("Every ");
					freqLine.addView(everyText);

					_freqCountSetting = new Spinner(this);
					_freqCountSetting.setAdapter(new FreqCountArrayAdapter(this));
					freqLine.addView(_freqCountSetting);

					_freqUnitsSetting = new Spinner(this);
					_freqUnitsSetting.setAdapter(new FreqUnitsArrayAdapter(this));
					freqLine.addView(_freqUnitsSetting);
				}
				topArea.addView(freqLine);
				addSpacing(topArea);

				LinearLayout betweenLine = new LinearLayout(this);
				{
					_enableBetweenSetting = new CheckBox(this);
					_enableBetweenSetting.setText("From");
					_enableBetweenSetting.setOnCheckedChangeListener(new OnCheckedChangeListener() {public void onCheckedChanged(CompoundButton b, boolean c) {updateGUI();}});
					betweenLine.addView(_enableBetweenSetting);

					_betweenStartSetting = new Spinner(this);
					_betweenStartSetting.setAdapter(new HourArrayAdapter(this));
					betweenLine.addView(_betweenStartSetting);

					TextView andText = new TextView(this);
					andText.setText(" to ");
					betweenLine.addView(andText);

					_betweenEndSetting = new Spinner(this);
					_betweenEndSetting.setAdapter(new HourArrayAdapter(this));
					betweenLine.addView(_betweenEndSetting);
				}
				topArea.addView(betweenLine);
				addSpacing(topArea);

				_allowSleepSetting = new CheckBox(this);
				_allowSleepSetting.setText("Allow device to sleep between updates");
				topArea.addView(_allowSleepSetting);
				addSpacing(topArea);

				_includeStatusTextSetting = new CheckBox(this);
				_includeStatusTextSetting.setText("Show Sign Status Header");
				_includeStatusTextSetting.setOnCheckedChangeListener(new OnCheckedChangeListener() {public void onCheckedChanged(CompoundButton b, boolean c) {updateGUI();}});
				topArea.addView(_includeStatusTextSetting);
				addSpacing(topArea);

				_launchAtStartupSetting = new CheckBox(this);
				_launchAtStartupSetting.setText("Auto-launch on boot");
				topArea.addView(_launchAtStartupSetting);
				addSpacing(topArea);
				
				_enableSelfStartSetting = new CheckBox(this);
				updateSelfStartText();
				_enableSelfStartSetting.setOnCheckedChangeListener(new OnCheckedChangeListener() {public void onCheckedChanged(CompoundButton b, boolean c) {setSelfStartEnabled(c);}});
				topArea.addView(_enableSelfStartSetting);
				addSpacing(topArea);

				_writeScreenSaverSetting = new CheckBox(this);
				_writeScreenSaverSetting.setOnCheckedChangeListener(new OnCheckedChangeListener() {public void onCheckedChanged(CompoundButton b, boolean c) {updateGUI();}});
				_writeScreenSaverSetting.setText("Write screenshots to screen-saver file");
				topArea.addView(_writeScreenSaverSetting);
				addSpacing(topArea);

				_filePathLine = new LinearLayout(this);
				{   
					TextView fpLabel = new TextView(this);
					fpLabel.setText("File: ");
					_filePathLine.addView(fpLabel);

					_filePathSetting = new EditText(this);
					_filePathSetting.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
					_filePathLine.addView(_filePathSetting, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
				}
				topArea.addView(_filePathLine);
			}

			RelativeLayout.LayoutParams mlp = new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
			mlp.addRule((_height>=400)?RelativeLayout.CENTER_IN_PARENT:RelativeLayout.ALIGN_PARENT_TOP);
			_settingsView.addView(topArea, mlp);

			RelativeLayout botArea = new RelativeLayout(this);
			{  		
				_goButton = new Button(this);
				_goButton.setText("Start Display");
				_goButton.setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						ElectricSignActivity.this.startDisplay();
					}
				});
				RelativeLayout.LayoutParams gop = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				gop.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
				botArea.addView(_goButton, gop);

				Button cancelButton = new Button(this);
				cancelButton.setText("Cancel");
				cancelButton.setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						ElectricSignActivity.this.finish();
					}
				});
				RelativeLayout.LayoutParams cop = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				cop.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
				botArea.addView(cancelButton, cop);
			}

			RelativeLayout.LayoutParams blp = new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
			blp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
			blp.addRule(RelativeLayout.CENTER_HORIZONTAL);
			_settingsView.addView(botArea, blp);
		}
		
		LinearLayout.LayoutParams settingsLayout = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
        int m = (_width >= 600) ? 30 : ((_width >= 300) ? 10 : 0);
        settingsLayout.setMargins(m, m, m, m);
		linLayout.addView(_settingsView, settingsLayout);

		// Set up registration for alarm events (we use these so that we'll get them even if the Nook is asleep at the time)
		_alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
		Intent intent = new Intent(ALARM_REFRESH_ACTION);
		_pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
		_alarmReceiver = new BroadcastReceiver() {
			public void onReceive(Context context, Intent intent) {
				if (isSleepAllowed()) setKeepScreenAwake(true);  // necessary to make sure the Nook doesn't go back to sleep as soon as this method returns!
				doReload();
			}
		};

		// We register dataReceiver to listen ALARM_REFRESH_ACTION
		IntentFilter filter = new IntentFilter(ALARM_REFRESH_ACTION);
		registerReceiver(_alarmReceiver, filter);

		_statusText = new TextView(this);
		_statusText.setGravity(Gravity.CENTER);
		_statusText.setTextSize(12);
		_statusText.setBackgroundColor(Color.WHITE);
		_statusText.setTextColor(Color.BLACK);
		linLayout.addView(_statusText);

		for (int i=0; i<2; i++)
		{
			_webViewDownloadErrorTimeStamps[i] = 0;
			WebView w = _webViews[i] = new WebView(this);
			w.setPictureListener(new PictureListener() {
				public void onNewPicture(WebView view, Picture picture) {
					 long ts;
					      if (view == _webViews[0]) ts = _webViewDownloadErrorTimeStamps[0];
					 else if (view == _webViews[1]) ts = _webViewDownloadErrorTimeStamps[1];
					 else
					 {
						 DoLogError("Unknown webView in picture listener!?");
						 return;
					 }
		        	 if ((picture != null)&&((System.currentTimeMillis()-ts) > 60*1000)) saveScreenshotToScreensaversFolder(picture);
		         }
		    });
		    w.setHorizontalScrollBarEnabled(false);
		    w.setVerticalScrollBarEnabled(false);
			w.setVisibility(View.GONE);  // we'll make the web view visible when the settings view is dismissed
			w.setWebViewClient(new WebViewClient() {
				public void onPageFinished(WebView view, String url) {
					super.onPageFinished(view,  url);
					if (view == getAlternateWebView()) pageFinishedLoading();
				}
				public void onReceivedError (WebView view, int errorCode, String description, String failingUrl) {
					super.onReceivedError(view, errorCode, description, failingUrl);
					DoLogError("Page download error detected: errorCode="+errorCode+", desc=["+description+"], url=["+failingUrl+"]");
					_downloadErrorCount++;
					     if (view == _webViews[0]) _webViewDownloadErrorTimeStamps[0] = System.currentTimeMillis();
					else if (view == _webViews[1]) _webViewDownloadErrorTimeStamps[1] = System.currentTimeMillis();
				}
			});
			linLayout.addView(w, new LinearLayout.LayoutParams(_width, LayoutParams.FILL_PARENT));
		}

		_contentView = linLayout;
		setContentView(_contentView);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		loadSettings();
	}
	
	private void pageFinishedLoading()
	{
		if (_downloadErrorCount == 0)
		{
			_wifiAttemptNumber = 0; // success means resetting the counter

			getCurrentWebView().setVisibility(View.GONE);
			_currentWebView = (_currentWebView==1)?0:1;  // swap the double buffer!
			getCurrentWebView().setVisibility(View.VISIBLE);

			String dateTimeStr = getDateTime();
			if (_includeStatusTextSetting.isChecked())
			{
				String battPercent = "";
				int bp = (int)(100.0*getBatteryRemainingPercent());
				if (bp >= 0) battPercent = " (Battery at "+bp+"%)";
				_statusText.setText("Updated "+dateTimeStr+battPercent);
			}
			_displayingSign = true;
		}
		_downloadErrorCount = 0;  // a clean slate for next time

		String desc;
		long reloadIntervalMillis = adjustIntervalToFitTimeWindow(_reloadIntervalMillis);
		long reloadIntervalMinutes = reloadIntervalMillis / (60*1000);
		if (reloadIntervalMinutes >= 60)
		{
			desc = Long.toString(reloadIntervalMinutes/60)+" hour(s)";
			reloadIntervalMinutes = reloadIntervalMinutes%60;
			if (reloadIntervalMinutes > 0) desc = desc + ", "+reloadIntervalMinutes+" minute(s)";
		}
		else desc = Long.toString(reloadIntervalMinutes)+" minute(s)";

		_nextUpdateTime = scheduleReload(reloadIntervalMillis);

		if (isSleepAllowed())
		{
			DoLogDebug("Sign update complete, turning off Wifi and going to sleep for "+desc+".");
			WifiManager wifi = (WifiManager) ElectricSignActivity.this.getSystemService(Context.WIFI_SERVICE);              
			wifi.setWifiEnabled(false);   // disable wifi between refreshes, to save power
			setKeepScreenAwake(false);    // back to sleep until next time!   
			_wifiShouldWorkAtTime = 0;    // note that we're not waiting for Wifi to start up anymore
		}
		else DoLogDebug("Sign update complete, next activity will occur in +"+desc+".");
	}

	public void afterTextChanged(Editable s)
	{
		_urlSetting.setBackgroundColor(isUrlValid() ? Color.WHITE : Color.RED);
		updateGUI();
	}
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {/* empty */}
	public void onTextChanged(CharSequence s, int start, int before, int count) {/*empty */}

	public void startDisplay() {
		saveSettings();

		DoLogInfo("Starting sign display "+this+" in process #" + android.os.Process.myPid());
		
		// When the user has indicated that we're running on a dedicated device,
		// also start a watchdog that will check once every 24 hours to see if
		// ElectricSign is running, and restart it, if it isn't.  This works around
		// an apparent bug where sometimes ElectricSign will mysteriously stop
		// running overnight (with no stack trace, just gone)
		Intent intent = new Intent(this, ElectricSignStartupIntentReceiver.class);
		PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
		if ((isLaunchAtStartup())&&(_enableSelfStartSetting.isChecked()))
		{
			DoLogInfo("Arming the 24-hour watchdog.");
			_alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), (24*60*60*1000), pendingIntent);
		}
		else 
		{
			DoLogInfo("Disarming the 24-hour watchdog.");
			_alarmManager.cancel(pendingIntent);
		}
		
		long freqCount = Long.parseLong(_freqCountSetting.getSelectedItem().toString());
		long millisBase = 1000;  
		String unit = _freqUnitsSetting.getSelectedItem().toString().toLowerCase();
		if (unit.contains("min"))  millisBase *= 60;
		else if (unit.contains("hour")) millisBase *= (60*60);
		else if (unit.contains("day"))  millisBase *= (24*60*60);
		_reloadIntervalMillis = freqCount * millisBase; 

		_settingsView.setVisibility(View.GONE);
		if (_includeStatusTextSetting.isChecked() == false) _statusText.setVisibility(View.GONE);
		getCurrentWebView().setVisibility(View.VISIBLE);
		_nextUpdateTime = scheduleReload(0);  // schedule a download for ASAP
		if (isSleepAllowed() == false) setKeepScreenAwake(true);
	}

	public void onDestroy() {
		unregisterReceiver(_alarmReceiver);
		super.onDestroy();
	}

	private long adjustIntervalToFitTimeWindow(long delayMillis)
	{
		if (_enableBetweenSetting.isChecked()) 
		{
			// Now, check out where we'll be at the specified time, and if it's not in the window,
			// start trying subsequence tops-of-hours until we find the next one that is in the window.
			Calendar calendar = Calendar.getInstance();
			calendar.setTimeInMillis(System.currentTimeMillis()+delayMillis);
			if (isInBetweenWindow(calendar)) return delayMillis;  // no adjustment necessary, we'll be landing inside the window anyway

			// If we got here, our target time is outside the between-window.  As a first step, move the target to the top of the next hour.
			calendar.set(Calendar.MINUTE, 0);
			calendar.set(Calendar.SECOND, 0);
			calendar.set(Calendar.MILLISECOND, 0);
			calendar.add(Calendar.HOUR_OF_DAY, 1);  // necessary to avoid any chance of the above truncation putting us back into the end of the old window

			// Now iterate over the next 24 tops-of-hours, and find the first one that gets us into our window again.
			for (int i=0; i<24; i++)
			{
				if (isInBetweenWindow(calendar)) return calendar.getTimeInMillis()-System.currentTimeMillis();
				else calendar.add(Calendar.HOUR_OF_DAY, 1);
			}
			DoLogError("adjustIntervalToFitTimeWindow("+delayMillis+") failed!  ");
		}
		return delayMillis;   // default is no adjustment
	}

	private boolean isInBetweenWindow(Calendar calendar)
	{
		int startHour = _betweenStartSetting.getSelectedItemPosition();  // 0-23
		int endHour   = _betweenEndSetting.getSelectedItemPosition();    // 0-23
		if (startHour == endHour) return true;   // e.g. between 5AM and 5AM is interpreted as "all the time"
		if (endHour == 0) endHour += 24;  // special case

		int calHour = calendar.get(Calendar.HOUR_OF_DAY);
		if (startHour < endHour)
		{
			return ((calHour >= startHour)&&(calHour < endHour));   // the intuitive case
		}
		else
		{
			return ((calHour >= startHour)&&(calHour < (endHour+24)));  // the "wraparound midnight" case
		}
	}
	
	private static boolean isConnectedToNetwork(Context context) 
	{
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = null;
        if (connectivityManager != null) {
            networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (!networkInfo.isAvailable()) networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        }
        return (networkInfo == null) ? false : networkInfo.isConnected();
    }

	public void doReload() 
	{
		WifiManager wifi = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
		if ((isSleepAllowed())&&(isConnectedToNetwork(this) == false)&&(wifi.isWifiEnabled() == false))
		{
			_wifiAttemptNumber++;
			String attStr = "(attempt #"+_wifiAttemptNumber+")";
			boolean worked = wifi.setWifiEnabled(true);
			if (_displayingSign == false) 
			{
				String statusStr;

				if (worked) statusStr = "Turning on WiFi, please wait one minute... "+attStr;
				else statusStr = "Error enabling WiFi!  "+attStr;            
				getCurrentWebView().loadDataWithBaseURL("dummy", statusStr, "text/html", "utf-8", null);
			}
			if (worked) DoLogDebug("Turning on Wifi:  Will wait 45 seconds for the Wifi to wake up and connect to the AP...  "+attStr);
			       else DoLogDebug("Error enabling Wifi, will try again soon!  "+attStr);
			_wifiShouldWorkAtTime = scheduleReload(45*1000);
		}
		else
		{
			long now = System.currentTimeMillis();
			if (now < _wifiShouldWorkAtTime)
			{
				DoLogDebug("Hmm, we got woken up a bit early, WiFi may not be available yet.  Back to sleep a bit longer...");
				scheduleReload(_wifiShouldWorkAtTime-now);
			}
			else if (now < _nextUpdateTime)
			{
				DoLogDebug("Hmm, we got woken up a bit early, it's not time for a display refresh yet.  Back to sleep a bit longer...");
				scheduleReload(_nextUpdateTime-now);
			}
			else
			{
				// Do the download in a separate thread, to avoid visits from the ANR if the network is slow
				//new DownloadFileTask().execute(getUrl());
				getAlternateWebView().loadUrl(getUrl());
			}
		}
	}
	
	private void addSpacing(LinearLayout toView)
	{
		if (_width >= 600)
		{
		   TextView dummy = new TextView(this);
		   toView.addView(dummy);
		}
	}

	private WebView getCurrentWebView()
	{
		return _webViews[_currentWebView];
	}

	private WebView getAlternateWebView()
	{
		return _webViews[(_currentWebView==1)?0:1];
	}

	private void setKeepScreenAwake(boolean awake)
	{
		int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
		Window win = getWindow();
		if (awake) 
		{
			PowerManager powerManager = (PowerManager) ElectricSignActivity.this.getSystemService(Context.POWER_SERVICE);
			powerManager.userActivity(SystemClock.uptimeMillis(), false);   //false will bring the screen back as bright as it was, true - will dim it
			win.addFlags(flags);
		}
		else win.clearFlags(flags);
	}

	private long scheduleReload(long millis) 
	{
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(System.currentTimeMillis());
		calendar.add(Calendar.SECOND, (int)(millis/1000));
		calendar.add(Calendar.MILLISECOND, (int)(millis%1000));  // done separately like this to avoid integer overflow in the 30-day case
		long wakeupTime = calendar.getTimeInMillis();
		_alarmManager.set(AlarmManager.RTC_WAKEUP, wakeupTime, _pendingIntent);
		return wakeupTime;
	}

	private double getBatteryRemainingPercent()
	{
		Intent batteryIntent = getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		int rawlevel = batteryIntent.getIntExtra("level", -1);
		double scale = batteryIntent.getIntExtra("scale", -1);
		double level = -1;
		if ((rawlevel >= 0) && (scale > 0)) level = rawlevel / scale;
		return level;
	}

	public String downloadHTML(String downloadUrl)
	{
		long startTime = System.nanoTime();

		StringBuffer buf = new StringBuffer(); 
		try
		{
			URL url = new URL(downloadUrl);

			URLConnection conn = url.openConnection();
			conn.setConnectTimeout(30*1000);
			conn.setReadTimeout(30*1000);

			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()), 8192);
			String inputLine;
			while((inputLine = in.readLine()) != null) 
			{
				buf.append(inputLine + "\r\n");
				if ((System.nanoTime()-startTime) >= (((long)30000)*1000*1000))
				{
					DoLogError("HTML download is taking too long, giving up!");
					buf = new StringBuffer();
					break;
				}
			}
			in.close();
		}
		catch(Exception e)
		{
			DoLogError("Exception caught for "+downloadUrl+", exception is: "+e.toString());
			buf = new StringBuffer();
		}

		long completeTimeMillis = (int)((System.nanoTime()-startTime)/1000000);        
		DoLogDebug("Download of ["+downloadUrl+"] took "+completeTimeMillis+" milliSeconds, downloaded "+buf.length()+" bytes.");            
		return buf.toString();
	}

	private String getDateTime() 
	{
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date date = new Date();
		return dateFormat.format(date);
	}

	public boolean dispatchTouchEvent(MotionEvent e)
	{ 
		if (_settingsView.getVisibility() != View.VISIBLE)
		{
			long now = System.currentTimeMillis();
			if ((now-_prevScreenTouchTime) > 1000) _screenTouchCount = 0;  // don't count ancient touches
			if (++_screenTouchCount == (2*3))   // three presses and three releases
			{
			   DoLogDebug("Screen touched three times within one second, exiting!");
			   finish();
			}
			else _prevScreenTouchTime = now;
			
			return true;
		}
		else return super.dispatchTouchEvent(e);      
	}

	private void saveScreenshotToScreensaversFolder(Picture p)
	{
		if (_writeScreenSaverSetting.isChecked() == false) return;

		Bitmap b = Bitmap.createBitmap(_width, _height, Config.ARGB_8888); 
		Canvas c = new Canvas(b); 
		try { 
			c.drawPicture(p);
		} 
		catch (Exception e) { 
			e.printStackTrace(); 
		} 

		// Create the necessary directory, if it isn't already there
		String filePath = _filePathSetting.getText().toString();
		String dirPath = filePath;
		int lastSlash = dirPath.lastIndexOf('/');
		if (lastSlash >= 0) dirPath = dirPath.substring(0, lastSlash);
		try {
			File f = new File(dirPath);
			f.mkdir();
		}
		catch(Exception e) {
			e.printStackTrace();
		}

		Bitmap statusBitmap = null;
		if (_includeStatusTextSetting.isChecked())
		{
			_statusText.buildDrawingCache(true);
			statusBitmap = _statusText.getDrawingCache(true);
		}

		if (statusBitmap != null)
		{
		   Bitmap combinedBitmap = Bitmap.createBitmap(_width, _height, Config.ARGB_8888);
		   Canvas combinedCanvas = new Canvas(combinedBitmap);
		   combinedCanvas.drawBitmap(statusBitmap, 0, 0, null);
		   combinedCanvas.drawBitmap(b, 0, statusBitmap.getHeight(), null);
		   saveBitmapToFile(filePath, combinedBitmap);  // so the screenshot will include both the status bar and the webview
		}
		else saveBitmapToFile(filePath, b);   // no status bar?  Then our job is easy
		
		if (_includeStatusTextSetting.isChecked()) _statusText.destroyDrawingCache();	
	}

	private void saveBitmapToFile(String filePath, Bitmap b)
	{
		// Then create the file in the directory
		File imageFile = new File(filePath);
		try {
			OutputStream fout = new FileOutputStream(imageFile);
			b.compress(Bitmap.CompressFormat.PNG, 100, fout);
			fout.flush();
			fout.close();
			DoLogDebug("Updated screenshot file "+filePath);
		} catch (Exception e) {
			DoLogError("Unable to save screenshot file "+filePath);
		}
	}
	
	private void setSpinnerSettingWithDefault(Spinner spinner, String val, String defVal)
	{
		ArrayAdapter<String> a = (ArrayAdapter<String>) spinner.getAdapter(); //cast to an ArrayAdapter
		int pos = a.getPosition(val);
		if (pos < 0) pos = a.getPosition(defVal);
		if (pos >= 0) spinner.setSelection(pos);
	}

	private void loadSettings() 
	{
		SharedPreferences s = getSharedPreferences(PREFS_NAME, 0);
		_launchAtStartupSetting.setChecked(  s.getBoolean("launchAtStartup",      false));
		_allowSleepSetting.setChecked(       s.getBoolean("allowSleep",           true));
		_writeScreenSaverSetting.setChecked( s.getBoolean("writeScreenSaver",     true));
		_urlSetting.setText(                 s.getString( "url",                  "http://news.google.com/"));
		_includeStatusTextSetting.setChecked(s.getBoolean("includestatustext",    true));
		_filePathSetting.setText(            s.getString( "filepath",             "/media/screensavers/ElectricSign/Sign.png"));
		_enableSelfStartSetting.setChecked(  s.getBoolean("selfstart",            false));
		_enableBetweenSetting.setChecked(    s.getBoolean("enablebetween",        false));
		setSpinnerSettingWithDefault(_freqCountSetting, s.getString("freq",  ""), "5");
		setSpinnerSettingWithDefault(_freqUnitsSetting, s.getString("units", ""), "Minute(s)");
		setSpinnerSettingWithDefault(_betweenStartSetting, s.getString("betweenstart", ""), "6 AM");
		setSpinnerSettingWithDefault(_betweenEndSetting,   s.getString("betweenend",   ""), "9 PM");
	}

	private void saveSettings()
	{
		SharedPreferences s = getSharedPreferences(PREFS_NAME, 0);
		SharedPreferences.Editor e = s.edit();
		e.putBoolean("launchAtStartup",  isLaunchAtStartup());
		e.putBoolean("allowSleep",       isSleepAllowed());     
		e.putBoolean("writeScreenSaver", isWriteScreenSaverFileAllowed());
		e.putString("url",               _urlSetting.getText().toString());  // not using getUrl() because I don't want auto-correct here
		e.putBoolean("includestatustext",_includeStatusTextSetting.isChecked());
		e.putString("filepath",          _filePathSetting.getText().toString());
		e.putString("freq",              _freqCountSetting.getSelectedItem().toString());
		e.putString("units",             _freqUnitsSetting.getSelectedItem().toString());
		e.putBoolean("selfstart",        _enableSelfStartSetting.isChecked());
		e.putBoolean("enablebetween",    _enableBetweenSetting.isChecked());
		e.putString("betweenstart",      _betweenStartSetting.getSelectedItem().toString());
		e.putString("betweenend",        _betweenEndSetting.getSelectedItem().toString());
		e.commit();
	}

	private String getUrl()
	{
		String ret = _urlSetting.getText().toString();
		if (URLUtil.isValidUrl(ret) == false) ret = "http://"+ret;
		return ret;
	} 

	private boolean isLaunchAtStartup()
	{
		return _launchAtStartupSetting.isChecked();
	}

	private boolean isSleepAllowed()
	{
		return _allowSleepSetting.isChecked();
	}

	private boolean isWriteScreenSaverFileAllowed()
	{
		return _writeScreenSaverSetting.isChecked();
	}

	private boolean areAllSettingsValid()
	{
		return isUrlValid();   // the only criterion for now
	}

	private boolean isUrlValid()
	{
		String url = _urlSetting.getText().toString();   // not using getUrl() becaues I don't want auto-correct here
		return ((url.length() > 0)&&((URLUtil.isValidUrl(url))||URLUtil.isValidUrl("http://"+url)));
	}

	private void updateGUI()
	{
		//_betweenStartSetting.setEnabled(_enableBetweenSetting.isChecked());
		//_betweenEndSetting.setEnabled(_enableBetweenSetting.isChecked());
		_goButton.setEnabled(areAllSettingsValid());
		_filePathLine.setVisibility(_writeScreenSaverSetting.isChecked() ? View.VISIBLE : View.INVISIBLE);
	}

	private void updateSelfStartText()
	{
		_enableSelfStartSetting.setText("Auto-Start Display in " + _selfStartCount+" seconds.");
	}

	private void setSelfStartEnabled(boolean c)
	{
		_selfStartCount = 15;
		updateSelfStartText();
		scheduleSelfStartTick();
	}

	private void doSelfStartTick()
	{
		if ((_settingsView.getVisibility() == View.VISIBLE)&&(_enableSelfStartSetting.isChecked()))
		{
			if (--_selfStartCount <= 0) startDisplay();
			else
			{
				updateSelfStartText();
				scheduleSelfStartTick();
			}
		}
	}	 

	private void scheduleSelfStartTick()
	{
		Handler h = new Handler() {
			public void handleMessage(Message msg) {  
				doSelfStartTick();  
			}  
		};
		h.sendMessageDelayed(h.obtainMessage(), 1000);
	}
	
	private void DoLogInfo(String s)
	{
	   Log.i(LOG_TAG, s);
	   DoPrivateLog("I", s);
	}
	
	private void DoLogDebug(String s)
	{
	   Log.d(LOG_TAG, s);
	   DoPrivateLog("D", s);
	}

	private void DoLogError(String s)
	{
	   Log.e(LOG_TAG, s);
	   DoPrivateLog("E", s);
	}
	
	private void DoPrivateLog(String prefix, String s)
	{
	   try {
		  final boolean _doPrivateLogging = false;  // disabled for now
		  if (_doPrivateLogging)
		  {
	         if (_privateLogFile == null) _privateLogFile = new PrintWriter(new FileWriter("/media/electricsign.log"));
		     _privateLogFile.println(prefix + " " + getDateTime() + " " + s);
		     _privateLogFile.flush();
		  }
	   }
	   catch(Exception e)
	   {
		   Log.e(LOG_TAG, "Error, couldn't open private log file! "+e);
		   System.out.println(e);
		   e.printStackTrace();
	   }
	}

	private abstract class SignSpinnerAdapter extends ArrayAdapter<String> {
		public SignSpinnerAdapter(Context ctxt) {super(ctxt, -1);}

		public View getView(int position, View convertView, ViewGroup parent) {return getSpinnerView(convertView, getItem(position), false);}
		public View getDropDownView(int position, View convertView, ViewGroup parent) {return getSpinnerView(convertView, getItem(position), true);}

		private View getSpinnerView(View convertView, String text, boolean isDropDown)
		{
			if (convertView==null) convertView = new TextView(ElectricSignActivity.this);
			TextView tv = (TextView) convertView;
			int p = ((_width>=600)&&(isDropDown)) ? 8 : 1;
			tv.setPadding(p,p,p,p);
			if (_width >= 600) tv.setTextSize(24);
			tv.setText(text);
			tv.setTextColor(Color.BLACK);
			return convertView;
		}
	}

	private class FreqCountArrayAdapter extends SignSpinnerAdapter {
		public FreqCountArrayAdapter(Context ctxt) {super(ctxt);}

		public String getItem(int position) {return Integer.toString(_freqVals[position]);}
		public int getCount() {return _freqVals.length;}
		public int getPosition(String s)
		{
			for (int i=getCount()-1; i>=0; i--) if (s.equalsIgnoreCase(getItem(i))) return i;
			return -1;
		}

		private final int _freqVals[] = {1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 30};
	};

	private class FreqUnitsArrayAdapter extends SignSpinnerAdapter {
		public FreqUnitsArrayAdapter(Context ctxt) {super(ctxt);}

		public String getItem(int position) {return _freqUnits[position];}
		public int getCount() {return _freqUnits.length;}
		public int getPosition(String s)
		{
			for (int i=getCount()-1; i>=0; i--) if (s.equalsIgnoreCase(getItem(i))) return i;
			return -1;
		}

		private final String _freqUnits[] = {"Minute(s)", "Hour(s)", "Day(s)"};
	};

	private class HourArrayAdapter extends SignSpinnerAdapter {
		public HourArrayAdapter(Context ctxt) {super(ctxt);}

		public String getItem(int position) {return _hours[position];}
		public int getCount() {return _hours.length;}
		public int getPosition(String s)
		{
			for (int i=getCount()-1; i>=0; i--) if (s.equalsIgnoreCase(getItem(i))) return i;
			return -1;
		}

		private final String _hours[] = {"12AM", "1 AM", "2 AM", "3 AM", "4 AM", "5 AM", "6 AM", "7 AM", "8 AM", "9 AM", "10 AM", "11 AM",
				                         "Noon", "1 PM", "2 PM", "3 PM", "4 PM", "5 PM", "6 PM", "7 PM", "8 PM", "9 PM", "10 PM", "11 PM"};
	};

	//private String _url = "http://sites.google.com/site/parkwoodannounce/announcements";
	private long _reloadIntervalMillis;
	private AlarmManager _alarmManager;
	private BroadcastReceiver _alarmReceiver;
	private PendingIntent _pendingIntent;

	private View _contentView;
	private RelativeLayout _settingsView;
	private TextView _statusText;
	private WebView _webViews[] = new WebView[2];   // we use two for double-buffering, that way the old content stays visible until the new is ready
	private int _currentWebView = 0;   // which of the two web views we are currently displaying
	private LinearLayout _filePathLine;

	private boolean _displayingSign = false;
	private int _wifiAttemptNumber = 0;
	private int _width;
	private int _height;

	private static final String LOG_TAG   = "ElectricSign";
	public static final String PREFS_NAME = "ElectricSignPrefs";

	private long _nextUpdateTime       = 0;
	private long _wifiShouldWorkAtTime = 0;

	private static final String ALARM_REFRESH_ACTION = "com.sugoi.electricsign.ALARM_REFRESH_ACTION";

	private EditText _urlSetting;
	private CheckBox _launchAtStartupSetting;
	private CheckBox _allowSleepSetting;
	private CheckBox _writeScreenSaverSetting;
	private Spinner  _freqCountSetting;
	private Spinner  _freqUnitsSetting;
	private CheckBox _includeStatusTextSetting;
	private EditText _filePathSetting;
	private CheckBox _enableSelfStartSetting;
	private CheckBox _enableBetweenSetting;
	private Spinner  _betweenStartSetting;
	private Spinner  _betweenEndSetting;
    private PrintWriter _privateLogFile = null;
    private long _prevScreenTouchTime = 0;
    private int _screenTouchCount = 0;
    
	private Button _goButton;
	private int _selfStartCount = 15;
	private int _downloadErrorCount = 0;
	private long _webViewDownloadErrorTimeStamps[] = new long[2];
}
