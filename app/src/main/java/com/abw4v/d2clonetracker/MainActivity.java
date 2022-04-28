package com.abw4v.d2clonetracker;

import androidx.appcompat.app.AppCompatActivity;
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
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final String CHANNEL_ID ="d2rCloneTrackerProgress";
    public static final String ERROR_CHANNEL_ID ="d2rCloneTrackerError";

    static final String d2rURL = "https://diablo2.io/";
    static final boolean debug = false;

    TextView lblHours, lblMinutes, lblSeconds;
    Button btnStart, btnCustomTime;
    EditText txtSeconds, txtMinutes;
    Spinner gameSpinner;

    static int time;
    static int earlySeconds = 30, earlyMinutes = 0;

    static long startAt;

    static PowerManager.WakeLock wl_cpu, wl;

    static Date currentDate;
    static Handler handler = new Handler();

    static AlarmManager alarmManager;
    static PendingIntent pendingIntent;

    static NotificationCompat.Action actionStop;

    static List<Status> statusList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        linkViewProperties();
        createNotificationChannel();
        createErrorChannel();
        setActions();
        startAt = System.currentTimeMillis();
    }

    void setActions() {
        Intent stopIntent = new Intent(this, MyReceiver.class);
        stopIntent.setAction("stop");
        stopIntent.putExtra("stop", "stop");
        PendingIntent stopPendingIntent =
                PendingIntent.getBroadcast(this.getApplicationContext(), 234, stopIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        actionStop = new NotificationCompat.Action(null, "stop", stopPendingIntent);
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

    public void getData() {

        RequestQueue queue = Volley.newRequestQueue(this);

        StringRequest stringRequest = new StringRequest(Request.Method.GET, "https://diablo2.io/dclone_api.php?hc=2&ladder=2",
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
                        pendingIntent = PendingIntent.getBroadcast(this.getApplicationContext(), 234, intent, PendingIntent.FLAG_CANCEL_CURRENT);
                        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

                        MainActivity.alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(30*1000, pendingIntent), pendingIntent);
                        keepAwake(this);
                        //playAlertSound(this);
                    } catch (Throwable e) {
                        e.printStackTrace();
                        showError(this, e);
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    showError(MainActivity.this, e);
                }
            }, error -> {
                getData();
                showError(MainActivity.this, new Throwable("Network Failure, this could be an issue with your device, or d2.io backend. Trying again..."));
                error.printStackTrace();
        });

        queue.add(stringRequest);
    }

    public void startAlert() {
        statusList.clear();
        getData();
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

    public void stop(View view) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (MainActivity.alarmManager != null) {
            MainActivity.alarmManager.cancel(pendingIntent);
        }
        notificationManager.cancelAll();
        try {
            MainActivity.wl_cpu.release();
            MainActivity.wl.release();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    static void playAlertSound(Context context) {
        MediaPlayer mediaPlayer = MediaPlayer.create(context, R.raw.notification_sound);
        mediaPlayer.start();
    }

    void turnOffDozeMode(Context context){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = context.getPackageName();
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm.isIgnoringBatteryOptimizations(packageName))
                intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            else {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
            }
            context.startActivity(intent);
        }
    }

    void linkViewProperties() {
        btnStart = findViewById(R.id.btnStart);
        findViewById(R.id.btnDisableDoze);

        btnStart.setOnClickListener(v -> startAlert());

        findViewById(R.id.btnStop).setOnClickListener(v -> stop(new View(this)));
        findViewById(R.id.btnDisableDoze).setOnClickListener(v -> turnOffDozeMode(this));
        findViewById(R.id.btnD2IO).setOnClickListener(v -> goToD2io());
    }

    void goToD2io() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(d2rURL));
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