package com.abw4v.d2clonetracker;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Status {
    int region = 0;
    int status = 0;
    List<Integer> prevStatus = new ArrayList<>();
    List<Long> prevStamp = new ArrayList<>();

    public Status() { }

    public Status(JSONObject json) throws Throwable {
        region = json.getInt("region");
        status = json.getInt("progress");
    }

    public String getMsg() {
        if (region == 3) return getRegionDisplay() + ": " + status + "/6";
        return getRegionDisplay() + ": " + status + "/6; ";
    }

    String getRegionDisplay() {
        if (region == 1) return "Americas";
        else if (region == 2) return "Europe";
        else return "Asia";
    }

    boolean isUpdatedStatus() {
        if (prevStatus.size() >= 2) {
            return status > prevStatus.get(1);
        } else return false;
    }
}
