package com.abw4v.d2clonetracker;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.ArrayMap;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.abw4v.d2clonetracker.MyService.*;

public class MainActivity extends AppCompatActivity {

    // Constants
    static final String PREFS_HARDCORE = "hardcore";
    static final String PREFS_LADDER = "ladder";
    static final String PREFS_REGION = "region";
    static final String PREFS_PERFORMANCE = "performanceMode";
    static final String PREFS_ERROR_NETWORK = "showNetworkErrors";

    static final String d2rURL = "https://diablo2.io/";
    static final String faqURL = "https://github.com/armlesswunder/d2rCloneTrackerAndroid#faq";

    // TODO: make sure this is right before any release
    static final boolean paid = true;

    final List<String> listRegion = new ArrayList<>(Arrays.asList("All", "Americas", "Europe", "Asia"));
    final List<String> listHardcore = new ArrayList<>(Arrays.asList("Both", "Hardcore", "Softcore"));
    final List<String> listLadder = new ArrayList<>(Arrays.asList("Both", "Ladder", "Non-Ladder"));

    // Views
    Spinner spinnerHardcore;
    Spinner spinnerLadder;
    Spinner spinnerRegion;

    Switch switchErrorNetwork;
    Switch switchPerformance;

    ProgressBar progressBar;

    // Vars
    MyService myService;

    AtomicBoolean myServiceBound = new AtomicBoolean(false);

