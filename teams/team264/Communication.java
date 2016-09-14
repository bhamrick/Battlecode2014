package team264;

import battlecode.common.Direction;
import battlecode.common.GameConstants;
import battlecode.common.RobotController;
import battlecode.common.*;
import java.util.*;

public class Communication {
    public static int attack_channel = 1;
    public static int defense_channel = 2;
    public static int rally_channel = 3;
    public static int pasture_channel = 4;
    public static int noise_tower_channel = 5;
    public static int noise_tower_construction_channel = 6;
    public static int kill_channel = 7;
    public static int emergency_pasture_channel = 8;
    public static int emergency_signal = 1712;

    public static void send_location(RobotController rc, int channel, MapLocation loc) {
        int data = 0xFFFF;
        if (loc != null) {
            data = (loc.y << 8) | loc.x;
        }
        try {
            rc.broadcast(channel, data);
        } catch (GameActionException e) {
            System.out.println("Exception in send_location");
        }
    }

    public static MapLocation read_location(RobotController rc, int channel) {
        try {
            int data = rc.readBroadcast(channel);
            if (data == 0xFFFF) {
                return null;
            }
            return new MapLocation(data & 0xFF, (data >> 8) & 0xFF);
        } catch (GameActionException e) {
            System.out.println("Exception in read_location");
            return null;
        }
    }

    public static void clear_channel(RobotController rc, int channel) {
        try {
            rc.broadcast(channel, 0xFFFF);
        } catch (GameActionException e) {
            System.out.println("Exception in clear_channel");
        }
    }
}
