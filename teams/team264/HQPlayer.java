package team264;

import battlecode.common.Direction;
import battlecode.common.GameConstants;
import battlecode.common.RobotController;
import battlecode.common.*;
import java.util.*;

public class HQPlayer {

    // Map sizes are based off of the diagonal
    static double very_small_size = 35;
    static double small_size = 65;
    static double medium_size = 95;
    static double large_size = 125;

    static int[] very_small_thresholds = { 14 };
    static int[] small_thresholds = { 13 };
    static int[] medium_thresholds = { 12 };
    static int[] large_thresholds = { 10, 24 };
    static int[] very_large_thresholds = { 6, 10, 20 };

    static int very_small_skirmish_threshold = 6;
    static int small_skirmish_threshold = 4;
    static int medium_skirmish_threshold = 3;
    static int large_skirmish_threshold = 2;
    static int very_large_skirmish_threshold = 2;

    static boolean building_noise_tower;
    public static void run(RobotController rc) {
        Direction[] directions = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};

        Team opp = rc.getTeam().opponent();
        MapLocation nextPasture = null;
        MapLocation nextNoiseTower = null;
        MapLocation rallypoint = null;
        building_noise_tower = false;
        int kill_count = 0;
        int death_count = 0;
        int last_robot_count = 0;

        int[] pastr_thresholds;
        int skirmish_advantage_threshold = 2;

        Random rand = new Random(rc.getRobot().getID());
        double map_size = Math.sqrt(rc.getMapWidth()*rc.getMapWidth() + rc.getMapHeight()*rc.getMapHeight());
        boolean go_for_contain = false;
        // Sample points to get a sense of road/void density
        int void_count = 0;
        int road_count = 0;
        for (int i = 0; i < 100; i++) {
            int x = rand.nextInt(rc.getMapWidth());
            int y = rand.nextInt(rc.getMapHeight());
            MapLocation loc = new MapLocation(x,y);
            TerrainTile tile = rc.senseTerrainTile(loc);
            if (tile == TerrainTile.VOID) {
                void_count++;
            }
            if (tile == TerrainTile.ROAD) {
                road_count++;
            }
        }
        map_size *= (100.0 + void_count - road_count) / 100.0;

        if(rc.senseHQLocation().distanceSquaredTo(rc.senseEnemyHQLocation()) < 400) {
            go_for_contain = true;
            MapLocation loc = rc.senseHQLocation();
            MapLocation enemyHQ = rc.senseEnemyHQLocation();
            while (!loc.equals(enemyHQ)) {
                TerrainTile tile = rc.senseTerrainTile(loc);
                if(tile == TerrainTile.VOID || tile == TerrainTile.OFF_MAP) {
                    go_for_contain = false;
                }
                loc = loc.add(loc.directionTo(enemyHQ));
            }
        }

        if (map_size < very_small_size) {
            go_for_contain = true;
            pastr_thresholds = very_small_thresholds;
            skirmish_advantage_threshold = very_small_skirmish_threshold;
            System.out.println("Very small");
        } else if(map_size < small_size) {
            pastr_thresholds = small_thresholds;
            skirmish_advantage_threshold = small_skirmish_threshold;
            System.out.println("Small");
        } else if(map_size < medium_size) {
            pastr_thresholds = medium_thresholds;
            skirmish_advantage_threshold = medium_skirmish_threshold;
            System.out.println("Medium");
        } else if(map_size < large_size) {
            pastr_thresholds = large_thresholds;
            skirmish_advantage_threshold = large_skirmish_threshold;
            System.out.println("Large");
        } else {
            pastr_thresholds = very_large_thresholds;
            skirmish_advantage_threshold = very_large_skirmish_threshold;
            System.out.println("Very large");
        }

        // clear both channels
        try {
            Communication.clear_channel(rc, Communication.attack_channel);
            Communication.clear_channel(rc, Communication.defense_channel);
            Communication.clear_channel(rc, Communication.rally_channel);
            Communication.clear_channel(rc, Communication.pasture_channel);
            Communication.clear_channel(rc, Communication.noise_tower_channel);
            Communication.clear_channel(rc, Communication.noise_tower_construction_channel);
            Communication.clear_channel(rc, Communication.kill_channel);
            Communication.clear_channel(rc, Communication.emergency_pasture_channel);
            
            // Set up initial rally point
            MapLocation hqLocation = rc.senseHQLocation();
            MapLocation enemyHQLocation = rc.senseEnemyHQLocation();
            rallypoint = hqLocation.add(hqLocation.directionTo(enemyHQLocation), 2);
            int data = (rallypoint.y << 8) | rallypoint.x;
            
            rc.broadcast(3, data);
            rallypoint = null;
        } catch (GameActionException e) {
            System.out.println("Exception clearing channels");
        }

