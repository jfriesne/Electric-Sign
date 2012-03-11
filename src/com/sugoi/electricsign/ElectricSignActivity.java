package com.sugoi.electricsign;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebView.PictureListener;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

public class ElectricSignActivity extends Activity 
{
   public void onCreate(Bundle savedInstanceState) 
   {
      super.onCreate(savedInstanceState);
        
      Log.i(ElectricSignActivity.LOG_TAG, "Parkwood HOA Announcements starting up!");
      requestWindowFeature(Window.FEATURE_NO_TITLE);  // might as well save some space

      // Set up registration for alarm events (we use these so that we'll get them even if the Nook is asleep at the time)
      _alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
      Intent intent = new Intent(ALARM_REFRESH_ACTION);
      _pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
      _alarmReceiver = new BroadcastReceiver() {
         public void onReceive(Context context, Intent intent) {
            setKeepScreenAwake(true);  // necessary to make sure the Nook doesn't go back to sleep as soon as this method returns!
            doReload();
         }
      };
        
      // We register dataReceiver to listen ALARM_REFRESH_ACTION
      IntentFilter filter = new IntentFilter(ALARM_REFRESH_ACTION);
      registerReceiver(_alarmReceiver, filter);
        
      DisplayMetrics displaymetrics = new DisplayMetrics();
      getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
      _width = displaymetrics.widthPixels;
      _height = displaymetrics.heightPixels;
        
      LinearLayout linLayout = new LinearLayout(this);

      _webview = new WebView(this);
      _webview.setPictureListener(new PictureListener() {
         public void onNewPicture(WebView view, Picture picture) {
            if (picture != null) SaveScreenshotToScreensaversFolder(picture);
         }
      });
        
      linLayout.addView(_webview, new LinearLayout.LayoutParams(_width, LayoutParams.FILL_PARENT));
      _contentView = linLayout;
      setContentView(_contentView);
        
      // This will prevent the screen-saver from kicking in, which would defeat our purpose
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
      _nextUpdateTime = scheduleReload(0);  // schedule a download for ASAP
   }
    
   public void onResume() {
      super.onResume();
   }
    
   public void onPause() {
      super.onPause();
   }
    
   public void onDestroy() {
       unregisterReceiver(_alarmReceiver);
       super.onDestroy();
   }
      
   private class DownloadFileTask extends AsyncTask<String,Void,String>
   {
      protected String doInBackground(String... url) {return ElectricSignActivity.this.downloadHTML(url[0]);}

      protected void onPostExecute(String html) 
      {
         int htmlIdx = html.toLowerCase().indexOf("<body");
         if (htmlIdx>=0) html = html.substring(htmlIdx);
         String battPercent = "";
         int bp = (int)(100.0*getBatteryRemainingPercent());
         if (bp >= 0) battPercent = ", "+bp+"%";

         String dateTimeStr = getDateTime();
         html = html.replace(">parkwood</a>", ">Current as of "+dateTimeStr+" ("+_completeTimeMillis+"mS"+battPercent+")</a>");
         if (html.length() > 0) 
         {
            Log.d(ElectricSignActivity.LOG_TAG, "Updating sign display at "+ dateTimeStr + " ("+html.length()+" characters)");
            _webview.loadDataWithBaseURL(_url, html, "text/html", "utf-8", null);
            _displayingSign = true;
         }

         Log.d(ElectricSignActivity.LOG_TAG, "Sign update complete, turning off Wifi and going to sleep for "+_reloadIntervalHours+" hours.");
         WifiManager wifi = (WifiManager) ElectricSignActivity.this.getSystemService(Context.WIFI_SERVICE);              
         wifi.setWifiEnabled(false);   // disable wifi between refreshes, to save power
         _wifiShouldWorkAtTime = 0;    // note that we're not waiting for Wifi to start up anymore
         _nextUpdateTime = scheduleReload(_reloadIntervalHours*60*60*1000);  // convert hours to milliseconds  
         setKeepScreenAwake(false);    // back to sleep until next time!        
      }
   }
    
