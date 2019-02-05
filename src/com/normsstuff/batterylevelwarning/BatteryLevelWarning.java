package com.normsstuff.batterylevelwarning;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
//import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.normstools.SaveStdOutput;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;

import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class BatteryLevelWarning extends Activity
                                 implements ShowEditListFragment.TimesListHandler {
	final String VersionID = "Battery Level Warning - February 3, 2019 @1300\n";
	
	String rootDir = Environment.getExternalStorageDirectory().getPath();
	final String LogFilePathPfx = rootDir + "/BattLvl_log_";
	private String LevelLogFilename = rootDir + "/BatteryLevelLog.txt";
	
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HHmmss", Locale.US); // builds Filename
    SimpleDateFormat sdfTime = new SimpleDateFormat("yyyy-MM-dd 'T' HH:mm:ss", Locale.US); // showTime
    SimpleDateFormat sdf4Time = new SimpleDateFormat("MMM dd HH:mm", Locale.US);

    
    // Constants used for Intents and saving in Bundles
	private static final int RESULT_SETTINGS = 310;
    final int GetTimeID = 234;
    final int NotifyID = 34949;
    final String RunningAlarmS = "BattLvlRunAlarm";
    final String TimeNextAlarmS = "TimeNextAlarm";
    final static public String StartedByID = "StartedBy";
    final static public String TimeOfAlarmID = "timeOfAlarm";
    final String NoAlarmSet = "Not set";
    final String TimeMsgS = "TimeMsg";
    final String TimesFilename = "BLW_times.txt";


	boolean debugging = true;
	boolean doingTesting = true; 
	boolean saveSTDOutput = false;
	boolean runningAlarm = false;
	private String timeNextAlarm = NoAlarmSet;  // set by setupAlarm 
	private String timeThisAlarm = "";
	
    private Thread.UncaughtExceptionHandler lastUEH = null;
    PendingIntent alarmPI;

	
    //- - - - - - - - - - - - - - - - - - - - - - -
    // Define inner class to handle exceptions
    class MyExceptionHandler implements Thread.UncaughtExceptionHandler {
        public void uncaughtException(Thread t, Throwable e){
           java.util.Date dt =  new java.util.Date();
           String fn = LogFilePathPfx + "exception_" + sdf.format(dt) + ".txt";
           try{ 
              PrintStream ps = new PrintStream( fn );
              e.printStackTrace(ps);
              ps.close();
              System.out.println("wrote trace to " + fn);
              e.printStackTrace(); // capture here also???
              SaveStdOutput.stop(); // close here vs calling flush() in class 
           }catch(Exception x){
              x.printStackTrace();
              Toast.makeText(getApplicationContext(), "BLW MyException exception " + x, 
 		             Toast.LENGTH_LONG).show();

           }
           lastUEH.uncaughtException(t, e); // call last one  Gives: "Unfortunately ... stopped" message
           return;    //???? what to do here
        }
    }  // end class
    
	//-------------------------------------------------------------------------
	// Define class to hold hour and minute values and build a display String
    static class HHMM implements Parcelable {
		int hour;
		int minute;
		
		public HHMM(String hhmm) {
			String[] split = hhmm.split(":");
			int hh = Integer.parseInt(split[0]);
			if(hh < 0 || hh > 23)
				throw new IllegalArgumentException("Invalid hour:" + hh);
			int mm = Integer.parseInt(split[1]);
			if(mm < 0 || mm > 59)
				throw new IllegalArgumentException("Invalid minute:"+mm);

			hour = hh;
			minute = mm;
		}

		public HHMM(int hh, int mm){
  			if(hh < 0 || hh > 23)
				throw new IllegalArgumentException("Invalid hour:" + hh);
			if(mm < 0 || mm > 59)
				throw new IllegalArgumentException("Invalid minute:"+mm);

			hour = hh;
			minute = mm;
		}

      //  Build a Calendar for this time today
      public Calendar getTimeToday() {
         Calendar cal = Calendar.getInstance();
         cal.set(Calendar.SECOND, 0);            //<<<<<< Only hours and minutes ???
         cal.set(Calendar.HOUR_OF_DAY, hour);
         cal.set(Calendar.MINUTE, minute);
         return cal;
      }
		
      //  Return hh:mm with 0 padding
		public String toString() {
			return new StringBuilder().append(padding_str(hour))
                                   .append(":")
                                   .append(padding_str(minute))
                                   .toString();
		}
		private  String padding_str(int c) {
			if (c >= 10)
			   return String.valueOf(c);
			else
			   return "0" + String.valueOf(c);
		}

		//- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
		// Define methods for Parcelable

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel out, int arg1) {
			out.writeInt(hour);
			out.writeInt(minute);
		}
	    public static final Parcelable.Creator<HHMM> CREATOR
                = new Parcelable.Creator<HHMM>() 
            {
			     public HHMM createFromParcel(Parcel in) {
			         return new HHMM(in);
			     }
			
			     public HHMM[] newArray(int size) {
			         return new HHMM[size];
			     }
	         };
 
		 private HHMM(Parcel in) {
		     hour = in.readInt();
		     minute = in.readInt();
		 }
	}  // end class HHMM  ---------------------------------


	private List<HHMM> times = new ArrayList<>();  // save the times to check the level
	public  List<HHMM> getTimes() {
		return times;
	}
	private HHMM nextTime = new HHMM(10,0);        //<<<<<
	
    // For Speaking the battery level
    TextToSpeech tts1;
    final String UtteranceId = "The message";
    boolean ttsReady = false;
    int nbrRepeats = 1;      //<<<<<< say it 1+ times
    int timeBetweenAlarms = 300;    // used with testing for repeated alarms
    int batteryWarningLevel = 100;  // level to give warning at
    private boolean writeLevelsToFile = false;
    private int batteryPct = -99;
	private NotificationManager notifMngr = null;


	//-------------------------------------------------------------------------------
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_battery_level_warning);
		
		getPreferences();
		
		// Check if API level 23 - for Permissions
		if(Build.VERSION.SDK_INT > 22) //Build.VERSION_CODES.LOLLIPOP_MR1) 
		{
//			String[] perms = {"android.permission.WRITE_EXTERNAL_STORAGE"};
//			int permsRequestCode = 200;
//			requestPermissions(perms, permsRequestCode);
		}
		
		if (debugging) {
       	 	// Quick and dirty debugging
            java.util.Date dt =  new java.util.Date();
            String fn = LogFilePathPfx + sdf.format(dt) + ".txt";   // 2014-02-02T193504

            try {
   				SaveStdOutput.start(fn);
   				saveSTDOutput = true;    // remember
	   	    } catch (IOException e) {
	   			e.printStackTrace();
	             Toast.makeText(getApplicationContext(), "BLW SaveStdOutput exception " + e, 
	 		             Toast.LENGTH_LONG).show();

	   	    }
//            showMsg("debugging is on, fn="+fn);
		}  // end debugging
		
		System.out.println("BLW onCreate() at "+ sdfTime.format(new Date()));
		
        lastUEH = Thread.getDefaultUncaughtExceptionHandler(); // save previous one
        Thread.setDefaultUncaughtExceptionHandler(new MyExceptionHandler());
       
		try{
			FileInputStream fis = openFileInput(TimesFilename);
			byte[] bfr = new byte[1000];    // should never be this long
			int nbrRd = fis.read(bfr);
			String listS = new String(bfr, 0, nbrRd);
			System.out.println("BLW onCreate  times=" + listS);
			fis.close();
		    //<<< split on ", " from toString()
		    String[] theTimes = listS.split(", "); 

		     //  Clear and rebuild list
		     times.clear();
		     for(String hhmmS : theTimes) {
		        times.add(new HHMM(hhmmS));          // validates array values
		     }

		}catch(FileNotFoundException fnf) {
			fnf.printStackTrace();
		    loadSomeTimes();  // first time -> prime the pump
		    saveTimesList();
		}catch(Exception x) {
			x.printStackTrace();
		}

        //  What was passed to us at startup?
      	Intent intent = getIntent();
    	System.out.println("BLW onCreate() intent="+intent 
    			+ "\n >>data="+intent.getData()
    			+ "\n >>extras="+ intent.getExtras()
    			+ "\n savedInstanceState=" + savedInstanceState);
    	
    	// Were we restarted?
    	if(savedInstanceState != null) {
    		String theTime = savedInstanceState.getString(TimeMsgS);
    		System.out.println("BLW sII with time=" + theTime);
    	}
    	
       // Were we started by an Intent?
		Bundle bndl = intent.getExtras();
		if(bndl != null) {
    		Set<String> set = bndl.keySet();
    		System.out.println(" >>bndl keySet=" + Arrays.toString(set.toArray()));
    		String startedBy = bndl.getString(StartedByID); //  were we started by another program
    		String timeOfAlarm = bndl.getString(TimeOfAlarmID);
    		System.out.println("BLW startedBy=" + startedBy +", timeOfAlarm="+timeOfAlarm
    				+ " at "+ sdfTime.format(new Date()));
    		
    		// Check if started by an Alarm >> Should check and say msg 
    		// and start another Alarm for the next time
    		boolean batteryLevelLow = false;
    		if(startedBy != null) {
	    		batteryPct = getBatteryLevel();
	    		timeThisAlarm = timeOfAlarm; // ?? why extra variable
	    		if(batteryPct <= batteryWarningLevel){
	    			sayBatteryLevel(timeOfAlarm);
	    			batteryLevelLow = true;
	    		}
	    		
	    		if(writeLevelsToFile){
	    			try {
	    			   PrintStream ps = new PrintStream( new BufferedOutputStream(
	    		                   new FileOutputStream(LevelLogFilename, true)), true);  // append to current & autoflush
	    			   ps.println("Battery level " + batteryPct +" at "+ sdfTime.format(new Date()));
	    			   ps.close();
	    			}catch(Exception x){
	    				x.printStackTrace();
	    			}
	    		}
    			runningAlarm = true;

	    		//  If we were started by the Alarm receiver, do it again
    			// or if level is low - check again soon
	    		if(doingTesting || batteryLevelLow){
	    			setupAlarm(timeBetweenAlarms); //<<<<<<<<< Start in xx seconds??? testing
	    			setPreferences();
	   			
	    		}else{
	    			findSetNextAlarm();
	    		}
	    		//  Update notification
	    		doNotify();
	    		
	    		//  We're done, exit soon
    	        Runnable runnable = new Runnable() {
    	            public void run() {
    	            	System.out.println("BLW runnable calling finish() at " 
    	            					   + sdfTime.format(new java.util.Date()));
    	   	   			finish();      // Done ????  <<<<<<<<<<< NOTE
    	            }
    	        };
    	        new Handler().postDelayed(runnable, 5000);

    		} // end startedBy != null <<< We were started by an Alarm
    		

        } // end bundle != null
		
		final Button startSensorBtn = (Button) findViewById(R.id.startSensor);
		String btnText = (runningAlarm ? "Cancel" : "Set") + " Battery Warning";
		startSensorBtn.setText(btnText);
		
		startSensorBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
	    		System.out.println("BLW button clicked runningAlarm="+runningAlarm
	    				           +", alarmPI="+alarmPI);
	    		
		    	if(!runningAlarm) {
		    		// If not running, start it up
	    			runningAlarm = true;
	    			setPreferences();
	    			
		    		if(doingTesting) {
		    			setupAlarm(timeBetweenAlarms); //<<<<<<<<< Start in xx seconds??? testing
		    			
		    		}else {
		    			findSetNextAlarm();
		    		}
		    		
		    		//  Set Notification
		    		doNotify();
		    				
		    	}else {
		    		// If it is currently running, Turn off alarm
		    		runningAlarm = false;
		    		timeNextAlarm = NoAlarmSet;
		    		setPreferences();
		    		
		    	    if(alarmPI == null) {
		    	    	Intent intent = new Intent(getBaseContext(), OnAlarmReceive.class);
		    	        alarmPI = PendingIntent.getBroadcast(
		                        BatteryLevelWarning.this, 0, intent,
		                        PendingIntent.FLAG_UPDATE_CURRENT);
		    	    }
	    	        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
	    	        alarmManager.cancel(alarmPI);
	    	        alarmPI = null;
	    	        System.out.println("BLW cancelled Alarm at "
	    	                           + sdfTime.format(new java.util.Date()));
	    	        
	    			notifMngr = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
	    	    	if(notifMngr != null) {
	    	    		notifMngr.cancel(NotifyID);   // remove notify
	    	    	}else{
	    	    		System.out.println("BLW notifyMngr == null");
	    	    	}
	    	        
		    	}
		    	
		    	// Change buttons text to match state
    			String btnText = (runningAlarm ? "Cancel" : "Set") + " Battery Warning";
    			startSensorBtn.setText(btnText);
			}
		});
		
		setTextValues();  // Build what is displayed on screen
	
	} // end onCreate()
	
	//-----------------------------------------------------------------
	private void loadSomeTimes() {
		// Load some times for testing
		times.add(new HHMM(7,15));
		times.add(new HHMM(10,0));
		times.add(new HHMM(15,0));
		times.add(new HHMM(15,30)); //<<<<<<<<< xtra for testing
		times.add(new HHMM(16,0));  //<<<<<<<<< xtra for testing
		times.add(new HHMM(16,30)); //<<<<<<<<< xtra for testing
		times.add(new HHMM(17,0));  //<<<<<<<<< xtra for testing
		times.add(new HHMM(17,30)); //<<<<<<<<< xtra for testing
		times.add(new HHMM(18,0));  //<<<<<<<<< xtra for testing
		times.add(new HHMM(21,45));
	}
	
	//  Define methods for TimesListHandler
	public void saveTimesList() {
		try{
			FileOutputStream fos =  openFileOutput(TimesFilename, MODE_PRIVATE); 
		    String listS = times.toString();
		    fos.write(listS.substring(1,listS.length()-1).getBytes());
		    fos.close();
		    System.out.println("BLW wrote times to " + getFilesDir());
		}catch(Exception x){
			x.printStackTrace();
		}
	}
	public List<HHMM> getTimesList() {
		return times;
	}
	public void setTimesList(List<HHMM> list) {
		times = list;
	}
	//=============================================================
   	// Check if date/time in cal is before the current time
   	public boolean isBeforeNow(Calendar cal){
           Calendar now = Calendar.getInstance();
           return (now.compareTo(cal) > 0);
   	}
   	public boolean isAfterNow(Calendar cal){
           Calendar now = Calendar.getInstance();
           return (now.compareTo(cal) < 0);
   	}

	
	//-------------------------------------------
	// Search times list for next time to use, compute seconds
	// and call setupAlarm to set an alarm after that time
	private void findSetNextAlarm() {
		// find time for next check
	     nextTime = findNextTime(); 
	     //  What if tomorrow  ?
	     Calendar nextEventCal = nextTime.getTimeToday();
	     if(isBeforeNow(nextEventCal)){
	       	  nextEventCal.add(Calendar.DAY_OF_MONTH, 1);  // move to tomorrow  
	     }
	      // Now set alarm for what was found
	      Calendar cal = Calendar.getInstance();
	      long nowInMillis = cal.getTimeInMillis();          // current time
	      long timeOfEvent = nextEventCal.getTimeInMillis();
	      int duration = (int)((timeOfEvent - nowInMillis) / 1000);
	      System.out.println("BLW Next event at: "+nextEventCal.getTime() 
	    		              +" in " + duration);
	      setupAlarm(duration);
	      setPreferences();
	}
	
	//--------------------------------
	private void doNotify() {
		notifMngr = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
	
		// Build an intent with some data
		Intent intent4P = new Intent(getBaseContext(), BatteryLevelWarning.class);
		// How to pass this to NotifyMessage -> Need flag on PI!!!!
		intent4P.putExtra("Notify at", sdfTime.format(new java.util.Date()));
		
		// This pending intent will open after notification click
		PendingIntent pi = PendingIntent.getActivity(getBaseContext(), 0, intent4P, 
				                                     PendingIntent.FLAG_UPDATE_CURRENT);
		
		Notification note = new Notification.Builder(getBaseContext())
							.setSmallIcon(R.drawable.ic_launcher_small)
							.setWhen(System.currentTimeMillis())
							.setTicker("Battery Level Warning")
							.setContentTitle("Battery Level Warning is running")
							.setContentText("Next check @ " + timeNextAlarm 
									+ " for " + batteryWarningLevel +"%")
							.setContentIntent(pi)
							.build();
		notifMngr.notify(NotifyID, note);
	}
	
	//==============================
	@Override
	public void onResume() {
		super.onResume();
		System.out.println("BLW onResume at " + sdfTime.format(new Date()));
		// What else should we do here ???
	}

	//==================================================================
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.battery_level_warning, menu);
		return true;
	}
	
	//------------------------------------------------
	private void getPreferences() {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		debugging = preferences.getBoolean("set_debug_text", false);
		doingTesting = preferences.getBoolean("do_testing", false);
		runningAlarm = preferences.getBoolean(RunningAlarmS, false);
		timeNextAlarm = preferences.getString(TimeNextAlarmS, NoAlarmSet);
		String battLevelS = preferences.getString("batteryLevel", "25");
		batteryWarningLevel = Integer.parseInt(battLevelS);
		writeLevelsToFile = preferences.getBoolean("write_levels", false);
	}
	
	private void setTextValues() {
		//  Display some info for user
		TextView tv = (TextView)findViewById(R.id.message1);
		tv.setText("Debug is "+(debugging ? "On" : "Off"));
		tv = (TextView)findViewById(R.id.message2);
		tv.setText("Continuous testing is "+(doingTesting ? "On" : "Off")
		          + "\nTime between testing alarms is " + timeBetweenAlarms);
		tv = (TextView)findViewById(R.id.message3);
		tv.setText("Battery level for warning is " + batteryWarningLevel);
		tv = (TextView)findViewById(R.id.message4);
		tv.setText("Time next alarm: " + timeNextAlarm + ", runningAlarm = "+runningAlarm);
	}
	
	private void setPreferences() {
		SharedPreferences.Editor editor = PreferenceManager
			                              .getDefaultSharedPreferences(this).edit();
		editor.putBoolean(RunningAlarmS, runningAlarm);
		editor.putString(TimeNextAlarmS, timeNextAlarm);
		editor.commit();
	}

	//-------------------------------------------------------------------
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
	    switch (item.getItemId()) {	
	    case R.id.action_settings:
			// Starts the Settings activity on top of the current activity
			Intent intent = new Intent(this, SettingsActivity.class);
			startActivityForResult(intent, RESULT_SETTINGS);
			return true;
			
	    case R.id.set_times:
//	    	Intent intPT = new Intent(this, GetTimesActivity.class); 
//	    	putTimesInIntent(intPT);
//	    	startActivityForResult(intPT, GetTimeID);
	        ShowEditListFragment showELF = new ShowEditListFragment();
//	        showELF.setTimesList(times);  // pass List
	        showELF.setTimesListHandler(this);
	
	        System.out.println("BLW Creating fragment for edit this="+this);

	        FragmentTransaction ft3 = getFragmentManager().beginTransaction();
	        showELF.show(ft3, "dialog");

	    	return true;
			
        case R.id.about:
            showMsg("Norm's Battery Level program\n"
            		+ VersionID
            		+ "email: radder@hotmail.com");
            return true;

			
        case R.id.exit:
        	finish();
        	return true;
	    	
	   	default:
	   		break;

		}
		return super.onOptionsItemSelected(item);
	}

	//------------------------------------------------------------------------------
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		System.out.println("onActRes() intent=" + data + ", result="+resultCode);

		switch (requestCode) {
		case RESULT_SETTINGS:
			getPreferences();
			setTextValues();  // show new values
			break;
			
		case GetTimeID:
			showMsg("onActRes got GetTimeID");
			break;
			
		default:
			System.out.println("BLW onActRes unknown reqCode="+requestCode);
		}
	}
	
    /** ------------------------------------------------------------------------------
    * Sets up the alarm
    *
    * @param seconds
    *            - after how many seconds from now the alarm should go off
    */
    private void setupAlarm(int seconds) {
      AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
      Intent intent = new Intent(getBaseContext(), OnAlarmReceive.class);
      
       // Getting current time and add the seconds in it
      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.SECOND, seconds);
      timeNextAlarm = sdf4Time.format(cal.getTimeInMillis());        // Feb 02 13:59

      intent.putExtra(TimeOfAlarmID, timeNextAlarm);  // pass the time
      
      alarmPI = PendingIntent.getBroadcast(
              BatteryLevelWarning.this, 0, intent,
              PendingIntent.FLAG_UPDATE_CURRENT);

      alarmManager.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), alarmPI);
      
      System.out.println("BLW Set the alarm for " + seconds + " seconds at "
              + sdfTime.format(new Date()) + " alarm at " + timeNextAlarm);

      Toast.makeText(getApplicationContext(), "Set alarm in "+ seconds + " for " + cal.getTime(), 
    		             Toast.LENGTH_SHORT).show();
    }

    //----------------------------------------------------------------------------
    private int getBatteryLevel() {
    	IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    	Intent batteryStatus = registerReceiver(null, ifilter);
    	int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    	int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

    	int batteryPct = (int)((level*100) / (float)scale);
    	System.out.println("Battery level="+batteryPct);
    	return batteryPct;
    }
    
    //------------------------------------------------
    private void sayBatteryLevel(String msg) {	
    	if(tts1 == null) {
	        tts1 = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
	            @Override
	            public void onInit(int status) {
	               System.out.println("BLW TTS onInit status="+status);	
	               if(status != TextToSpeech.ERROR) {
	                  tts1.setLanguage(Locale.US);
	                  ttsReady = true;
	                  BatteryLevelWarning.this.sayTheMessage();
	               }
	            }
	         });
	         
	         tts1.setOnUtteranceProgressListener(new UtteranceProgressListener() {
	       	   public void onDone(String ut) {
	                  Toast.makeText(getApplicationContext(), "onDone for ut="+ut, Toast.LENGTH_SHORT).show();
	       	   }

	   			@Override
	   			public void onStart(String utteranceId) {
	   				
	   			}

	   			@Override
	   			@Deprecated
	   			public void onError(String ut) {
	   	            Toast.makeText(getApplicationContext(), "onError for ut="+ut, Toast.LENGTH_SHORT).show();
	   			}
	         });
    	} // end tts1 == null
    	
    	String msgText = "Battery level is " + batteryPct;
    	
    	if(ttsReady) {
			int res = tts1.speak(msgText, TextToSpeech.QUEUE_FLUSH, null);  //OLD version
//       	  int res = tts1.speak(text, TextToSpeech.QUEUE_FLUSH, null, UtteranceId);  // -1
			Toast.makeText(getApplicationContext(), msgText + " Xres="+res, Toast.LENGTH_SHORT).show();

    	} else {
    		theMessage = msgText; // save
    	}

    } // end sayBatteryLevel
	//-----------------------------------------------------
	//  Special Q&D code to say message when ready
	String theMessage = "";
	
	private void sayTheMessage() {
		String bltMsg =  theMessage;
		for(int i=0; i < nbrRepeats; i++) {  //<<<<<<< Multiple copies of message works
			bltMsg = bltMsg + "\n" + theMessage;
		}
		bltMsg = bltMsg + "\nTime for alarm was "+ timeThisAlarm;
		
		int res = tts1.speak(bltMsg, TextToSpeech.QUEUE_FLUSH, null);  //OLD version
//   	  int res = tts1.speak(text, TextToSpeech.QUEUE_FLUSH, null, UtteranceId);  // -1
		Toast.makeText(getApplicationContext(), bltMsg + " res="+res, Toast.LENGTH_SHORT).show();
	}
	
	//  Search times list for next time 
	public HHMM findNextTime() {
	      // Find next time
	      //  Assume times are in order
	      for(HHMM hhmm : times) {
	         if(isAfterNow(hhmm.getTimeToday())) {
	            return hhmm;           // this one is in the future
	         }
	      }
	      //  If not found, then use first time  which is tomorrow
	      return times.get(0);
	}

	//--------------------------------------------------------------
	//  Save values to allow us to restore state when rotated
	@Override
	public void onSaveInstanceState(Bundle bndl) {
		super.onSaveInstanceState(bndl);
		String time = sdfTime.format(new java.util.Date());
		bndl.putString(TimeMsgS, time);
		System.out.println("BLW onSaveInstanceState() at "+ time);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		System.out.println("BLW onPause() at "+ sdfTime.format(new java.util.Date()));
	}
	
    @Override
    public void onDestroy() {
    	super.onDestroy();

		System.out.println("BLW onDestroy at "+ sdfTime.format(new java.util.Date()));
		
		if(saveSTDOutput)  { // was it started?
			SaveStdOutput.stop();  //<<<<<<<<<<< DON'T stop if others using it!!!
		}
    }

	//-------------------------------------------------------------------------    
//  Show a message in an Alert box
	private void showMsg(String msg) {

		AlertDialog ad = new AlertDialog.Builder(this).create();
		ad.setCancelable(false); // This blocks the 'BACK' button
		ad.setMessage(msg);
		ad.setButton(DialogInterface.BUTTON_POSITIVE, "Clear message", 
		  new DialogInterface.OnClickListener() {
		    @Override
		    public void onClick(DialogInterface dialog, int which) {
		        dialog.dismiss();                    
		    }
		});
		ad.show();
	}

}
