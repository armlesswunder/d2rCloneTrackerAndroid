package com.abw4v.d2clonetracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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

        StringRequest stringRequest = new StringRequest(Request.Method.GET, "https://diablo2.io/dclone_api.php?hc=2&ladder=2",
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
                    if (retries < 31) {
                        error.printStackTrace();
                        getData(context);
                        showError(context, new Throwable("Network Failure, Trying again..."));
                        retries++;
                    } else {
                        error.printStackTrace();
                        showError(context, new Throwable("Network Failure, Max attempts (30) exceeded! there is probably an issue with d2.io backend or your connection."));
                    }
        });

        queue.add(stringRequest);
    }

    void setStatus(Status oldStatus) {
        for (int i = 0; i < statusList.size(); i++) {
            Status status = statusList.get(i);
            if (status.region == oldStatus.region) {
                statusList.set(i, oldStatus);
                break;
            }
        }
    }

    String getMsg() {
        StringBuilder temp = new StringBuilder();
        for (Status status: statusList) {
            temp.append(status.getMsg());
        }
        return temp.toString();
    }

    Status getOldStatus(Status newStatus) {
        for (Status status: statusList) {
            if (status.region == newStatus.region) return status;
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
                .setSmallIcon(R.drawable.ic_launcher_foreground)
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
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Update")
                .setContentText(getMsg() + "\nNext fetch: " + convertDate(startAt + getStartOffest()))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(getMsg() + "\nNext fetch: " + convertDate(startAt + getStartOffest())))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setSilent(!updatedStatus())
                .addAction(actionStop)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        return notificationCompat;
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