    ServiceConnection myServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            MyService.MyServiceBinder binder = (MyService.MyServiceBinder) service;
            myService = binder.getService();
            myServiceBound.set(true);
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            myServiceBound.set(false);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getDefaults();
        linkViewProperties();
        createNotificationChannel();
        createErrorChannel();
    }

    void getDefaults() {
        SharedPreferences prefs = getSharedPreferences("default", Context.MODE_PRIVATE);
        modeHardcore = prefs.getInt(PREFS_HARDCORE, 0);
        modeLadder = prefs.getInt(PREFS_LADDER, 0);
        modeRegion = prefs.getInt(PREFS_REGION, 0);
        modePerformance = prefs.getInt(PREFS_PERFORMANCE, 1);
        showErrorNetwork = prefs.getBoolean(PREFS_ERROR_NETWORK, false);
    }

    // start a service and then bind the app to it, so it can run after the app is closed
    private void startService(Context context) {
        Intent intent = new Intent(context, MyService.class);
        startForegroundService(intent);
        bindService(intent, myServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (myServiceBound.get()) {
            unbindService(myServiceConnection);
            myServiceBound.set(false);
        }
    }

    void linkViewProperties() {
        progressBar = findViewById(R.id.progressBar);

        findViewById(R.id.btnStart).setOnClickListener(v -> startAlert());
        findViewById(R.id.btnStop).setOnClickListener(v -> stop());
        findViewById(R.id.btnFAQ).setOnClickListener(v -> btnFAQPressed());
        findViewById(R.id.btnD2IO).setOnClickListener(v -> goToD2io());

        TextView lblVersion = findViewById(R.id.lblVersion);
        PackageInfo pInfo = null;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionLocal = pInfo.versionName;
            lblVersion.setText("V " + versionLocal);
        } catch (PackageManager.NameNotFoundException e) {
            lblVersion.setText("V (?)");
            e.printStackTrace();
        }

        switchErrorNetwork = findViewById(R.id.switchErrorNetwork);
        switchErrorNetwork.setChecked(showErrorNetwork);
        switchErrorNetwork.setOnClickListener(v -> {
            showErrorNetwork = switchErrorNetwork.isChecked();
            setDefaults();
        });

        switchPerformance = findViewById(R.id.switchPerformance);
        switchPerformance.setChecked(modePerformance == 2);
        switchPerformance.setOnClickListener(v -> {
            if (paid) {
                modePerformance = switchPerformance.isChecked() ? 2 : 1;
            } else {
                paidAlert();
                switchPerformance.setChecked(false);
                modePerformance = 1;
            }
            setDefaults();
        });

        spinnerHardcore = findViewById(R.id.spinnerHardcore);
        spinnerLadder = findViewById(R.id.spinnerLadder);
        spinnerRegion = findViewById(R.id.spinnerRegion);

        ArrayAdapter<String> hardcoreAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, listHardcore);
        ArrayAdapter<String> ladderAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, listLadder);
        ArrayAdapter<String> regionAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, listRegion);

        spinnerHardcore.setAdapter(hardcoreAdapter);
        spinnerLadder.setAdapter(ladderAdapter);
        spinnerRegion.setAdapter(regionAdapter);

        hardcoreAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        ladderAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        regionAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);

        spinnerHardcore.setSelection(modeHardcore);
        spinnerLadder.setSelection(modeLadder);
        spinnerRegion.setSelection(modeRegion);

        spinnerHardcore.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                modeHardcore = position;
                setDefaults();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        spinnerLadder.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                modeLadder = position;
                setDefaults();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        spinnerRegion.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                modeRegion = position;
                setDefaults();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    void setDefaults() {
        getSharedPreferences("default", Context.MODE_PRIVATE).edit()
                .putInt(PREFS_HARDCORE, modeHardcore)
                .putInt(PREFS_LADDER, modeLadder)
                .putInt(PREFS_REGION, modeRegion)
                .putInt(PREFS_PERFORMANCE, modePerformance)
                .putBoolean(PREFS_ERROR_NETWORK, showErrorNetwork)
                .apply();
    }

    public void startAlert() {
        progressBar.setVisibility(View.VISIBLE);
        statusList.clear();
        getData();
    }

    void paidAlert() {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
        alertBuilder.setTitle("NOTICE");
        alertBuilder.setMessage("Performance mode is available for paid users in United States only! Get status updates twice as often! A portion of the proceeds will got to Teebling's site (Lets support the backend that makes this app work!)");
        alertBuilder.show();
    }

    public void getData() {

        MyService.startAt = System.currentTimeMillis();
        RequestQueue queue = Volley.newRequestQueue(this);
        String url = getURL();

        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
            response -> {
                try {
                    //Long stamp = System.currentTimeMillis();
                    JSONArray jArr = new JSONArray(response);
                    for (int i = 0; i < jArr.length(); i++) {
                        JSONObject json = jArr.getJSONObject(i);
                        Status newStatus = new Status(json);
                        //newStatus.prevStamp.add(0, stamp);
                        newStatus.prevStatus.add(0, newStatus.status);
                        statusList.add(newStatus);
                    }
                    //Intent intent = new Intent(this, MyReceiver.class);
                    //pendingIntent = PendingIntent.getBroadcast(this.getApplicationContext(), 234, intent, PendingIntent.FLAG_IMMUTABLE);
                    //alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

                    //MyReceiver.alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(System.currentTimeMillis() + MyReceiver.getStartOffset(), pendingIntent), pendingIntent);
                    //keepAwake(this);
                    runOnUiThread(() -> progressBar.setVisibility(View.GONE));
                    //MyReceiver.showNotification(MainActivity.this, System.currentTimeMillis() + MyReceiver.getStartOffset());
                    //playAlertSound(this);

                    startService(MainActivity.this.getApplicationContext());
                } catch (Throwable e) {
                    e.printStackTrace();
                    runOnUiThread(() -> progressBar.setVisibility(View.GONE));
                    showError(MainActivity.this, e);
                }
            }, error -> {
                //getData();
                runOnUiThread(() -> progressBar.setVisibility(View.GONE));
                showError(MainActivity.this, new Throwable(ERROR_MSG_NETWORK_INITIAL));
                error.printStackTrace();
        }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String>  params = new HashMap<>();
                params.put("User-Agent", "ArmlessWunder");

                return params;
            }
        };

        queue.add(stringRequest);
    }

    public static String getURL() {
        String urlbase = "https://diablo2.io/dclone_api.php";
        String hardcore = "";
        String ladder = "";
        String query = "";
        String and = "&";

        if (modeHardcore != 0) {
            hardcore = "hc=" + modeHardcore;
            query = "?";
        }

        if (modeLadder != 0) {
            ladder = "ladder=" + modeLadder;
            query = "?";
        }

        if (ladder.isEmpty() || hardcore.isEmpty()) {
            and = "";
        }

        return urlbase + query + hardcore + and + ladder;
    }

    static void keepAwake(Context context) {
        //16 hours
        long timeInMillis = (System.currentTimeMillis() + 1000L*60L*60L*16L);
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean isScreenOn = pm.isInteractive();
        if (!isScreenOn)
        {
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,"MyLock:");
            wl.acquire(timeInMillis);
            wl_cpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MyCpuLock:");

            wl_cpu.acquire(timeInMillis);
        }
    }

    void showError(Context context, Throwable e) {
        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            NotificationCompat.Builder builder = getNotification(context, e);
            notificationManager.notify(1, builder.build());
        } catch(Throwable e1) {
            e.printStackTrace();
        }
    }

    void stop() {
        if (myServiceBound.get()) {
            unbindService(myServiceConnection);
            myServiceBound.set(false);
        }
        getApplicationContext().stopService(new Intent(getApplicationContext(), MyService.class));
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(MyService.NOTIFICATION_ID);
    }

    static void playAlertSound(Context context) {
        MediaPlayer mediaPlayer = MediaPlayer.create(context, R.raw.notification_sound);
        mediaPlayer.start();
    }

    void turnOffDozeMode(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm.isIgnoringBatteryOptimizations(packageName))
                intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            else {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
            }
            startActivity(intent);
        }
    }

    void goToD2io() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(d2rURL));
        startActivity(intent);
    }

    void btnFAQPressed() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(faqURL));
        startActivity(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "D2RCloneTracker";
            String description = "Shows alert if progress increases for any region";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void createErrorChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "D2RCloneTrackerErrors";
            String description = "Shows alert if the app experiences an error.";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(ERROR_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}