package com.abw4v.d2clonetracker;

import static com.abw4v.d2clonetracker.MyService.getOldStatus;
import static com.abw4v.d2clonetracker.MyService.getStartOffset;
import static com.abw4v.d2clonetracker.MyService.mContext;
import static com.abw4v.d2clonetracker.MyService.modeHardcore;
import static com.abw4v.d2clonetracker.MyService.modeLadder;
import static com.abw4v.d2clonetracker.MyService.modeRotw;
import static com.abw4v.d2clonetracker.MyService.setStatus;
import static com.abw4v.d2clonetracker.MyService.showError;
import static com.abw4v.d2clonetracker.MyService.showErrorNetwork;
import static com.abw4v.d2clonetracker.MyService.showNotification;
import static com.abw4v.d2clonetracker.MyService.statusList;

import android.util.Log;


import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketExtension;
import com.neovisionaries.ws.client.WebSocketFactory;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Iterator;

import java.io.IOException;
import java.util.Objects;


public class D2EmuWsClient
{
    static URI uri;
    static String username = "armlesswunder";
    static String password = "21432cb95ab5a5e1";
    private static final String SERVER = "wss://d2emu.com/ws";
    private static final int TIMEOUT = 5000;

    static WebSocket connect() throws IOException, WebSocketException
    {

        return new WebSocketFactory()
                .setConnectionTimeout(TIMEOUT)
                .createSocket(SERVER)
                .addListener(new WebSocketAdapter() {
                    // A text message arrived from the server.
                    public void onTextMessage(WebSocket websocket, String message) {
                        Log.i("WS Msg", message);
                        //Long stamp = System.currentTimeMillis();

                        try {
                            JSONObject json = new JSONObject(message);

                            json = json.getJSONObject("dclone");
                            for (Iterator<String> it = json.keys(); it.hasNext(); ) {
                                String j = it.next();

                                ///  I have no idea which region is cn or if the data is valid
                                if (j.startsWith("cn")) {
                                    continue;
                                }

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
                                Status oldStatus = getOldStatus(newStatus);
                                if (Objects.equals(oldStatus.id, "00")) {
                                    newStatus.prevStatus.add(0, newStatus.status);
                                    statusList.add(newStatus);
                                } else {
                                    oldStatus.status = newStatus.status;
                                    oldStatus.prevStatus.add(0, oldStatus.status);
                                    setStatus(oldStatus);
                                }
                            }
                            showNotification(mContext, getStartOffset() + System.currentTimeMillis());
                        } catch (Throwable e) {
                            if (showErrorNetwork) {
                                showError(mContext, e);
                            }
                        }
                    }
                })
                .addExtension(WebSocketExtension.PERMESSAGE_DEFLATE)
                .setUserInfo(username, password)
                .connect();
    }


    /**
     * Wrap the standard input with BufferedReader.
     */
    private static BufferedReader getInput() throws IOException
    {
        return new BufferedReader(new InputStreamReader(System.in));
    }
}