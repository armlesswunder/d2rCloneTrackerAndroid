package com.abw4v.d2clonetracker;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Status {
    String id = "00";
    int region = 0;
    int status = 0;
    int ladder = 0;
    int hardcore = 0;
    List<Integer> prevStatus = new ArrayList<>();
    List<Long> prevStamp = new ArrayList<>();

    public Status() { }

    public Status(JSONObject json) throws Throwable {
        region = json.getInt("region");
        status = json.getInt("progress");
        ladder = json.getInt("ladder");
        hardcore = json.getInt("hc");
        id = "" + region + ladder + hardcore;
    }

    public String getMsg() {
        return getRegionDisplay() + ": " + status + "/6; ";
    }

    String getRegionDisplay() {
        if (region == 1) return "Americas " + getHardcore() + "/" + getLadder();
        else if (region == 2) return "Europe " + getHardcore() + "/" + getLadder();
        else return "Asia " + getHardcore() + "/" + getLadder();
    }

    String getHardcore() {
        if (hardcore == 1) return "HC";
        else return "SC";
    }

    String getLadder() {
        if (ladder == 1) return "L";
        else return "NL";
    }

    boolean isUpdatedStatus() {
        if (prevStatus.size() >= 2) {
            return status > prevStatus.get(1);
        } else return false;
    }
}
