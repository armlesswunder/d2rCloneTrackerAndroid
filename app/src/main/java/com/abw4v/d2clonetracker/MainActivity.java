package com.abw4v.d2clonetracker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.ads.AdView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.abw4v.d2clonetracker.MyService.*;

public class MainActivity extends AppCompatActivity {
    static int msgIndex = 0;
    // Constants
    static final String PREFS_HARDCORE = "hardcore";
    static final String PREFS_ROTW = "rotw";
    static final String PREFS_LADDER = "ladder";
    static final String PREFS_REGION = "region";
    static final String PREFS_THRESHOLD = "threshold";
    static final String PREFS_PERFORMANCE = "performanceMode";
    static final String PREFS_ERROR_NETWORK = "showNetworkErrors";

    BackgroundService service;

    static final String d2rURL = "https://www.d2emu.com/";
    static final String faqURL = "https://github.com/armlesswunder/d2rCloneTrackerAndroid#faq";

    // TODO: make sure this is right before any release
    static final boolean paid = false;

    final List<String> listRegion = new ArrayList<>(Arrays.asList("All", "Americas", "Europe", "Asia"));
    final List<String> listHardcore = new ArrayList<>(Arrays.asList("Both", "Hardcore", "Softcore"));
    final List<String> listRotw = new ArrayList<>(Arrays.asList("Both", "RotW", "Non-RotW"));
    final List<String> listLadder = new ArrayList<>(Arrays.asList("Both", "Ladder", "Non-Ladder"));
    final List<String> listGTS = new ArrayList<>(Arrays.asList("All", "1", "2", "3", "4", "5"));
    final List<String> listDataSource = new ArrayList<>(Arrays.asList("D2Emu Web Socket", "D2Emu REST", "D2IO REST"));

    // Views
    Spinner spinnerHardcore;
    Spinner spinnerRotw;
    Spinner spinnerLadder;
    Spinner spinnerRegion;
    Spinner spinnerGTS;
    Spinner spinnerDataSource;

    Switch switchErrorNetwork;
    Switch switchPerformance;

    ProgressBar progressBar;

    AdView adView;

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
        createWalkChannel();
        createErrorChannel();
        requestPermissions();
        recieverInit();

