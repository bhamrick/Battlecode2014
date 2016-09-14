package team264;

import battlecode.common.Direction;
import battlecode.common.GameConstants;
import battlecode.common.RobotController;
import battlecode.common.*;
import java.util.*;

public class PasturePlayer {
    static int defense_channel = 2;
    public static void run(RobotController rc) {
        Random rand = new Random(rc.getRobot().getID());
        while (true) {
            try {
                // Signal for defense if a robot is seen with probability 0.5
                Robot[] nearbyRobots = rc.senseNearbyGameObjects(rc.getRobot().getClass(), RobotType.PASTR.sensorRadiusSquared, rc.getTeam().opponent());
                MapLocation defenseLoc = Communication.read_location(rc, Communication.defense_channel);
                if (nearbyRobots.length > 0 && (defenseLoc == null || rand.nextBoolean())) {
                    RobotInfo target = rc.senseRobotInfo(nearbyRobots[0]);
                    int data = (target.location.y << 8) | target.location.x;
                    rc.broadcast(defense_channel, data);
                } else if (nearbyRobots.length == 0) {
                    // Clear the defense channel with probability 1/3
                    if (rand.nextInt(5) < 1) {
                        rc.broadcast(defense_channel, 0xFFFF);
                    }
                }
            } catch (Exception e) {
                System.out.println("Pasture exception");
            }
            rc.yield();
        }
    }
}