   public void doReload() 
   {
      WifiManager wifi = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
      if (wifi.isWifiEnabled() == false)
      {
         _wifiAttemptNumber++;
         String attStr = "(attempt #"+_wifiAttemptNumber+")";
         boolean worked = wifi.setWifiEnabled(true);
         if (_displayingSign == false) 
         {
            String statusStr;
             
            if (worked) statusStr = "Turning on WiFi, please wait one minute... "+attStr;
                   else statusStr = "Error enabling WiFi!  "+attStr;            
            _webview.loadDataWithBaseURL("dummy", statusStr, "text/html", "utf-8", null);
         }
         if (worked) Log.d(ElectricSignActivity.LOG_TAG, "Turning on Wifi:  Will wait 45 seconds for the Wifi to wake up and connect to the AP...  "+attStr);
                else Log.d(ElectricSignActivity.LOG_TAG, "Error enabling Wifi, will try again soon!  "+attStr);
         _wifiShouldWorkAtTime = scheduleReload(45*1000);
      }
      else
      {
         long now = System.currentTimeMillis();
         if (now < _wifiShouldWorkAtTime)
         {
            Log.d(ElectricSignActivity.LOG_TAG, "Hmm, we got woken up a bit early, WiFi may not be available yet.  Back to sleep a bit longer...");
            scheduleReload(_wifiShouldWorkAtTime-now);
         }
         else if (now < _nextUpdateTime)
         {
            Log.d(ElectricSignActivity.LOG_TAG, "Hmm, we got woken up a bit early, it's not time for a display refresh yet.  Back to sleep a bit longer...");
            scheduleReload(_nextUpdateTime-now);
         }
         else
         {
            // Do the download in a separate thread, to avoid visits from the ANR if the network is slow
            new DownloadFileTask().execute(_url);
         }
      }
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
      calendar.add(Calendar.MILLISECOND, (int)millis);
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
               Log.e(ElectricSignActivity.LOG_TAG, "HTML download is taking too long, giving up!");
               buf = new StringBuffer();
               break;
            }
         }
         in.close();
      }
      catch(Exception e)
      {
         Log.e(ElectricSignActivity.LOG_TAG, "Exception caught for "+downloadUrl+", exception is: "+e.toString());
         buf = new StringBuffer();
      }
       
      _completeTimeMillis = (int)((System.nanoTime()-startTime)/1000000);        
      Log.d(ElectricSignActivity.LOG_TAG, "Download of ["+downloadUrl+"] took "+_completeTimeMillis+"milliSeconds, downloaded "+buf.length()+" bytes.");            
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
      Log.d(ElectricSignActivity.LOG_TAG, "Screen touched, exiting!");
      finish();
      return true;
   }
   
   private void SaveScreenshotToScreensaversFolder(Picture p)
   {
      Bitmap b = Bitmap.createBitmap(_width, _height, Config.ARGB_8888); 
      Canvas c = new Canvas(b); 
      try { 
         c.drawPicture(p);
      } 
      catch (Exception e) { 
         e.printStackTrace(); 
      } 
        
      // Create the necessary directory, if it isn't already there
      String screenshotDir = "/media/screensavers/ElectricSign";
      try {
         File f = new File(screenshotDir);
         f.mkdir();
      }
      catch(Exception e) {
         e.printStackTrace();
      }
 
      // Then create the file in the directory
      String fileName = screenshotDir;
      if (fileName.endsWith("/") == false) fileName = fileName + "/";
      fileName = fileName + "ElectricSignContents.png";

      File imageFile = new File(fileName);
      try {
         OutputStream fout = new FileOutputStream(imageFile);
         b.compress(Bitmap.CompressFormat.PNG, 100, fout);
         fout.flush();
         fout.close();
         Log.d(ElectricSignActivity.LOG_TAG, "Updated screenshot file "+fileName);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }
   
   private String _url = "http://sites.google.com/site/parkwoodannounce/announcements";
   private long _reloadIntervalHours = 6;

   private AlarmManager _alarmManager;
   private BroadcastReceiver _alarmReceiver;
   private PendingIntent _pendingIntent;

   private View _contentView;
   private WebView _webview;
   private boolean _displayingSign = false;
   private int _wifiAttemptNumber = 0;
   private int _completeTimeMillis = 0;
   private int _width;
   private int _height;
    
   private static String LOG_TAG = "ElectricSign";
    
   private long _nextUpdateTime       = 0;
   private long _wifiShouldWorkAtTime = 0;
    
   private static final String ALARM_REFRESH_ACTION = "com.sugoi.electricsign.ALARM_REFRESH_ACTION";
}