        adView = findViewById(R.id.adView);
        if (!paid) {
            adView.setVisibility(View.GONE);
            //MobileAds.initialize(this);
            //AdRequest adRequest = new AdRequest.Builder().build();
            //adView.loadAd(adRequest);
        } else {
            adView.setVisibility(View.GONE);
        }
    }

    void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && this.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (this.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            showAlert(this, "Warning!", "The app will not work for you because you rejected required permissions. Please enable required permission (go to app settings) and try again.");
        }
    }

    public void showAlert(Activity activity, String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton("OK", null);

        AlertDialog alert = builder.create();
        if (!(activity).isFinishing())
            alert.show();
    }

    void getDefaults() {
        SharedPreferences prefs = getSharedPreferences("default", Context.MODE_PRIVATE);
        modeHardcore = prefs.getInt(PREFS_HARDCORE, 0);
        modeRotw = prefs.getInt(PREFS_ROTW, 0);
        modeLadder = prefs.getInt(PREFS_LADDER, 0);
        modeRegion = prefs.getInt(PREFS_REGION, 0);
        threshold = prefs.getInt(PREFS_THRESHOLD, 0);
        modePerformance = prefs.getInt(PREFS_PERFORMANCE, 1);
        showErrorNetwork = prefs.getBoolean(PREFS_ERROR_NETWORK, false);
    }

    // start a service and then bind the app to it, so it can run after the app is closed
    private void startService(Context context) {
        Intent intent = new Intent(context, MyService.class);
        if (useForegroundService()) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        bindService(intent, myServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(updateUIReciver);
        if (!useForegroundService()) {
            showMessage(mContext, "FATAL ERROR: Application was destroyed by OS or user. Tracker is stopping...");
            if (myServiceBound.get()) {
                myServiceBound.set(false);
                myService.unbindService(myServiceConnection);
            }
        }
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (myServiceBound.get()) {
            if (useForegroundService()) {
                unbindService(myServiceConnection);
                myServiceBound.set(false);
            }
        }
    }

    void linkViewProperties() {
        progressBar = findViewById(R.id.progressBar);

        findViewById(R.id.btnStart).setOnClickListener(v -> startAlert());
        findViewById(R.id.btnStop).setOnClickListener(v -> stop());
        findViewById(R.id.btnFAQ).setOnClickListener(v -> btnFAQPressed());
        findViewById(R.id.btnDebug).setOnClickListener(v -> btnDebugPressed());
        findViewById(R.id.btnDoze).setOnClickListener(v -> turnOffDozeMode());
        findViewById(R.id.btnNotifications).setOnClickListener(v -> btnNotificationsPressed());
        findViewById(R.id.btnD2IO).setOnClickListener(v -> goToD2io());

        findViewById(R.id.btnDoze).setVisibility(!useForegroundService() ? View.VISIBLE : View.GONE);

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
            modePerformance = switchPerformance.isChecked() ? 2 : 1;
            setDefaults();
        });

        spinnerHardcore = findViewById(R.id.spinnerHardcore);
        spinnerRotw = findViewById(R.id.spinnerRotw);
        spinnerLadder = findViewById(R.id.spinnerLadder);
        spinnerRegion = findViewById(R.id.spinnerRegion);
        spinnerGTS = findViewById(R.id.spinnerGTS);
        spinnerDataSource = findViewById(R.id.spinnerDataSource);

        ArrayAdapter<String> hardcoreAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, listHardcore);
        ArrayAdapter<String> rotwAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, listRotw);
        ArrayAdapter<String> ladderAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, listLadder);
        ArrayAdapter<String> regionAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, listRegion);
        ArrayAdapter<String> gtsAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, listGTS);
        ArrayAdapter<String> dataSourceAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, listDataSource);

        spinnerHardcore.setAdapter(hardcoreAdapter);
        spinnerRotw.setAdapter(rotwAdapter);
        spinnerLadder.setAdapter(ladderAdapter);
        spinnerRegion.setAdapter(regionAdapter);
        spinnerGTS.setAdapter(gtsAdapter);
        spinnerDataSource.setAdapter(dataSourceAdapter);

        hardcoreAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        rotwAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        ladderAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        regionAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        gtsAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        dataSourceAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);

        spinnerHardcore.setSelection(modeHardcore);
        spinnerRotw.setSelection(modeRotw);
        spinnerLadder.setSelection(modeLadder);
        spinnerRegion.setSelection(modeRegion);
        spinnerGTS.setSelection(threshold);
        spinnerDataSource.setSelection(modeD2EmuWS);

        spinnerHardcore.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                modeHardcore = position;
                setDefaults();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        spinnerRotw.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                modeRotw = position;
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
            public void onNothingSelected(AdapterView<?> parent) { }});

        spinnerGTS.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    threshold = position;
                    setDefaults();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) { }
        });

        spinnerDataSource.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                serviceMode = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    void setDefaults() {
        getSharedPreferences("default", Context.MODE_PRIVATE).edit()
                .putInt(PREFS_HARDCORE, modeHardcore)
                .putInt(PREFS_ROTW, modeRotw)
                .putInt(PREFS_LADDER, modeLadder)
                .putInt(PREFS_REGION, modeRegion)
                .putInt(PREFS_THRESHOLD, threshold)
                .putInt(PREFS_PERFORMANCE, modePerformance)
                .putBoolean(PREFS_ERROR_NETWORK, showErrorNetwork)
                .apply();
    }

    public void startAlert() {
        statusList.clear();
        /// TODO change me to re enable service
        //if (!useForegroundService()) {
        //    if (service == null) {
        //        service = new BackgroundService();
        //    }
        //    service.startService(getApplicationContext());
        //} else {
            if (MyService.isRESTMode()) {
                progressBar.setVisibility(View.VISIBLE);
                getData();
            } else {
                MyService.startAt = System.currentTimeMillis();
                startService(MainActivity.this.getApplicationContext());
            }
        //}
    }

    public void getData() {

        MyService.startAt = System.currentTimeMillis();

        RequestQueue queue = Volley.newRequestQueue(this);
        String url = getURL();

        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
            response -> {
                try {
                    //Long stamp = System.currentTimeMillis();
                    JSONObject json = new JSONObject(response);

                    for (Iterator<String> it = json.keys(); it.hasNext(); ) {
                        String j = it.next();
                        if (!j.contains("Hardcore") && modeHardcore == 1) {
                            continue;
                        }
                        if (j.contains("Hardcore") && modeHardcore == 2) {
                            continue;
                        }
                        if (!j.contains("Rotw") && modeRotw == 1) {
                            continue;
                        }
                        if (j.contains("Rotw") && modeRotw == 2) {
                            continue;
                        }
                        if (j.contains("Non") && modeLadder == 1) {
                            continue;
                        }
                        if (!j.contains("Non") && modeLadder == 2) {
                            continue;
                        }
                            Status newStatus = new Status(j, json.getJSONObject(j));
                            //newStatus.prevStamp.add(0, stamp);
                            newStatus.prevStatus.add(0, newStatus.status);
                            statusList.add(newStatus);

                    }

                    //for (int i = 0; i < jArr.length(); i++) {
                    //    JSONObject json = jArr.getJSONObject(i);
                    //    Status newStatus = new Status(json);
                    //    //newStatus.prevStamp.add(0, stamp);
                    //    newStatus.prevStatus.add(0, newStatus.status);
                    //    statusList.add(newStatus);
                    //}
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
                params.put("User-Agent", "Android_Clone_Tracker");

                return params;
            }
        };

        queue.add(stringRequest);
    }

    public static String getURL() {
        if (serviceMode == modeD2EmuREST) {
            return "https://www.d2emu.com/dclone/dclone.json";
        } else {
            String urlbase = "https://diablo2.io/dclone_api.php";
            String hardcore = "";
            String rotw = "";
            String ladder = "";
            String query = "";
            String and = "&";

            if (modeHardcore != 0) {
                hardcore = "hc=" + modeHardcore;
                query = "?";
            }

            if (modeRotw != 0) {
                rotw = "rotw=" + modeRotw;
                query = "?";
            }

            if (modeLadder != 0) {
                ladder = "ladder=" + modeLadder;
                query = "?";
            }

            if (ladder.isEmpty() || hardcore.isEmpty()) {
                and = "";
            }

            /// TODO needs to be verified
            return urlbase+query+hardcore+and+ladder+and+rotw;
        }
    }

    BroadcastReceiver updateUIReciver;

    void recieverInit() {
        IntentFilter filter = new IntentFilter();

        filter.addAction("d2rTrackerAction");

        updateUIReciver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {

                TextView txt = findViewById(R.id.statusTxt);
                try {
                    txt.setText(getMsg());
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateUIReciver,filter, RECEIVER_EXPORTED);
        } else {
            registerReceiver(updateUIReciver,filter);
        }
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
        //if (useForegroundService()) {
            if (myServiceBound.get()) {
                unbindService(myServiceConnection);
                myServiceBound.set(false);
            }
            getApplicationContext().stopService(new Intent(getApplicationContext(), MyService.class));
            NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(MyService.NOTIFICATION_ID);
        //} else {
        //    if (service != null) {
        //        service.stopService();
        //        service = null;
        //    }
        //    if (ws.isOpen()) {
        //        ws.disconnect();
        //    }
        //}
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

    void btnDebugPressed() {
        try {
            msgIndex = msgIndex + 1;
            String s = getOldMsg(msgIndex);
            TextView txt = findViewById(R.id.statusTxt);

                txt.setText(s);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    void btnNotificationsPressed() {Intent intent = new Intent();
        String packageName = getPackageName();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + packageName));
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

    private void createWalkChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "D2RCloneTrackerWalks";
            String description = "Shows alert if walk occurs";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(WALK_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            Uri uri = new Uri.Builder()
                   .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                   .authority(getResources().getResourcePackageName(R.raw.notification_sound))
                   .appendPath(getResources().getResourceTypeName(R.raw.notification_sound))
                   .appendPath(getResources().getResourceEntryName(R.raw.notification_sound))
                   .build();
            channel.setSound(uri, channel.getAudioAttributes());
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