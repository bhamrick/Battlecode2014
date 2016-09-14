package team264;

import battlecode.common.Direction;
import battlecode.common.GameConstants;
import battlecode.common.RobotController;
import battlecode.common.*;
import java.util.*;

public class NoiseTowerPlayer {
    static double current_angle;
    static double current_attack_distance;
    static final double max_attack_distance = 16.5;
    public static void run(RobotController rc) {
        current_angle = 0;
        current_attack_distance = max_attack_distance;

        Random rand = new Random(rc.getRobot().getID());
        int no_pasture_count = 50;

        while (true) {
            try {
                if(rc.isActive()) {
                    MapLocation target = addAngle(rc.getLocation(), current_angle, current_attack_distance); 
                    current_attack_distance -= 2.0;
                    if (current_attack_distance < 5) {
                        current_angle += 7*Math.PI/16.;
                        setupDistance(rc);
                    }
                    if(rc.canAttackSquare(target)) {
                        rc.attackSquare(target);
                    } else {
                        System.out.println("Cannot attack square " + target);
                    }
                }
                // Signal for defense if a robot is seen with probability 1/3
                Robot[] nearbyRobots = rc.senseNearbyGameObjects(rc.getRobot().getClass(), RobotType.NOISETOWER.sensorRadiusSquared, rc.getTeam().opponent());
                MapLocation defenseLoc = Communication.read_location(rc, Communication.defense_channel);
                if (nearbyRobots.length > 0 && (defenseLoc == null || rand.nextInt(3) < 1)) {
                    RobotInfo target = rc.senseRobotInfo(nearbyRobots[0]);
                    Communication.send_location(rc, Communication.defense_channel, target.location);
                } else if (nearbyRobots.length == 0) {
                    // Clear the defense channel with probability 1/5
                    if (rand.nextInt(5) < 1) {
                        Communication.clear_channel(rc, Communication.defense_channel);
                    }
                }
                boolean alone = true;
                MapLocation buildPastureLoc = Communication.read_location(rc, Communication.pasture_channel);
                if (buildPastureLoc != null && rc.getLocation().distanceSquaredTo(buildPastureLoc) < 5) {
                    alone = false;
                } else {
                    MapLocation[] ourPastures = rc.sensePastrLocations(rc.getTeam());
                    for(MapLocation p : ourPastures) {
                        if(rc.getLocation().distanceSquaredTo(p) < 5) {
                            alone = false;
                            break;
                        }
                    }
                }
                if (alone) {
                    no_pasture_count--;
                    if (no_pasture_count < 0) {
                        System.out.println("Goodbye cruel world!");
                        return;
                    }
                }
            } catch (Exception e) {
                System.out.println("Noise tower exception");
                e.printStackTrace();
            }
            rc.yield();
        }
    }

    static void setupDistance(RobotController rc) {
        MapLocation curLoc = rc.getLocation();
        current_attack_distance = max_attack_distance;
        for (double d = 1.0; d < max_attack_distance; d += 1.0) {
            MapLocation loc = addAngle(curLoc, current_angle, d);
            TerrainTile tile = rc.senseTerrainTile(loc);
            if(tile == TerrainTile.VOID || tile == TerrainTile.OFF_MAP) {
                current_attack_distance = d + 4.0;
                break;
            }
        }
        if (current_attack_distance > max_attack_distance) {
            current_attack_distance = max_attack_distance;
        }
    }

    static MapLocation addAngle(MapLocation center, double angle, double distance) {
        return new MapLocation(center.x + (int)(Math.cos(angle) * distance), center.y + (int)(Math.sin(angle) * distance));
    }
}
