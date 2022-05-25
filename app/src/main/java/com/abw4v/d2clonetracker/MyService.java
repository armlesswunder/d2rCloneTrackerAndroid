package com.abw4v.d2clonetracker;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.format.DateFormat;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.abw4v.d2clonetracker.MainActivity.*;

public class MyService extends Service{

    static final String TAG = "d2rCloneTrackerService";

    public static final String ERROR_MSG_NETWORK_INITIAL = "Network Failure: \n\nMax retries (5) exceeded, please try again later. \n\nThere may be an issue with your connection, and/or diablo2.io server.";
    public static final String ERROR_MSG_NETWORK = "Network Failure: \n\nMax retries (5) exceeded, will try again in 3 minutes. \n\nThere may be an issue with your connection, and/or diablo2.io server.";

    public static final String CHANNEL_ID ="d2rCloneTrackerProgress";
    public static final String ERROR_CHANNEL_ID ="d2rCloneTrackerError";

    static final int NOTIFICATION_ID = 9527;

    final static long ERROR_MAJOR_OFFSET = 3*60000; //retry after 3 minutes if major error
    final static long ERROR_MINOR_OFFSET = 10000; //retry after 10 seconds if minor error

    public static long startAt;

    static PowerManager.WakeLock wl_cpu, wl;

    static AlarmManager alarmManager;
    static PendingIntent pendingIntent;

    static List<Status> statusList = new ArrayList<>();

    static public int retries = 0;

    // Shared Vars
    public static int modeHardcore = 0;
    public static int modeLadder = 0;
    public static int modeRegion = 0;
    public static int modePerformance = 1;

    public static final int HARDCORE = 1;
    public static final int SOFTCORE = 2;

    public static final int LADDER = 1;
    public static final int NON_LADDER = 2;

    public static boolean showErrorNetwork = false;

    RequestQueue queue;

    static Handler handler = new Handler();
    public Runnable runnable = this::timerTask;

    public class MyServiceBinder extends Binder {
        public MyService getService() {
            return MyService.this;
        }
    }

