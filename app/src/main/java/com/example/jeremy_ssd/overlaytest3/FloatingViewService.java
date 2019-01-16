package com.example.jeremy_ssd.overlaytest3;

import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class FloatingViewService extends Service implements View.OnClickListener {


    private WindowManager mWindowManager;
    private View mFloatingView;
    private View collapsedView;
    private View expandedView;
    private static Timer timer = new Timer();
    private Context ctx;
    private Chronometer chrono;
    private TextView chronoTexview;

    private UsageStatsManager mUsageStatsManager;
    private PackageManager mPm;
    private Long phoneUsageToday = 0L;
    private Long currentAppUsageToday = 0L;
    List<UsageEvents.Event> allEvents = new ArrayList<>();
    HashMap<String, AppUsageInfo> map = new HashMap<String, AppUsageInfo>();
    private float co2Today = 0F;

    public FloatingViewService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ctx = this;
        timer.scheduleAtFixedRate(new mainTask(), 0, 1000);
        chrono = new Chronometer(ctx);
        chrono.setBase(SystemClock.elapsedRealtime());
        //getting the widget layout from xml using layout inflater
        mFloatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null);
        chrono.start();
        mUsageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        mPm = getPackageManager();
        //setting the layout parameters
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY ,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);


        //getting windows services and adding the floating view to it
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mFloatingView, params);


        //getting the collapsed and expanded view from the floating view
        collapsedView = mFloatingView.findViewById(R.id.layoutCollapsed);
        expandedView = mFloatingView.findViewById(R.id.layoutExpanded);
        chronoTexview = mFloatingView.findViewById(R.id.chrono);

        //adding click listener to close button and expanded view
        mFloatingView.findViewById(R.id.buttonClose).setOnClickListener(this);
        expandedView.setOnClickListener(this);

        //adding an touchlistener to make drag movement of the floating widget
        mFloatingView.findViewById(R.id.relativeLayoutParent).setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_UP:
                        //when the drag is ended switching the state of the widget
                        collapsedView.setVisibility(View.GONE);
                        expandedView.setVisibility(View.VISIBLE);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        //this code is helping the widget to move around the screen with fingers
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        mWindowManager.updateViewLayout(mFloatingView, params);
                        return true;
                }
                return false;
            }
        });
    }

    private class mainTask extends TimerTask
    {
        public void run()
        {
            toastHandler.sendEmptyMessage(0);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mFloatingView != null) mWindowManager.removeView(mFloatingView);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.layoutExpanded:
                //switching views
                collapsedView.setVisibility(View.VISIBLE);
                expandedView.setVisibility(View.GONE);
                break;

            case R.id.buttonClose:
                //closing the widget
                stopSelf();
                break;
        }
    }

    private final Handler toastHandler = new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            long elapsedMillis = SystemClock.elapsedRealtime() - chrono.getBase();
            Toast.makeText(getApplicationContext(), "timer: "+elapsedMillis, Toast.LENGTH_SHORT).show();
            updateView();

            chronoTexview.setText("timer: "+DateUtils.formatElapsedTime(currentAppUsageToday / 1000));
            phoneUsageToday=0l;
        }
    };

    public void updateView() {
        UsageEvents.Event currentEvent;

        long currTime = System.currentTimeMillis();
        long startTime = currTime - 1000; //querying past three hours

        Calendar date = new GregorianCalendar();
// reset hour, minutes, seconds and millis
        date.setTimeInMillis(currTime);
        //date.set(Calendar.DAY_OF_YEAR, -6);
        date.set(Calendar.HOUR_OF_DAY, 0);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);

        //UsageStatsManager mUsageStatsManager =  (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);

        assert mUsageStatsManager != null;
        UsageEvents usageEvents = mUsageStatsManager.queryEvents(startTime, currTime);

//capturing all events in a array to compare with next element

        while (usageEvents.hasNextEvent()) {
            currentEvent = new UsageEvents.Event();
            usageEvents.getNextEvent(currentEvent);
            if (currentEvent.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    currentEvent.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                allEvents.add(currentEvent);
                String key = currentEvent.getPackageName();
// taking it into a collection to access by package name
                if (map.get(key) == null)
                    map.put(key, new AppUsageInfo(key));
            }
        }

//iterating through the arraylist
        for (int i = 0; i < allEvents.size()-1 ; i++) {
            UsageEvents.Event E0 = allEvents.get(i);
            UsageEvents.Event E1 = allEvents.get(i + 1);

//for launchCount of apps in time range
            if (!E0.getPackageName().equals(E1.getPackageName()) && E1.getEventType() == 1) {
// if true, E1 (launch event of an app) app launched
                map.get(E1.getPackageName()).launchCount++;
            }

//for UsageTime of apps in time range
            if (E0.getEventType() == 1
                    && E0.getClassName().equals(E1.getClassName())) {
                long diff =0;
                if(E1.getEventType() == 1){
                    diff = 1000L;
                }


                phoneUsageToday += diff; //gloabl Long var for total usagetime in the timerange

                map.get(E0.getPackageName()).timeInForeground += diff;
                if( E0.getEventType() == 1){
                    currentAppUsageToday = map.get(E0.getPackageName()).timeInForeground;
                }
                ApplicationInfo appInfo;
                try {
                    appInfo = mPm.getApplicationInfo(E0.getPackageName(), 0);
                } catch (Exception e) {
                    continue;
                }
                Drawable icon;
                try {
                    icon = getPackageManager().getApplicationIcon(E0.getPackageName());
                } catch (Exception e) {
                    icon = null;
                }
                Float ratioCO2 = getRationCO2(appInfo.loadLabel(mPm).toString());
                if (ratioCO2 != null) {
                    co2Today += ratioCO2 * (diff / 60000F);
                }
                map.get(E0.getPackageName()).co2 = ratioCO2;
                map.get(E0.getPackageName()).appName = appInfo.loadLabel(mPm).toString();
                map.get(E0.getPackageName()).appIcon = icon;
            }
        }
    }

    private Float getRationCO2(String appName) {
        TypedValue outValue = new TypedValue();
        switch (appName) {
            case "Facebook":
                getResources().getValue(R.dimen.Facebook, outValue, true);
                return outValue.getFloat();

            case "SeLoger":
                getResources().getValue(R.dimen.SeLoger, outValue, true);
                return outValue.getFloat();
            default:
                return null;
        }
    }
}