package com.abw4v.d2clonetracker;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.abw4v.d2clonetracker.MyReceiver.alarmManager;
import static com.abw4v.d2clonetracker.MyReceiver.pendingIntent;
import static com.abw4v.d2clonetracker.MyReceiver.statusList;
import static com.abw4v.d2clonetracker.MyReceiver.wl;
import static com.abw4v.d2clonetracker.MyReceiver.wl_cpu;

public class MainActivity extends AppCompatActivity {

    // Constants
    public static final String CHANNEL_ID ="d2rCloneTrackerProgress";
    public static final String ERROR_CHANNEL_ID ="d2rCloneTrackerError";

    static final String PREFS_HARDCORE = "hardcore";
    static final String PREFS_LADDER = "ladder";
    static final String PREFS_DOZE = "dozeMode";

    static final String d2rURL = "https://diablo2.io/";
    static final String faqURL = "https://github.com/armlesswunder/d2rCloneTrackerAndroid#faq";
    static final boolean debug = false;

    public static final int BOTH = 0;

    public static final int HARDCORE = 1;
    public static final int SOFTCORE = 2;

    public static final int LADDER = 1;
    public static final int NON_LADDER = 2;

    // Shared Vars
    public static int modeHardcore = BOTH;
    public static int modeLadder = BOTH;

    // Vars
    List<String> listHardcore = new ArrayList<>(Arrays.asList("Both", "Hardcore", "Softcore"));
    List<String> listLadder = new ArrayList<>(Arrays.asList("Both", "Ladder", "Non-Ladder"));

    // Views
    Spinner spinnerHardcore;
    Spinner spinnerLadder;

    ProgressBar progressBar;

    boolean dozeMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getDefaults();
        linkViewProperties();
        createNotificationChannel();
        createErrorChannel();
        setActions();
    }

    void getDefaults() {
        SharedPreferences prefs = getSharedPreferences("default", Context.MODE_PRIVATE);
        modeHardcore = prefs.getInt(PREFS_HARDCORE, BOTH);
        modeLadder = prefs.getInt(PREFS_LADDER, BOTH);
        dozeMode = prefs.getBoolean(PREFS_DOZE, true);
    }

    @Override
    protected void onDestroy() {
        MyReceiver.appDestroyed = true;
        try {
            showError(getApplicationContext(), new Throwable("App closed (either implicitly or explicitly), tracker will stop now..."));
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
            notificationManager.cancelAll();
            alarmManager.cancel(pendingIntent);
            wl_cpu.release();
            wl.release();
        } catch (Throwable e) {
            e.printStackTrace();
        }

        super.onDestroy();
    }

    void linkViewProperties() {
        progressBar = findViewById(R.id.progressBar);

        findViewById(R.id.btnStart).setOnClickListener(v -> startAlert());
        findViewById(R.id.btnStop).setOnClickListener(v -> stop());
        findViewById(R.id.btnDisableDoze).setOnClickListener(v -> turnOffDozeMode());
        findViewById(R.id.btnFAQ).setOnClickListener(v -> btnFAQPressed());
        findViewById(R.id.btnD2IO).setOnClickListener(v -> goToD2io());

        spinnerHardcore = findViewById(R.id.spinnerHardcore);
        spinnerLadder = findViewById(R.id.spinnerLadder);

        ArrayAdapter<String> hardcoreAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, listHardcore);
        ArrayAdapter<String> ladderAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, listLadder);

        spinnerHardcore.setAdapter(hardcoreAdapter);
        spinnerLadder.setAdapter(ladderAdapter);

        hardcoreAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        ladderAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);

        spinnerHardcore.setSelection(modeHardcore);
        spinnerLadder.setSelection(modeLadder);

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
    }

    void setDefaults() {
        getSharedPreferences("default", Context.MODE_PRIVATE).edit().putInt(PREFS_HARDCORE, modeHardcore).putInt(PREFS_LADDER, modeLadder).apply();
    }

    public void startAlert() {

        if (dozeMode) {
            warnDoze();
        } else {
            progressBar.setVisibility(View.VISIBLE);
            MyReceiver.appDestroyed = false;
            statusList.clear();
            getData();
        }
    }

    void warnDoze() {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
        alertBuilder.setCancelable(false);
        alertBuilder.setTitle("WARNING");
        alertBuilder.setMessage("Please ensure doze mode is disabled to ensure proper functionality. Failure to do so will result in random errors and crashes. Please read the FAQ for more details");
        alertBuilder.setPositiveButton("OK", (a, b) -> {
            SharedPreferences prefs = getSharedPreferences("default", Context.MODE_PRIVATE);
            dozeMode = false;
            prefs.edit().putBoolean(PREFS_DOZE, false).apply();
        });
        alertBuilder.show();
    }

    public void getData() {

        MyReceiver.startAt = System.currentTimeMillis();
        RequestQueue queue = Volley.newRequestQueue(this);
        String url = getURL();

        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
            response -> {
                try {
                    Long stamp = System.currentTimeMillis();
                    JSONArray jArr = new JSONArray(response);
                    for (int i = 0; i < jArr.length(); i++) {
                        JSONObject json = jArr.getJSONObject(i);
                        Status newStatus = new Status(json);
                        newStatus.prevStamp.add(0, stamp);
                        newStatus.prevStatus.add(0, newStatus.region);
                        statusList.add(newStatus);
                    }
                    try {
                        Intent intent = new Intent(this, MyReceiver.class);
                        pendingIntent = PendingIntent.getBroadcast(this.getApplicationContext(), 234, intent, PendingIntent.FLAG_IMMUTABLE);
                        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

                        MyReceiver.alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(30*1000, pendingIntent), pendingIntent);
                        keepAwake(this);
                        runOnUiThread(() -> progressBar.setVisibility(View.GONE));
                        //playAlertSound(this);
                    } catch (Throwable e) {
                        e.printStackTrace();
                        runOnUiThread(() -> progressBar.setVisibility(View.GONE));
                        showError(this, e);
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    runOnUiThread(() -> progressBar.setVisibility(View.GONE));
                    showError(MainActivity.this, e);
                }
            }, error -> {
                //getData();
                runOnUiThread(() -> progressBar.setVisibility(View.GONE));
                showError(MainActivity.this, new Throwable("Network Failure, this could be an issue with your device, or d2.io backend. Please try again."));
                error.printStackTrace();
        });

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

    void setActions() {
        Intent stopIntent = new Intent(this, MyReceiver.class);
        stopIntent.setAction("stop");
        stopIntent.putExtra("stop", "stop");
        PendingIntent stopPendingIntent =
                PendingIntent.getBroadcast(this.getApplicationContext(), 234, stopIntent, PendingIntent.FLAG_IMMUTABLE);
        MyReceiver.actionStop = new NotificationCompat.Action(null, "stop", stopPendingIntent);
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


    NotificationCompat.Builder getNotification(Context context, Throwable e) {

        NotificationCompat.Builder notificationCompat = new NotificationCompat.Builder(context, ERROR_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("ERROR")
                .setContentText(e.getMessage())
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(e.getMessage()))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        return notificationCompat;
    }

    void stop() {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (MyReceiver.alarmManager != null) {
            MyReceiver.alarmManager.cancel(pendingIntent);
        }
        notificationManager.cancelAll();
        try {
            wl_cpu.release();
            wl.release();
        } catch (Throwable e) {
            e.printStackTrace();
        }
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