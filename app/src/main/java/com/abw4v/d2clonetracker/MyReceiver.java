package com.abw4v.d2clonetracker;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
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
import static com.abw4v.d2clonetracker.MainActivity.actionStop;
import static com.abw4v.d2clonetracker.MainActivity.pendingIntent;
import static com.abw4v.d2clonetracker.MainActivity.startAt;
import static com.abw4v.d2clonetracker.MainActivity.statusList;

public class MyReceiver extends BroadcastReceiver {

    int retries = 0;

    public void getData(Context context) {

        RequestQueue queue = Volley.newRequestQueue(context);

        StringRequest stringRequest = new StringRequest(Request.Method.GET, MainActivity.getURL(),
                response -> {
                    try {
                        Long stamp = System.currentTimeMillis();
                        JSONArray jArr = new JSONArray(response);
                        for (int i = 0; i < jArr.length(); i++) {
                            JSONObject json = jArr.getJSONObject(i);
                            Status newStatus = new Status(json);
                            Status oldStatus = getOldStatus(newStatus);
                            oldStatus.status = newStatus.status;
                            oldStatus.prevStamp.add(0, stamp);
                            oldStatus.prevStatus.add(0, oldStatus.status);
                            setStatus(oldStatus);
                        }
                        showNotification(context);
                        //appNotification(context);
                        startAlert(context);
                        MainActivity.keepAwake(context);
                        retries = 0;
                    } catch (Throwable e) {
                        e.printStackTrace();
                        showError(context, e);
                    }
                }, error -> {
                    if (retries < 5) {
                        error.printStackTrace();
                        getData(context);
                        showError(context, new Throwable("Network Failure, Trying again..."));
                        retries++;
                    } else {
                        error.printStackTrace();
                        startAlert(context);
                        MainActivity.keepAwake(context);
                        retries = 0;
                        showError(context, new Throwable("Network Failure, Max retries (5) exceeded! There is may be an issue with your connection, battery optimization (doze mode), and/or diablo2.io server."));
                    }
        });

        queue.add(stringRequest);
    }

    void setStatus(Status oldStatus) {
        for (int i = 0; i < statusList.size(); i++) {
            Status status = statusList.get(i);
            if (status.id.equals(oldStatus.id)) {
                statusList.set(i, oldStatus);
                break;
            }
        }
    }

    String getMsg() {
        List<Status> tempList = new ArrayList<>(statusList);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            tempList.sort((o1, o2) -> (o2.status - o1.status));
        }
        StringBuilder temp = new StringBuilder();
        for (Status status: tempList) {
            temp.append(status.getMsg());
        }
        return temp.toString();
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
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            MainActivity.alarmManager.cancel(pendingIntent);
            notificationManager.cancelAll();
            try {
                MainActivity.wl_cpu.release();
                MainActivity.wl.release();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        } else {
            getData(context);
        }
    }

    void appNotification(Context context) {
        String regularMsg = getMsg();
        Toast.makeText(context, regularMsg, Toast.LENGTH_LONG).show();
        //MainActivity.playAlertSound(context);
    }

    void showNotification(Context context) {
        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            NotificationCompat.Builder builder = getNotification(context);
            notificationManager.notify(0, builder.build());
        } catch(Throwable e) {
            e.printStackTrace();
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
                .setSmallIcon(R.drawable.ic_baseline_storm_24)
                .setContentTitle("ERROR")
                .setContentText(e.getMessage())
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(e.getMessage()))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setSilent(retries < 25)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        return notificationCompat;
    }

    NotificationCompat.Builder getNotification(Context context) {

        NotificationCompat.Builder notificationCompat = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_baseline_storm_24)
                .setContentTitle("D2R Clone Progress")
                .setContentText(getMsg() + "\nNext fetch: " + convertDate(startAt + getStartOffest()))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(getMsg() + "\nNext fetch: " + convertDate(startAt + getStartOffest())))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setSilent(!updatedStatus())
                .addAction(actionStop)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        int highestStatus = getHighestStatus();
        if (highestStatus == 3) {
            //flash blue
            notificationCompat
                .setDefaults(Notification.DEFAULT_SOUND | Notification.FLAG_SHOW_LIGHTS)
                .setLights(0xff0000ff, 500, 500);
        } else if (highestStatus == 4) {
            //flash green
            notificationCompat
                    .setDefaults(Notification.DEFAULT_VIBRATE | Notification.DEFAULT_SOUND | Notification.FLAG_SHOW_LIGHTS)
                    .setLights(0xff00ff00, 500, 300);
        } else if (highestStatus == 5) {
            //flash orange
            notificationCompat
                    .setDefaults(Notification.DEFAULT_VIBRATE | Notification.DEFAULT_SOUND | Notification.FLAG_SHOW_LIGHTS)
                    .setLights(0xffFFA500, 100, 100);
        } else if (highestStatus == 6) {
            //flash red
            notificationCompat
                    .setDefaults(Notification.FLAG_SHOW_LIGHTS)
                    .setLights(0xffff0000, 1000, 1000);
        }
        return notificationCompat;
    }

    int getHighestStatus() {
        int highestStatus = 0;
        for (Status status: statusList) {
            if (status.status > highestStatus) highestStatus = status.status;
        }
        return highestStatus;

    }

    boolean updatedStatus() {
        for (Status status: statusList) {
            if (status.isUpdatedStatus()) return true;
        }
        return false;
    }

    public void startAlert(Context context) {
        Intent intent = new Intent(context, MyReceiver.class);
        pendingIntent = PendingIntent.getBroadcast(context.getApplicationContext(), 234, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        MainActivity.alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        startAt = getStartOffest() + System.currentTimeMillis();
        if (startAt > System.currentTimeMillis()) {
            MainActivity.alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(startAt, pendingIntent), pendingIntent);
        } else {
            Log.d("StartAlert", ": system time: "+System.currentTimeMillis()+" lower than start time: "+startAt);
        }
        //MainActivity.alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startAt - (earlySeconds * 1000) - (earlyMinutes * 60000), MainActivity.pendingIntent);
    }

    // lets do our part not spam the endpoint if not needed!
    long getStartOffest() {
        int maxStatus = 1;

        for (Status status: statusList) {
            if (status.status > maxStatus) maxStatus = status.status;
        }

        if (maxStatus == 1) return 2L*60000L; //low alert
        else if (maxStatus == 2) return 3L*30000L;
        else if (maxStatus == 3) return 60000L;
        else if (maxStatus == 4) return 30000L;
        else if (maxStatus == 5) return 30000L; //high alert
        else return 3L*60000L; //reset, very low alert
    }

    public static String convertDate(Long dateInMilliseconds) {
        return DateFormat.format("hh:mm:ss", dateInMilliseconds).toString();
    }
}