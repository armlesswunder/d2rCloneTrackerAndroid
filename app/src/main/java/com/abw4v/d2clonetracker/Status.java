package com.abw4v.d2clonetracker;

import static com.abw4v.d2clonetracker.MyService.threshold;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Status {
    String id = "00";
    int region = 0;
    int status = 0;
    int ladder = 0;
    int hardcore = 0;
    int rotw = 0;
    List<Integer> prevStatus = new ArrayList<>();
    List<Long> prevStamp = new ArrayList<>();

    public Status() { }

    public Status(String type, JSONObject json) throws Throwable {
        if (Objects.equals(type, "krNonLadder")) {
            status = json.getInt("status");
            region = 3;
            ladder = 0;
            hardcore = 0;
            rotw = 0;
        }
        if (Objects.equals(type, "krNonLadderHardcore")) {
            status = json.getInt("status");
            region = 3;
            ladder = 0;
            hardcore = 1;
            rotw = 0;
        }
        if (Objects.equals(type, "krLadder")) {
            status = json.getInt("status");
            region = 3;
            ladder = 1;
            hardcore = 0;
            rotw = 0;
        }
        if (Objects.equals(type, "krLadderHardcore")) {
            status = json.getInt("status");
            region = 3;
            ladder = 1;
            hardcore = 1;
            rotw = 0;
        }
        if (Objects.equals(type, "usNonLadder")) {
            status = json.getInt("status");
            region = 1;
            ladder = 0;
            hardcore = 0;
            rotw = 0;
        }
        if (Objects.equals(type, "usNonLadderHardcore")) {
            status = json.getInt("status");
            region = 1;
            ladder = 0;
            hardcore = 1;
            rotw = 0;
        }
        if (Objects.equals(type, "usLadder")) {
            status = json.getInt("status");
            region = 1;
            ladder = 1;
            hardcore = 0;
            rotw = 0;
        }
        if (Objects.equals(type, "usLadderHardcore")) {
            status = json.getInt("status");
            region = 1;
            ladder = 1;
            hardcore = 1;
            rotw = 0;
        }
        if (Objects.equals(type, "euNonLadder")) {
            status = json.getInt("status");
            region = 2;
            ladder = 0;
            hardcore = 0;
            rotw = 0;
        }
        if (Objects.equals(type, "euNonLadderHardcore")) {
            status = json.getInt("status");
            region = 2;
            ladder = 0;
            hardcore = 1;
            rotw = 0;
        }
        if (Objects.equals(type, "euLadder")) {
            status = json.getInt("status");
            region = 2;
            ladder = 1;
            hardcore = 0;
            rotw = 0;
        }
        if (Objects.equals(type, "euLadderHardcore")) {
            status = json.getInt("status");
            region = 2;
            ladder = 1;
            hardcore = 1;
            rotw = 0;
        }
        if (Objects.equals(type, "krNonLadderRotw")) {
            status = json.getInt("status");
            region = 3;
            ladder = 0;
            hardcore = 0;
            rotw = 1;
        }
        if (Objects.equals(type, "krNonLadderHardcoreRotw")) {
            status = json.getInt("status");
            region = 3;
            ladder = 0;
            hardcore = 1;
            rotw = 1;
        }
        if (Objects.equals(type, "krLadderRotw")) {
            status = json.getInt("status");
            region = 3;
            ladder = 1;
            hardcore = 0;
            rotw = 1;
        }
        if (Objects.equals(type, "krLadderHardcoreRotw")) {
            status = json.getInt("status");
            region = 3;
            ladder = 1;
            hardcore = 1;
            rotw = 1;
        }
        if (Objects.equals(type, "usNonLadderRotw")) {
            status = json.getInt("status");
            region = 1;
            ladder = 0;
            hardcore = 0;
            rotw = 1;
        }
        if (Objects.equals(type, "usNonLadderHardcoreRotw")) {
            status = json.getInt("status");
            region = 1;
            ladder = 0;
            hardcore = 1;
            rotw = 1;
        }
        if (Objects.equals(type, "usLadderRotw")) {
            status = json.getInt("status");
            region = 1;
            ladder = 1;
            hardcore = 0;
            rotw = 1;
        }
        if (Objects.equals(type, "usLadderHardcoreRotw")) {
            status = json.getInt("status");
            region = 1;
            ladder = 1;
            hardcore = 1;
            rotw = 1;
        }
        if (Objects.equals(type, "euNonLadderRotw")) {
            status = json.getInt("status");
            region = 2;
            ladder = 0;
            hardcore = 0;
            rotw = 1;
        }
        if (Objects.equals(type, "euNonLadderHardcoreRotw")) {
            status = json.getInt("status");
            region = 2;
            ladder = 0;
            hardcore = 1;
            rotw = 1;
        }
        if (Objects.equals(type, "euLadderRotw")) {
            status = json.getInt("status");
            region = 2;
            ladder = 1;
            hardcore = 0;
            rotw = 1;
        }
        if (Objects.equals(type, "euLadderHardcoreRotw")) {
            status = json.getInt("status");
            region = 2;
            ladder = 1;
            hardcore = 1;
            rotw = 1;
        }
        status += 1;
        id = "" + region + ladder + hardcore + rotw;
    }

    public String getMsg() {
        return getRegionDisplay() + ": " + getStatus() + "/6; ";
    }


    public String getOldMsg(int index) {
        return getRegionDisplay() + ": " + getOldStatus(index) + "/6; ";
    }

    int getStatus() {
        return (walkOccurred() ? 6 : status);
    }

    int getOldStatus(int index) {
        return prevStatus.get(index);
    }

    String getRegionDisplay() {
        if (region == 1) return "Americas" + getHardcore() + getLadder() + getRotw();
        else if (region == 2) return "Europe" + getHardcore() + getLadder() + getRotw();
        else return "Asia" + getHardcore() + getLadder() + getRotw();
    }

    String getHardcore() {
        if (hardcore == 1) return "\uD83D\uDC80";
        else return "";
    }

    String getRotw() {
        if (rotw == 1) return " RotW";
        else return "";
    }

    String getLadder() {
        if (ladder == 1) return "\uD83E\uDE9C";
        else return "";
    }

    boolean isUpdatedStatus() {
        if (prevStatus.size() >= 2) {
            // api doesn't return 6 status, instead we'll check for if the status was reset for an event
            if (status < prevStatus.get(1)) return true;
            return status > prevStatus.get(1) && status > threshold;
        } else return false;
    }

    boolean walkOccurred() {
        if (prevStatus.size() >= 2) {
            return status < prevStatus.get(1);
        } else return false;
    }
}