        while(true) {
            try {
                int new_robot_count = rc.senseRobotCount();
                if (new_robot_count < last_robot_count) {
                    death_count += last_robot_count - new_robot_count;
                }
                int data = rc.readBroadcast(Communication.kill_channel);
                if (data == 1) {
                    kill_count++;
                    Communication.clear_channel(rc, Communication.kill_channel);
                }
            } catch(GameActionException e) {
                System.out.println("Error updating kill count");
            }
            try {
                // With probability 0.1, clear the defense channel
                if (rand.nextInt(10) < 1) {
                    try {
                        rc.broadcast(2, 0xFFFF);
                    } catch (GameActionException e) {
                        System.out.println("Error clearing the defense channel");
                    }
                }
                if (rc.isActive()) {
                    Robot[] nearbyRobots = rc.senseNearbyGameObjects(rc.getRobot().getClass(), RobotType.HQ.attackRadiusMaxSquared, opp);
                    RobotInfo target = null;
                    for (Robot r : nearbyRobots) {
                        try {
                            RobotInfo info = rc.senseRobotInfo(r);
                            if (info.type != RobotType.HQ && (target == null || info.health < target.health)) {
                                target = info;
                            }
                        } catch (Exception e) {
                            System.out.println("Exception in HQ attacking");
                        }
                    }
                    if (target != null) {
                        try {
                            rc.attackSquare(target.location);
                        } catch (GameActionException e) {
                            System.out.println("Invalid HQ attack");
                        }
                    }
                }
                if (rc.isActive() && rc.senseRobotCount() < 25) {
                    for (Direction d : directions) {
                        if (rc.canMove(d)) {
                            try {
                                rc.spawn(d);
                                break;
                            } catch (GameActionException e) {
                                // Continue to try the next direction
                            }
                        }
                    }
                }
                
                MapLocation[] ourPastures = rc.sensePastrLocations(rc.getTeam());
                if (nextPasture != null) {
                    for (MapLocation p : ourPastures) {
                        if (p.equals(nextPasture)) {
                            nextPasture = null;
                            nextNoiseTower = null;
                            Communication.clear_channel(rc, Communication.pasture_channel);
                            Communication.clear_channel(rc, Communication.noise_tower_channel);
                            Communication.clear_channel(rc, Communication.noise_tower_construction_channel);
                            building_noise_tower = false;
                            break;
                        }
                    }
                }

                if (nextPasture == null) {
                    nextPasture = Pasturizer.choosePastureLocation(rc, rand, (Clock.getRoundNum() < 100 && pastr_thresholds[0] == very_large_thresholds[0]));
                    System.out.println("Chose pasture location " + nextPasture);
                    for (Direction d : directions) {
                        MapLocation trial = nextPasture.add(d);
                        TerrainTile tile = rc.senseTerrainTile(trial);
                        if(tile == TerrainTile.NORMAL || tile == TerrainTile.ROAD) {
                            nextNoiseTower = trial;
                            break;
                        }
                    }
                    Communication.send_location(rc, Communication.rally_channel, nextPasture);
                }
                if (go_for_contain) {
                    if (rc.senseRobotCount() < 10) {
                        Communication.send_location(rc, Communication.rally_channel, nextPasture);
                    } else {
                        // On very small maps, try to set up an HQ contain
                        Communication.send_location(rc, Communication.rally_channel, rc.senseEnemyHQLocation());
                    }
                }

                boolean isEnemyHQFarming = false;
                MapLocation[] enemyPastures = rc.sensePastrLocations(rc.getTeam().opponent());
                MapLocation enemyHQLocation = rc.senseEnemyHQLocation();
                for (MapLocation p : enemyPastures) {
                    if (p.distanceSquaredTo(enemyHQLocation) <= 10) {
                        isEnemyHQFarming = true;
                    }
                }
                // Build a pasture when we've got enough robots or if we've won skirmishes
                if ((ourPastures.length < pastr_thresholds.length && (rc.senseRobotCount() >= pastr_thresholds[ourPastures.length] || isEnemyHQFarming)) || (ourPastures.length == 0 && kill_count - death_count >= skirmish_advantage_threshold)) {
                    Communication.send_location(rc, Communication.rally_channel, nextPasture);
                    Communication.send_location(rc, Communication.noise_tower_channel, nextNoiseTower);
                    building_noise_tower = true;
                }

                if(building_noise_tower) {
                    try {
                        int construction_started_round = rc.readBroadcast(Communication.noise_tower_construction_channel);
                        if(Clock.getRoundNum() - construction_started_round >= 55) {
                            Communication.send_location(rc, Communication.pasture_channel, nextPasture);
                        }
                    } catch(GameActionException e) {
                        System.out.println("Exception deciding whether to build pasture");
                    }
                }
            } catch (Exception e) {
                System.out.println("HQ Exception");
            }
            if (rc.senseTeamMilkQuantity(rc.getTeam()) > 0.995 * GameConstants.WIN_QTY) {
                try {
                    rc.broadcast(Communication.emergency_pasture_channel, Communication.emergency_signal);
                } catch(GameActionException e) {
                    System.out.println("Error creating emergency signal");
                }
            }
            last_robot_count = rc.senseRobotCount();
            rc.yield();
        }
    }

    static class Pasturizer {
        static Direction[] directions = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};
        static public MapLocation choosePastureLocation(RobotController rc, Random rand, boolean limitDistance) {
            double[][] growths = rc.senseCowGrowth();
            MapLocation hqLocation = rc.senseHQLocation();
            MapLocation enemyHQLocation = rc.senseEnemyHQLocation();
            MapLocation[] ourPastures = rc.sensePastrLocations(rc.getTeam());
            MapLocation target = null;
            double best_total_growth = 0;
            int count = 0;
            for (int iter = 0; iter < 400; iter++) {
                if (rc.isActive() && rc.senseRobotCount() < 25) {
                    for (Direction d : directions) {
                        if (rc.canMove(d)) {
                            try {
                                rc.spawn(d);
                                break;
                            } catch (GameActionException e) {
                                // Continue to try the next direction
                            }
                        }
                    }
                }
                int x = rand.nextInt(growths.length);
                int y = rand.nextInt(growths[x].length);
                boolean ignore = false;
                MapLocation randLoc = new MapLocation(x,y);
                for(MapLocation p : ourPastures) {
                    if(p.distanceSquaredTo(randLoc) < 40) {
                        ignore = true;
                        break;
                    }
                }
                if(randLoc.distanceSquaredTo(enemyHQLocation) * 1.1 < randLoc.distanceSquaredTo(hqLocation)) {
                    ignore = true;
                }
                if(limitDistance && randLoc.distanceSquaredTo(hqLocation) > 800) {
                    ignore = true;
                }
                if(ignore) {
                    continue;
                }
                if (growths[x][y] > 0) {
                    if (target == null) {
                        target = new MapLocation(x,y);
                    } else {
                        double total_growth = 0;
                        for (Direction d : directions) {
                            MapLocation newLoc = new MapLocation(x,y).add(d);
                            if (newLoc.x >= 0 && newLoc.x < growths.length && newLoc.y >= 0 && newLoc.y < growths[0].length) {
                                total_growth += growths[newLoc.x][newLoc.y];
                            }
                        }
                        if (total_growth > best_total_growth + 1e-3) {
                            count = 0;
                            target = new MapLocation(x,y);
                            best_total_growth = total_growth;
                        }
                        MapLocation trial = new MapLocation(x,y);
                        if (total_growth > best_total_growth - 1e-6 && Math.sqrt(hqLocation.distanceSquaredTo(trial)) < Math.sqrt(hqLocation.distanceSquaredTo(target)) + 10) {
                            if (Math.sqrt(hqLocation.distanceSquaredTo(trial)) < Math.sqrt(hqLocation.distanceSquaredTo(target)) - 10 || Math.sqrt(enemyHQLocation.distanceSquaredTo(trial)) > Math.sqrt(enemyHQLocation.distanceSquaredTo(target)) + 10) {
                                count = 0;
                                target = new MapLocation(x,y);
                                best_total_growth = total_growth;
                            } else {
                                count += 1;
                                if (rand.nextInt(count) < 1) {
                                    target = new MapLocation(x,y);
                                    best_total_growth = total_growth;
                                }
                            }
                        }
                    }
                }
            }
            return target;
        }
    }
}
