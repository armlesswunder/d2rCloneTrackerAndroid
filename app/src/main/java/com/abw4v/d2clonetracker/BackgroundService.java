package com.abw4v.d2clonetracker;

import static com.abw4v.d2clonetracker.CommonService.*;
import static com.abw4v.d2clonetracker.MyService.*;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import com.neovisionaries.ws.client.WebSocketState;

public class BackgroundService {

    public Runnable runnable = this::timerTask;

    public void startService(Context context) {

        mContext = context.getApplicationContext();
        initiateWakeLock();
        if (isRESTMode()) {
            //startAlert(getApplicationContext(), getStartOffset());
            //getData(getApplicationContext());
        } else {
            startWS();
            handler.postDelayed(runnable, 10*1000);
        }
    }

    public void stopService() {
        stopWakeLock();
        handler.removeCallbacks(runnable);
    }

    @SuppressLint("WakelockTimeout")
    void initiateWakeLock() {
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
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

    @SuppressLint("WakelockTimeout")
    void stopWakeLock() {
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            // PARTIAL_WAKE_LOCK: Ensures that the CPU is running;
            // the screen and keyboard backlight will be allowed to go off.
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "D2RService:" + TAG);
            if (wakeLock != null) {
                wakeLock.release();
            }
        } else {
            Log.e(TAG, "Failed to get an instance of PowerManager");
        }
    }



    static boolean isRESTMode() {
        return serviceMode == modeD2EmuREST || serviceMode == modeD2IOREST;
    }

    void timerTask() {
        if (isRESTMode()) {
            //getData(mContext.getApplicationContext());
        } else {
            WebSocketState state = ws.getState();
            if (state != WebSocketState.OPEN) {
                startWS();
                Log.e(TAG, "Socket was closed, retrying");
            } else {
                Log.i(TAG, "Socket is open");
            }
            handler.postDelayed(runnable, 10*1000);
        }
    }
}
