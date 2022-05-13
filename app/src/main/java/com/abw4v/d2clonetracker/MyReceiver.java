package com.abw4v.d2clonetracker;

import android.app.AlarmManager;
import android.app.Application;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.PowerManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static android.content.Context.ALARM_SERVICE;
import static com.abw4v.d2clonetracker.MainActivity.CHANNEL_ID;
import static com.abw4v.d2clonetracker.MainActivity.ERROR_CHANNEL_ID;

public class MyReceiver extends BroadcastReceiver {
    final static long ERROR_MAJOR_OFFSET = 3*60000; //retry after 3 minutes if major error
    final static long ERROR_MINOR_OFFSET = 10000; //retry after 10 seconds if minor error

    static boolean appDestroyed = false;

    public static long startAt;

    static PowerManager.WakeLock wl_cpu, wl;

    static AlarmManager alarmManager;
    static PendingIntent pendingIntent;

    static NotificationCompat.Action actionStop;

    static List<Status> statusList = new ArrayList<>();

    static public int retries = 0;

    RequestQueue queue;

    public void getData(Context context) {
        if (appDestroyed) {
            try {
                //NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context.getApplicationContext());
                //notificationManager.cancelAll();
                alarmManager.cancel(pendingIntent);
                wl_cpu.release();
                wl.release();
            } catch (Throwable e) {
                e.printStackTrace();
            }
            return;
        }

        // TODO see if this works
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
                        startAlert(context, getStartOffset() + System.currentTimeMillis());
                        MainActivity.keepAwake(context);
                        retries = 0;
                    } catch (Throwable e) {
                        e.printStackTrace();
                        startAlert(context, ERROR_MAJOR_OFFSET + System.currentTimeMillis());
                        MainActivity.keepAwake(context);
                        retries = 0;
                        showError(context, e);
                    }
                }, error -> {
                    if (retries < 5) {
                        error.printStackTrace();
                        startAlert(context, ERROR_MINOR_OFFSET + System.currentTimeMillis());
                        //showError(context, new Throwable("Network Failure, Trying again..."));
                        retries++;
                    } else {
                        error.printStackTrace();
                        startAlert(context, ERROR_MAJOR_OFFSET + System.currentTimeMillis());
                        MainActivity.keepAwake(context);
                        retries = 0;
                        if (MainActivity.showErrorNetwork) {
                            showError(context, new Throwable("Network Failure: \n\nMax retries (5) exceeded, will try again in 3 minutes \n\nThere may be an issue with your connection, battery optimization (doze mode), and/or diablo2.io server."));
                        }
                    }
        });

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

        if (MainActivity.modeRegion != 0) {
            List<Status> tempList2 = new ArrayList<>();
            for (Status status: tempList) {
                if (MainActivity.modeRegion == status.region) tempList2.add(status);
            }
            tempList = new ArrayList<>(tempList2);
        }
        return tempList;
    }

    static String getMsg() throws Throwable {
        List<Status> tempList = getStatusList();

        if (tempList.size() > 1 && Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            tempList.sort((o1, o2) -> (o2.status - o1.status));
        }
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


    @Override
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
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            NotificationCompat.Builder builder = getNotification(context, startAt);
            notificationManager.notify(0, builder.build());
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
                .setSilent(retries == 5 || appDestroyed)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        return notificationCompat;
    }

    static NotificationCompat.Builder getNotification(Context context, long startAt) {
        String out;
        try {
            out = getMsg() + "\nNext fetch: " + convertDate(startAt, context);
        } catch (Throwable e) {
            appDestroyed = true;
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
                .addAction(actionStop)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        int highestStatus = getHighestStatus();
        if (updatedStatus()) {
            if (highestStatus == 5) {
                //flash orange
                notificationCompat
                        .setDefaults(Notification.DEFAULT_VIBRATE | Notification.FLAG_SHOW_LIGHTS)
                        .setVibrate(new long[] { 1000, 1000 })
                        .setLights(Color.YELLOW, 1000, 1000);
            } else if (highestStatus == 6) {
                //flash red
                notificationCompat
                        .setDefaults(Notification.FLAG_SHOW_LIGHTS)
                        .setVibrate(new long[] { 1000, 1000, 1000 })
                        .setLights(Color.RED, 1000, 1000);
            }
        }
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
        Intent intent = new Intent(context, MyReceiver.class);
        pendingIntent = PendingIntent.getBroadcast(context.getApplicationContext(), 234, intent, PendingIntent.FLAG_IMMUTABLE);
        alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        if (startAt > System.currentTimeMillis()) {
            alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(startAt, pendingIntent), pendingIntent);
        } else {
            Log.d("StartAlert", ": system time: "+System.currentTimeMillis()+" lower than start time: "+startAt);
        }
        //MainActivity.alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startAt - (earlySeconds * 1000) - (earlyMinutes * 60000), MainActivity.pendingIntent);
    }

    // lets do our part not spam the endpoint if not needed!
    public static long getStartOffset() {
        int maxStatus = 1;

        for (Status status: getStatusList()) {
            if (status.status > maxStatus) maxStatus = status.status;
        }

        if (maxStatus == 1) return 5*60000L; //every 5 minutes
        else if (maxStatus == 2) return 4*60000L; //every 4 minutes
        else if (maxStatus == 3) return 3*60000L; //every 3 minutes
        else if (maxStatus == 4) return 2*60000L; //every 2 minutes
        else if (maxStatus == 5) return 60000L; //every 1 minute
        else return 6L*60000L; //every 6 minutes
    }

    public static String convertDate(Long dateInMilliseconds, Context context) {
        if (!DateFormat.is24HourFormat(context)) {
            return DateFormat.format("h:mm:ss a", dateInMilliseconds).toString();
        } else {
            return DateFormat.format("HH:mm:ss", dateInMilliseconds).toString();
        }
    }
}