    PowerManager.WakeLock wakeLock;
    final IBinder binder = new MyServiceBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NOTIFICATION_ID, getNotification(getApplicationContext(), System.currentTimeMillis()+getStartOffset()).build());
        initiateWakeLock();
        startAlert(getApplicationContext(), getStartOffset());
        //getData(getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(runnable);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @SuppressLint("WakelockTimeout")
    void initiateWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            // PARTIAL_WAKE_LOCK: Ensures that the CPU is running;
            // the screen and keyboard backlight will be allowed to go off.
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "D2RService:" + TAG);
            if (wakeLock != null) {
                wakeLock.acquire();
            }
        } else {
            Log.e(TAG, "Failed to get an instance of PowerManager");
        }
    }

    static void stop(Service service, Runnable runnable) {
        handler.removeCallbacks(runnable);
        service.stopForeground(true);
    }

    void timerTask() {
        getData(getApplicationContext());
    }

    public void getData(Context context) {
        if (queue == null) {
            queue = Volley.newRequestQueue(context);
        }

        StringRequest stringRequest = new StringRequest(Request.Method.GET, MainActivity.getURL(),
                response -> {
                    try {
                        JSONArray jArr = new JSONArray(response);
                        for (int i = 0; i < jArr.length(); i++) {
                            JSONObject json = jArr.getJSONObject(i);
                            Status newStatus = new Status(json);
                            Status oldStatus = getOldStatus(newStatus);
                            oldStatus.status = newStatus.status;
                            //oldStatus.prevStamp.add(0, stamp);
                            oldStatus.prevStatus.add(0, oldStatus.status);
                            setStatus(oldStatus);
                        }
                        showNotification(context, getStartOffset() + System.currentTimeMillis());
                        //appNotification(context);
                        startAlert(context, getStartOffset());
                        retries = 0;
                    } catch (Throwable e) {
                        e.printStackTrace();
                        startAlert(context, ERROR_MAJOR_OFFSET);
                        retries = 0;
                        if (showErrorNetwork) {
                            showError(context, e);
                        }
                    }
                }, error -> {
            if (retries < 5) {
                error.printStackTrace();
                startAlert(context, ERROR_MINOR_OFFSET);
                //showError(context, new Throwable("Network Failure, Trying again..."));
                retries++;
            } else {
                error.printStackTrace();
                startAlert(context, ERROR_MAJOR_OFFSET);
                //MainActivity.keepAwake(context);
                retries = 0;
                if (showErrorNetwork) {
                    showError(context, new Throwable(ERROR_MSG_NETWORK));
                }
            }
        }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String>  params = new HashMap<>();
                params.put("User-Agent", "ArmlessWunder");

                return params;
            }
        };;

        queue.add(stringRequest);
    }

    void setStatus(Status oldStatus) {
        if (oldStatus.prevStatus.size() > 5) {
            oldStatus.prevStatus.remove(5);
        }

        for (int i = 0; i < statusList.size(); i++) {
            Status status = statusList.get(i);
            if (status.id.equals(oldStatus.id)) {
                statusList.set(i, oldStatus);
                break;
            }
        }
    }

    static List<Status> getStatusList() {
        List<Status> tempList = new ArrayList<>(statusList);

        if (modeRegion != 0) {
            List<Status> tempList2 = new ArrayList<>();
            for (Status status: tempList) {
                if (modeRegion == status.region) tempList2.add(status);
            }
            tempList = new ArrayList<>(tempList2);
        }
        return tempList;
    }

    static String getMsg() throws Throwable {
        List<Status> tempList = getStatusList();
        tempList.sort((o1, o2) -> (o2.status - o1.status));

        StringBuilder temp = new StringBuilder();
        for (Status status: tempList) {
            temp.append(status.getMsg());
        }
        String out = temp.toString();
        if (out.isEmpty()) {
            throw new Throwable("Fatal error: App was closed by user or OS, tracker will stop now.");
        }
        return out;
    }

    Status getOldStatus(Status newStatus) {
        for (Status status: statusList) {
            if (status.id.equals(newStatus.id)) return status;
        }
        return new Status();
    }

    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        if (action != null && action.equals("stop")) {
            try {
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
                notificationManager.cancelAll();
                alarmManager.cancel(pendingIntent);
                wl_cpu.release();
                wl.release();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        } else {
            getData(context);
        }
    }

    void appNotification(Context context) {
        //String regularMsg = getMsg();
        //Toast.makeText(context, regularMsg, Toast.LENGTH_LONG).show();
        //MainActivity.playAlertSound(context);
    }

    static void showNotification(Context context, long startAt) {
        try {
            //NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            NotificationCompat.Builder builder = getNotification(context, startAt);
            //notificationManager.notify(0, builder.build());
            updateNotificationContent(context, builder.build());
        } catch(Throwable e) {
            e.printStackTrace();
        }
    }

    static void showError(Context context, Throwable e) {
        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            NotificationCompat.Builder builder = getNotification(context, e);
            notificationManager.notify(1, builder.build());
        } catch(Throwable e1) {
            e.printStackTrace();
        }
    }


    static NotificationCompat.Builder getNotification(Context context, Throwable e) {

        NotificationCompat.Builder notificationCompat = new NotificationCompat.Builder(context, ERROR_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_baseline_storm_24)
                .setContentTitle("ERROR")
                .setContentText(e.getMessage())
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(e.getMessage()))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        return notificationCompat;
    }

    static NotificationCompat.Builder getNotification(Context context, long startAt) {
        String out;
        try {
            out = getMsg() + "\nNext fetch: " + convertDate(startAt, context);
        } catch (Throwable e) {
            return getNotification(context, e);
        }

        NotificationCompat.Builder notificationCompat = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_baseline_storm_24)
                .setContentTitle("D2R Clone Progress")
                .setContentText(out)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(out))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setSilent(!updatedStatus())
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        return notificationCompat;
    }

    static int getHighestStatus() {
        int highestStatus = 0;
        for (Status status: getStatusList()) {
            if (status.status > highestStatus) highestStatus = status.status;
        }
        return highestStatus;
    }

    static boolean updatedStatus() {
        for (Status status: getStatusList()) {
            if (status.isUpdatedStatus()) return true;
        }
        return false;
    }

    public void startAlert(Context context, long startAt) {
        handler.postDelayed(runnable, startAt);
    }

    // lets do our part not spam the endpoint if not needed!
    public static long getStartOffset() {
        int maxStatus = 1;

        for (Status status: getStatusList()) {
            if (status.status > maxStatus) maxStatus = status.status;
        }

        if (maxStatus == 1) return 5*60000L/modePerformance; //every 5 minutes
        else if (maxStatus == 2) return 4*60000L/modePerformance; //every 4 minutes
        else if (maxStatus == 3) return 3*60000L/modePerformance; //every 3 minutes
        else if (maxStatus == 4) return 2*60000L/modePerformance; //every 2 minutes
        else if (maxStatus == 5) return 60000L/modePerformance; //every 1 minute
        else return 6L*60000L/modePerformance; //every 6 minutes
    }

    public static String convertDate(Long dateInMilliseconds, Context context) {
        if (!DateFormat.is24HourFormat(context)) {
            return DateFormat.format("h:mm:ss a", dateInMilliseconds).toString();
        } else {
            return DateFormat.format("HH:mm:ss", dateInMilliseconds).toString();
        }
    }

    static void updateNotificationContent(Context context, Notification notificationCompat) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        notificationManager.notify(NOTIFICATION_ID, notificationCompat);
    }

}