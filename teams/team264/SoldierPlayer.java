package team264;

import battlecode.common.Direction;
import battlecode.common.GameConstants;
import battlecode.common.RobotController;
import battlecode.common.*;
import java.util.*;

public class SoldierPlayer {
    static MapLocation enemyHQLocation, hqLocation;
    static boolean defender = true;
    static boolean isVeryLarge = false;
    static final double very_large_size = 113;
    static boolean retreating = false;
    static boolean defending = false;
    public static void run(RobotController rc) {
        Random rand = new Random(rc.getRobot().getID());
        defender = (rc.getRobot().getID() / 7) % 2 == 0;
        Direction[] directions = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};

        hqLocation = rc.senseHQLocation();
        enemyHQLocation = rc.senseEnemyHQLocation();
        Pathfinder pather = new Pathfinder(rc);
        MapLocation rallypoint = hqLocation.add(hqLocation.directionTo(enemyHQLocation), -2);
        MapLocation defendingPasture = null;

        isVeryLarge = Math.sqrt(rc.getMapWidth() * rc.getMapWidth() + rc.getMapHeight() * rc.getMapHeight()) >= very_large_size;

        try {
            MapLocation loc = Communication.read_location(rc, Communication.rally_channel);
            if (loc != null) {
                rallypoint = loc;
            }
        } catch(Exception e) {
            System.out.println("Exception reading rallypoint");
        }
        pather.setWaypoint(rallypoint);
        // pather.exact = false;
        pather.exact = true;
        Attacker attacker = new Attacker(rc, rand);
        Team opp = rc.getTeam().opponent();
        boolean force_sneak = false;

        while (true) {
            try {
                if (retreating && rc.getHealth() > 40) {
                    retreating = false;
                    pather.setWaypoint(rallypoint);
                }
                if (rc.isActive()) {
                    force_sneak = false;
                    try {
                        MapLocation rally_loc = Communication.read_location(rc, Communication.rally_channel);
                        if (rally_loc != null && pather.next_waypoint.equals(rally_loc) && rc.getLocation().distanceSquaredTo(rally_loc) < 50) {
                            force_sneak = true;
                        }
                        MapLocation noise_tower_loc = Communication.read_location(rc, Communication.noise_tower_channel);
                        if (noise_tower_loc != null && pather.next_waypoint.distanceSquaredTo(noise_tower_loc) < 5) {
                            pather.exact = true;
                            if (rc.getLocation().distanceSquaredTo(noise_tower_loc) < 50) {
                                force_sneak = true;
                            }
                        }
                        if (rc.getLocation().equals(noise_tower_loc)) {
                            rc.broadcast(Communication.noise_tower_construction_channel, Clock.getRoundNum());
                            rc.construct(RobotType.NOISETOWER);
                        }

                        MapLocation[] ourPastures = rc.sensePastrLocations(rc.getTeam());
                        for (MapLocation p : ourPastures) {
                            if (rc.getLocation().distanceSquaredTo(p) < 50) {
                                force_sneak = true;
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Exception determining whether to construct noise tower");
                    }
                    try {
                        MapLocation pastr_loc = Communication.read_location(rc, Communication.pasture_channel);
                        if (pastr_loc != null && pather.next_waypoint.equals(pastr_loc)) {
                            pather.exact = true;
                            if (rc.getLocation().distanceSquaredTo(pastr_loc) < 50) {
                                force_sneak = true;
                            }
                        }
                        if (rc.getLocation().equals(pastr_loc)) {
                            rc.construct(RobotType.PASTR);
                        }
                    } catch (Exception e) {
                        System.out.println("Exception determining whether to pasturize");
                    }
                    // Try to move away if outnumbered
                    if (doRetreat(rc)) {
                        rc.yield();
                        continue;
                    }
                    if (attacker.doAttack(rc)) {
                        rc.yield();
                        continue;
                    }
                    try {
                        int data = rc.readBroadcast(Communication.emergency_pasture_channel);
                        if (data == Communication.emergency_signal) {
                            Robot[] nearbyEnemies = rc.senseNearbyGameObjects(rc.getRobot().getClass(), rc.getType().sensorRadiusSquared, rc.getTeam().opponent());
                            if (nearbyEnemies.length == 0) {
                                MapLocation curLoc = rc.getLocation();
                                double[][] growths = rc.senseCowGrowth();
                                if (growths[curLoc.x][curLoc.y] > 0) {
                                    rc.construct(RobotType.PASTR);
                                }
                            }
                        }
                    } catch (GameActionException e) {
                        System.out.println("Exception reading emergency channel");
                    }
                    if (retreating) {
                        pather.setWaypoint(hqLocation);
                    }
                    Direction toMove = pather.nextDirection(rc);
                    if (toMove != Direction.NONE) {
                        try {
                            if(force_sneak) {
                                customSneak(rc, toMove);
                            } else {
                                customMove(rc, toMove);
                            }
                        } catch (GameActionException e) {
                            System.out.println("Exception from pathing");
                        }
                    } else {
                        // With some probability, at high health move randomly even if not requested in order to clear pasture locations.
                        if (rand.nextInt(6) < 1 && rc.getHealth() > 60 && !pather.next_waypoint.equals(enemyHQLocation)) {
                            Direction d = directions[rand.nextInt(directions.length)];
                            if (rc.isActive() && customCanMove(rc, d)) {
                                try {
                                    customSneak(rc, d);
                                } catch (GameActionException e) {
                                    e.printStackTrace();
                                    System.out.println("Exception during random move");
                                }
                            }
                        }
                    }
                } else if(rc.getActionDelay() < 2.0) {
                    MapLocation[] ourPastures = rc.sensePastrLocations(rc.getTeam());
                    if (ourPastures.length == 0) {
                        MapLocation loc = Communication.read_location(rc, Communication.rally_channel);
                        if (loc != null) {
                            rallypoint = loc;
                        }
                    } else {
                        boolean okayRallyPoint = false;
                        for (MapLocation p : ourPastures) {
                            if (rallypoint.equals(p)) {
                                okayRallyPoint = true;
                            }
                        }
                        if (!okayRallyPoint) {
                            rallypoint = ourPastures[rand.nextInt(ourPastures.length)];
                        }
                    }
                    if(!defender && (Communication.read_location(rc, Communication.noise_tower_channel) != null || Communication.read_location(rc, Communication.pasture_channel) != null)) {
                        MapLocation channelLoc = Communication.read_location(rc, Communication.rally_channel);
                        if (channelLoc != null) {
                            rallypoint = channelLoc;
                        }
                    }
                    // Do micro
                    boolean micro_happened = false;
                    try {
                        // Check attack channel for the position of an enemy robot
                        MapLocation curLoc = rc.getLocation();
                        MapLocation target = Communication.read_location(rc, Communication.attack_channel);
                        if (rc.getHealth() > 50 && target != null && curLoc.distanceSquaredTo(target) < 100) {
                            pather.setWaypoint(target);
                            pather.exact = true;
                            micro_happened = true;
                        }

                        // With probability 0.05, clear the attack channel.
                        if (rand.nextInt(20) < 1) {
                            Communication.clear_channel(rc, Communication.attack_channel);
                        }

                        // Check the defense channel
                        target = Communication.read_location(rc, Communication.defense_channel);
                        if (rc.getHealth() > 35 && target != null && rc.getLocation().distanceSquaredTo(target) < 150) {
                            pather.setWaypoint(target);
                            pather.exact = true;
                            micro_happened = true;
                            defending = true;
                        } else {
                            defending = false;
                        }

                        if (rc.getHealth() > 50) {
                            // Check for a nearby robot
                            Robot[] nearbyEnemies = rc.senseNearbyGameObjects(rc.getRobot().getClass(), rc.getType().sensorRadiusSquared, rc.getTeam().opponent());
                            double min_health = 50000;
                            for (Robot r : nearbyEnemies) {
                                RobotInfo info = rc.senseRobotInfo(r);
                                if (pather.unobstructed(curLoc, info.location)) {
                                    if ((target == null || info.health < min_health) && info.location.distanceSquaredTo(enemyHQLocation) > 10) {
                                        min_health = info.health;
                                        target = info.location;
                                    }
                                }
                            }
                            if (target != null) {
                                pather.setWaypoint(target);
                                pather.exact = true;
                                micro_happened = true;
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Exception in micro");
                    }
                    // If no micro happens target an enemy pasture.
                    if (!micro_happened) {
                        MapLocation[] enemyPastures = rc.sensePastrLocations(opp);
                        if (enemyPastures.length > 0 && (ourPastures.length > 0 || enemyPastures.length > 1 || !isVeryLarge)) {
                            MapLocation currentWaypoint = pather.next_waypoint;
                            boolean waypointOkay = false;
                            for (MapLocation loc : enemyPastures) {
                                if (loc.equals(currentWaypoint)) {
                                    waypointOkay = true;
                                    break;
                                }
                            }
                            if (!waypointOkay) {
                                int index = rand.nextInt(enemyPastures.length);
                                // If the pasture is very close to the enemy HQ, go for something else
                                if (enemyPastures[index].distanceSquaredTo(enemyHQLocation) <= 10) {
                                    // Do a linear search for something else
                                    index = -1;
                                    for(int i = 0; i < enemyPastures.length; i++) {
                                        if(enemyPastures[i].distanceSquaredTo(enemyHQLocation) > 10) {
                                            index = i;
                                            break;
                                        }
                                    }
                                }
                                if (index >= 0) {
                                    pather.setWaypoint(enemyPastures[index]);
                                    pather.exact = true;
                                }
                            }
                        } else {
                            pather.setWaypoint(rallypoint);
                            // pather.exact = false;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            rc.yield();
        }
    }

    static boolean doRetreat(RobotController rc) {
        Robot[] nearbyAllies = rc.senseNearbyGameObjects(rc.getRobot().getClass(), rc.getType().sensorRadiusSquared, rc.getTeam());
        Robot[] nearbyEnemies = rc.senseNearbyGameObjects(rc.getRobot().getClass(), rc.getType().sensorRadiusSquared, rc.getTeam().opponent());
        int dangerousEnemyCount = 0;
        MapLocation curLoc = rc.getLocation();
        int tdx = 0;
        int tdy = 0;
        for(Robot r : nearbyEnemies) {
            try {
                RobotInfo info = rc.senseRobotInfo(r);
                if (info.type == RobotType.SOLDIER && rc.getLocation().distanceSquaredTo(info.location) <= RobotType.SOLDIER.attackRadiusMaxSquared) {
                    dangerousEnemyCount++;
                    tdx += info.location.x - curLoc.x;
                    tdy += info.location.y - curLoc.y;
                }
                // Don't retreat if you can snipe someone
                if (info.health <= RobotType.SOLDIER.attackPower && curLoc.distanceSquaredTo(info.location) <= RobotType.SOLDIER.attackRadiusMaxSquared) {
                    return false;
                }
            } catch (GameActionException e) {
                System.out.println("Exception in doRetreat");
            }
        }
        if (dangerousEnemyCount == 0) {
            return false;
        }
        if (nearbyAllies.length == 0) {
            // Micro for lone robots
            if (dangerousEnemyCount > 1) {
                // Run away from their center of mass
                if (rc.getHealth() < 50) {
                    retreating = true;
                }
                MapLocation runAwayFromThis = new MapLocation(curLoc.x + tdx, curLoc.y + tdy);
                Direction goal_d = curLoc.directionTo(runAwayFromThis).opposite();
                try {
                    for (Direction d : approximateDirections(goal_d)) {
                        if (rc.canMove(d)) {
                            rc.move(d);
                            return true;
                        }
                    }
                } catch (GameActionException e) {
                    System.out.println("Exception in doRetreat");
                    return false;
                }
            }
        }
        if (nearbyAllies.length <= 3) {
            // Micro for small skirmishes
            Robot[] veryNearbyAllies = rc.senseNearbyGameObjects(rc.getRobot().getClass(), rc.getType().attackRadiusMaxSquared, rc.getTeam());
            if (dangerousEnemyCount > veryNearbyAllies.length + 1 || rc.getHealth() < Math.max(25, 1.5 * dangerousEnemyCount * RobotType.SOLDIER.attackPower / (Math.min(3.1, nearbyAllies.length + 1)))) {
                // Run away from their center of mass
                if (rc.getHealth() < 50) {
                    retreating = true;
                }
                MapLocation runAwayFromThis = new MapLocation(curLoc.x + tdx, curLoc.y + tdy);
                Direction goal_d = curLoc.directionTo(runAwayFromThis).opposite();
                try {
                    for (Direction d : approximateDirections(goal_d)) {
                        if (rc.canMove(d)) {
                            rc.move(d);
                            return true;
                        }
                    }
                } catch (GameActionException e) {
                    System.out.println("Exception in doRetreat");
                    return false;
                }
            }
        }
        // Micro for large battles
        try {
            Robot[] attackingEnemies = rc.senseNearbyGameObjects(rc.getRobot().getClass(), rc.getType().attackRadiusMaxSquared, rc.getTeam().opponent());
            RobotInfo[] attackingInfos = new RobotInfo[attackingEnemies.length];
            double bestGrade = 0;
            for (int i = 0; i < attackingEnemies.length; i++) {
                attackingInfos[i] = rc.senseRobotInfo(attackingEnemies[i]);
                if (attackingInfos[i].type == RobotType.SOLDIER) {
                    bestGrade++;
                }
            }
            if (bestGrade < 1) {
                bestGrade = 1.5;
            }
            double originalGrade = bestGrade;
            if (bestGrade > 2) {
                Direction bestDirection = Direction.NONE;

                for (Direction d : all_directions) {
                    if (!rc.canMove(d)) {
                        continue;
                    }
                    double grade = 0;
                    MapLocation newLoc = curLoc.add(d);
                    for (RobotInfo info : attackingInfos) {
                        if (info.type == RobotType.SOLDIER && newLoc.distanceSquaredTo(info.location) <= RobotType.SOLDIER.attackRadiusMaxSquared) {
                            grade += 1;
                        }
                    }
                    if (grade < 1) {
                        grade = 1.5;
                    }
                    if (grade < bestGrade) {
                        bestGrade = grade;
                        bestDirection = d;
                    }
                }

                if (bestDirection != Direction.NONE && bestGrade < originalGrade - 1.2) {
                    rc.move(bestDirection);
                    return true;
                }
            }
        } catch (GameActionException e) {
            System.out.println("Exception in large battle retreats");
            return false;
        }
        return false;
    }

    static class Attacker {
        Team opp;
        Random rand;
        public Attacker(RobotController rc, Random rng) {
            opp = rc.getTeam().opponent();
            rand = rng;
        }
        public boolean doAttack(RobotController rc) {
            // Returns true if an attack happened
            Robot[] nearbyRobots = rc.senseNearbyGameObjects(rc.getRobot().getClass(), RobotType.SOLDIER.attackRadiusMaxSquared, opp);
            RobotInfo target = null;
            for (Robot r : nearbyRobots) {
                try {
                    RobotInfo info = rc.senseRobotInfo(r);
                    if (info.type != RobotType.HQ && (target == null || info.health < target.health)) {
                        target = info;
                    }
                } catch (Exception e) {
                    System.out.println("Exception in attacking");
                }
            }
            if (target != null) {
                try {
                    // With probability 0.2, broadcast the target.
                    MapLocation attackLoc = Communication.read_location(rc, Communication.attack_channel);
                    if (attackLoc == null || rand.nextInt(5) < 1) {
                        Communication.send_location(rc, Communication.attack_channel, target.location);
                    }
                    rc.attackSquare(target.location);
                    if (target.health <= RobotType.SOLDIER.attackPower) {
                        try {
                            rc.broadcast(Communication.kill_channel, 1);
                        } catch (GameActionException e) {
                            System.out.println("Error broadcasting kill");
                        }
                    }
                    return true;
                } catch (GameActionException e) {
                    System.out.println("Invalid attack");
                }
            }
            return false;
        }
    }
    
    static class Pathfinder {
        public boolean wall_following;
        public boolean clockwise;
        public Direction last_wall;
        public Direction last_move;
        public MapLocation next_waypoint;
        Random rand;
        boolean exact;
        RobotController rc;
        int[][] voidRegions;
        int nextVoidRegion;
        int curFollowingRegion;
        MapLocation enemyHQLocation;

        public Pathfinder(RobotController cont) {
            wall_following = false;
            last_wall = Direction.NONE;
            next_waypoint = null;
            clockwise = false;
            exact = false;
            rc = cont;
            curFollowingRegion = 0;
            // All the robots use the same seed so that pathing is deterministic, but mixes well
            rand = new Random();

            int width = rc.getMapWidth();
            int height = rc.getMapHeight();
            voidRegions = new int[width][height];
            for(int i = 0; i < width; i++) {
                for(int j = 0; j < height; j++) {
                    voidRegions[i][j] = 0;
                }
            }
            enemyHQLocation = rc.senseEnemyHQLocation();

            nextVoidRegion = 1;

            // Fill all the void regions
            // This could take a while
            for(int i = 0; i < width; i++) {
                for(int j = 0; j < height; j++) {
                    MapLocation loc = new MapLocation(i,j);
                    if (voidRegions[i][j] == 0 && rc.senseTerrainTile(loc) == TerrainTile.VOID) {
                        fillVoidRegion(loc);
                    }
                }
            }
        }

        public boolean unobstructed(MapLocation start, MapLocation end) {
            while(!start.equals(end)) {
                if(voidRegions[start.x][start.y] != 0) {
                    return false;
                }
                start = start.add(start.directionTo(end));
            }
            return true;
        }

        Direction[] orthogonalDirections = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        void fillVoidRegion(MapLocation loc) {
            doFillVoidRegion(loc, nextVoidRegion);
            nextVoidRegion++;
        }

        void doFillVoidRegion(MapLocation loc, int id) {
            TerrainTile tile = rc.senseTerrainTile(loc);
            if (tile != TerrainTile.OFF_MAP && (tile == TerrainTile.VOID || loc.distanceSquaredTo(enemyHQLocation) <= 27) && voidRegions[loc.x][loc.y] == 0) {
                voidRegions[loc.x][loc.y] = id;
                for(Direction d : orthogonalDirections) {
                    doFillVoidRegion(loc.add(d), id);
                }
            }
        }

        public void setWaypoint(MapLocation w) {
            if (!w.equals(next_waypoint)) {
                next_waypoint = w;
            }
        }

        boolean shouldStopFollowing(int region) {
            if (region == 0) {
                return true;
            }
            MapLocation loc = rc.getLocation();
            boolean derped = true;
            for(Direction d : all_directions) {
                MapLocation newLoc = loc.add(d);
                if (newLoc.x < 0 || newLoc.x >= voidRegions.length || newLoc.y < 0 || newLoc.y >= voidRegions[0].length || voidRegions[newLoc.x][newLoc.y] != 0) {
                    derped = false;
                    break;
                }
            }
            if (derped) {
                return true;
            }
            while (!loc.equals(next_waypoint)) {
                if (voidRegions[loc.x][loc.y] == region) {
                    return false;
                }
                loc = loc.add(loc.directionTo(next_waypoint));
            }
            return true;
        }

        boolean shouldFollowClockwise(MapLocation curLoc, Direction direct_move) {
            WallFollower cwFollower = new WallFollower(voidRegions, curLoc, direct_move, true);
            WallFollower ccwFollower = new WallFollower(voidRegions, curLoc, direct_move, false);
            boolean retval = true;
            int minSqDist = 999999999;
            int tempSqDist;
            for(int i = 0; i<100; i++) {
                MapLocation cwNext = cwFollower.next();
                if (cwNext == null) {
                    break;
                }
                tempSqDist = cwNext.distanceSquaredTo(next_waypoint);
                if (tempSqDist < minSqDist) {
                    minSqDist = tempSqDist;
                    retval = true;
                }
                MapLocation ccwNext = ccwFollower.next();
                if (ccwNext == null) {
                    break;
                }
                tempSqDist = ccwNext.distanceSquaredTo(next_waypoint);
                if (tempSqDist < minSqDist) {
                    minSqDist = tempSqDist;
                    retval = false;
                }
            }
            return retval;
        }

        public Direction nextDirection(RobotController rc) {
            if (next_waypoint == null || rc.getLocation().equals(next_waypoint)) {
                return Direction.NONE;
            }
            MapLocation curLoc = rc.getLocation();
            // Special case for retreats
            if (next_waypoint.equals(hqLocation) && curLoc.distanceSquaredTo(hqLocation) < 40) {
                if (curLoc.distanceSquaredTo(hqLocation) < 5) {
                    return Direction.NONE;
                }
                if (rc.canMove(curLoc.directionTo(hqLocation))) {
                    return curLoc.directionTo(hqLocation);
                }
                return Direction.NONE;
            }
            if (!exact && curLoc.distanceSquaredTo(next_waypoint) < 25 && unobstructed(curLoc, next_waypoint)) {
                return Direction.NONE;
            }
            // Special case for HQ contains
            if (next_waypoint.equals(enemyHQLocation) && curLoc.distanceSquaredTo(enemyHQLocation) < 40) {
                Direction d = curLoc.directionTo(enemyHQLocation);
                if (curLoc.distanceSquaredTo(enemyHQLocation) < 28) {
                    for (Direction d2 : approximateDirections(d.opposite())) {
                        if (rc.canMove(d2)) {
                            return d2;
                        }
                    }
                }
                if (curLoc.add(d).distanceSquaredTo(enemyHQLocation) < 28) {
                    return Direction.NONE;
                }
                return d;
            }
            Direction direct_move = curLoc.directionTo(next_waypoint);
            if (wall_following) {
                if (shouldStopFollowing(curFollowingRegion)) {
                    wall_following = false;
                }
            }
            if(!wall_following) {
                if(!customCanMove(rc, direct_move)) {
                    for(Direction d : approximateDirections(direct_move)) {
                        if(customCanMove(rc, d)) {
                            last_move = d;
                            return d;
                        }
                    }
                    MapLocation triedLoc = curLoc.add(direct_move);
                    if(voidRegions[triedLoc.x][triedLoc.y] != 0) {
                        wall_following = true;
                        curFollowingRegion = voidRegions[triedLoc.x][triedLoc.y];
                        clockwise = shouldFollowClockwise(curLoc, direct_move);
                        last_wall = direct_move;
                    }
                } else {
                    last_move = direct_move;
                    return direct_move;
                }
            }
            if(wall_following) {
                if (clockwise) {
                    for(Direction d : clockwiseDirectionsFrom(last_wall)) {
                        if(customCanMove(rc, d)) {
                            last_wall = clockwiseFrom(clockwiseFrom(d.opposite()));
                            last_move = d;
                            return d;
                        }
                    }
                } else {
                    for(Direction d : counterclockwiseDirectionsFrom(last_wall)) {
                        if(customCanMove(rc, d)) {
                            last_wall = counterclockwiseFrom(counterclockwiseFrom(d.opposite()));
                            last_move = d;
                            return d;
                        }
                    }
                }
            }
            for (Direction d : orthogonalDirections(direct_move)) {
                if (customCanMove(rc, d)) {
                    last_move = d;
                    return d;
                }
            }
            last_move = Direction.NONE;
            return Direction.NONE;
        }

        class WallFollower {
            int[][] voidRegions;
            MapLocation curLoc;
            MapLocation endLoc;
            Direction wall_direction;
            boolean clockwise;
            
            public WallFollower(int[][] voidRs, MapLocation loc, Direction wall_dir, boolean cw) {
                curLoc = loc;
                endLoc = loc;
                wall_direction = wall_dir;
                voidRegions = voidRs;
                clockwise = cw;
            }

            void step() {
                if (clockwise) {
                    for(Direction d : clockwiseDirectionsFrom(wall_direction)) {
                        MapLocation nextLoc = curLoc.add(d);
                        if (nextLoc.x >= 0 && nextLoc.x < voidRegions.length && nextLoc.y >= 0 && nextLoc.y < voidRegions[0].length && voidRegions[nextLoc.x][nextLoc.y] == 0) {
                            wall_direction = clockwiseFrom(clockwiseFrom(d.opposite()));
                            curLoc = nextLoc;
                            break;
                        }
                    }
                } else {
                    for(Direction d : counterclockwiseDirectionsFrom(wall_direction)) {
                        MapLocation nextLoc = curLoc.add(d);
                        if (nextLoc.x >= 0 && nextLoc.x < voidRegions.length && nextLoc.y >= 0 && nextLoc.y < voidRegions[0].length && voidRegions[nextLoc.x][nextLoc.y] == 0) {
                            wall_direction = counterclockwiseFrom(counterclockwiseFrom(d.opposite()));
                            curLoc = nextLoc;
                            break;
                        }
                    }
                }
            }

            public MapLocation next() {
                step();
                if (curLoc.equals(endLoc)) {
                    return null;
                }
                return curLoc;
            }
        }
    }

    static boolean customCanMove(RobotController rc, Direction d) {
        // Treats squares near the enemy HQ as bad
        if (!rc.canMove(d)) {
            return false;
        }
        MapLocation curLoc = rc.getLocation();
        MapLocation dest = curLoc.add(d);
        Robot[] nearbyAllies = rc.senseNearbyGameObjects(rc.getRobot().getClass(), rc.getType().sensorRadiusSquared, rc.getTeam());
        if (nearbyAllies.length > 5) {
            return dest.distanceSquaredTo(enemyHQLocation) > 15;
        }
        return dest.distanceSquaredTo(enemyHQLocation) > 27.95;
    }

    static boolean customMove(RobotController rc, Direction d) throws GameActionException {
        // Only move into enemy firing range if there is reasonable support.
        MapLocation curLoc = rc.getLocation();
        MapLocation nextLoc = curLoc.add(d);
        Robot[] dangerousEnemyRobots = rc.senseNearbyGameObjects(rc.getRobot().getClass(), nextLoc, RobotType.SOLDIER.attackRadiusMaxSquared, rc.getTeam().opponent());
        int dangerousEnemyCount = 0;
        for (Robot r : dangerousEnemyRobots) {
            RobotInfo info = rc.senseRobotInfo(r);
            if (info.type == RobotType.SOLDIER) {
                dangerousEnemyCount++;
            }
        }
        if(dangerousEnemyCount == 0) {
            rc.move(d);
            return true;
        }
        if(dangerousEnemyCount > 2 || rc.getHealth() < 30) {
            return false;
        }
        // Exception for defending pastures
        if (defending) {
            rc.move(d);
            return true;
        }
        Robot[] nearbyAlliedRobots = rc.senseNearbyGameObjects(rc.getRobot().getClass(), RobotType.SOLDIER.sensorRadiusSquared, rc.getTeam());
        Robot[] nearbyEnemyRobots = rc.senseNearbyGameObjects(rc.getRobot().getClass(), RobotType.SOLDIER.sensorRadiusSquared, rc.getTeam().opponent());
        int somewhatDangerousEnemyCount = 0;
        for (Robot r : nearbyEnemyRobots) {
            RobotInfo info = rc.senseRobotInfo(r);
            if (info.type == RobotType.SOLDIER) {
                somewhatDangerousEnemyCount++;
            }
        }
        if(nearbyAlliedRobots.length - somewhatDangerousEnemyCount >= dangerousEnemyCount/4) {
            rc.move(d);
            return true;
        }
        if(somewhatDangerousEnemyCount == 1) {
            try {
                RobotInfo info = rc.senseRobotInfo(dangerousEnemyRobots[0]);
                if (rc.getHealth() >= info.health) {
                    rc.move(d);
                    return true;
                }
            } catch(Exception e) {
                System.out.println("Exception in 1v1 case");
            }
        }
        return false;
    }

    static boolean customSneak(RobotController rc, Direction d) throws GameActionException {
        // Only move into enemy firing range if there is reasonable support.
        MapLocation curLoc = rc.getLocation();
        MapLocation nextLoc = curLoc.add(d);
        Robot[] dangerousEnemyRobots = rc.senseNearbyGameObjects(rc.getRobot().getClass(), nextLoc, RobotType.SOLDIER.attackRadiusMaxSquared, rc.getTeam().opponent());
        int dangerousEnemyCount = 0;
        for (Robot r : dangerousEnemyRobots) {
            RobotInfo info = rc.senseRobotInfo(r);
            if (info.type == RobotType.SOLDIER) {
                dangerousEnemyCount++;
            }
        }
        if(dangerousEnemyCount == 0) {
            rc.sneak(d);
            return true;
        }
        Robot[] nearbyAlliedRobots = rc.senseNearbyGameObjects(rc.getRobot().getClass(), RobotType.SOLDIER.sensorRadiusSquared, rc.getTeam());
        Robot[] nearbyEnemyRobots = rc.senseNearbyGameObjects(rc.getRobot().getClass(), RobotType.SOLDIER.sensorRadiusSquared, rc.getTeam().opponent());
        if(nearbyAlliedRobots.length - nearbyEnemyRobots.length >= dangerousEnemyCount/4) {
            rc.sneak(d);
            return true;
        }
        return false;
    }

    static boolean moveApproximatelyToward(RobotController rc, MapLocation dest) {
        // Returns whether the move actually happened
        for (Direction d : approximateDirections(rc.getLocation().directionTo(dest))) {
            if (customCanMove(rc, d)) {
                try {
                    if (customMove(rc, d)) {
                        return true;
                    }
                } catch (GameActionException e) {
                    System.out.println("Exception in moveApproximatelyToward");
                }
            }
        }
        return false;
    }

    static Direction[] north_neighbors = {Direction.NORTH, Direction.NORTH_EAST, Direction.NORTH_WEST};
    static Direction[] north_east_neighbors = {Direction.NORTH_EAST, Direction.EAST, Direction.NORTH};
    static Direction[] east_neighbors = {Direction.EAST, Direction.SOUTH_EAST, Direction.NORTH_EAST};
    static Direction[] south_east_neighbors = {Direction.SOUTH_EAST, Direction.SOUTH, Direction.EAST};
    static Direction[] south_neighbors = {Direction.SOUTH, Direction.SOUTH_WEST, Direction.SOUTH_EAST};
    static Direction[] south_west_neighbors = {Direction.SOUTH_WEST, Direction.WEST, Direction.SOUTH};
    static Direction[] west_neighbors = {Direction.WEST, Direction.NORTH_WEST, Direction.SOUTH_WEST};
    static Direction[] north_west_neighbors = {Direction.NORTH_WEST, Direction.NORTH, Direction.WEST};
    static Direction[] all_directions = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};

    static Direction[] approximateDirections(Direction d) {
        switch(d) {
            case NORTH:
                return north_neighbors;
            case NORTH_EAST:
                return north_east_neighbors;
            case EAST:
                return east_neighbors;
            case SOUTH_EAST:
                return south_east_neighbors;
            case SOUTH:
                return south_neighbors;
            case SOUTH_WEST:
                return south_west_neighbors;
            case WEST:
                return west_neighbors;
            case NORTH_WEST:
                return north_west_neighbors;
        }
        return all_directions;
    }

    static Direction[] clockwise_north = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};
    static Direction[] clockwise_north_east = {Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST, Direction.NORTH};
    static Direction[] clockwise_east = {Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST, Direction.NORTH, Direction.NORTH_EAST};
    static Direction[] clockwise_south_east = {Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST, Direction.NORTH, Direction.NORTH_EAST, Direction.EAST};
    static Direction[] clockwise_south = {Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST, Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST};
    static Direction[] clockwise_south_west = {Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST, Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH};
    static Direction[] clockwise_west = {Direction.WEST, Direction.NORTH_WEST, Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST};
    static Direction[] clockwise_north_west = {Direction.NORTH_WEST, Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST};

    static Direction[] clockwiseDirectionsFrom(Direction d) {
        switch (d) {
            case NORTH:
                return clockwise_north;
            case NORTH_EAST:
                return clockwise_north_east;
            case EAST:
                return clockwise_east;
            case SOUTH_EAST:
                return clockwise_south_east;
            case SOUTH:
                return clockwise_south;
            case SOUTH_WEST:
                return clockwise_south_west;
            case WEST:
                return clockwise_west;
            case NORTH_WEST:
                return clockwise_north_west;
        }
        return clockwise_north;
    }

    static Direction[] counterclockwise_north = {Direction.NORTH, Direction.NORTH_WEST, Direction.WEST, Direction.SOUTH_WEST, Direction.SOUTH, Direction.SOUTH_EAST, Direction.EAST, Direction.NORTH_EAST};
    static Direction[] counterclockwise_north_west = {Direction.NORTH_WEST, Direction.WEST, Direction.SOUTH_WEST, Direction.SOUTH, Direction.SOUTH_EAST, Direction.EAST, Direction.NORTH_EAST, Direction.NORTH};
    static Direction[] counterclockwise_west = {Direction.WEST, Direction.SOUTH_WEST, Direction.SOUTH, Direction.SOUTH_EAST, Direction.EAST, Direction.NORTH_EAST, Direction.NORTH, Direction.NORTH_WEST};
    static Direction[] counterclockwise_south_west = {Direction.SOUTH_WEST, Direction.SOUTH, Direction.SOUTH_EAST, Direction.EAST, Direction.NORTH_EAST, Direction.NORTH, Direction.NORTH_WEST, Direction.WEST};
    static Direction[] counterclockwise_south = {Direction.SOUTH, Direction.SOUTH_EAST, Direction.EAST, Direction.NORTH_EAST, Direction.NORTH, Direction.NORTH_WEST, Direction.WEST, Direction.SOUTH_WEST};
    static Direction[] counterclockwise_south_east = {Direction.SOUTH_EAST, Direction.EAST, Direction.NORTH_EAST, Direction.NORTH, Direction.NORTH_WEST, Direction.WEST, Direction.SOUTH_WEST, Direction.SOUTH};
    static Direction[] counterclockwise_east = {Direction.EAST, Direction.NORTH_EAST, Direction.NORTH, Direction.NORTH_WEST, Direction.WEST, Direction.SOUTH_WEST, Direction.SOUTH, Direction.SOUTH_EAST};
    static Direction[] counterclockwise_north_east = {Direction.NORTH_EAST, Direction.NORTH, Direction.NORTH_WEST, Direction.WEST, Direction.SOUTH_WEST, Direction.SOUTH, Direction.SOUTH_EAST, Direction.EAST};
    static Direction[] counterclockwiseDirectionsFrom(Direction d) {
        switch (d) {
            case NORTH:
                return counterclockwise_north;
            case NORTH_EAST:
                return counterclockwise_north_east;
            case EAST:
                return counterclockwise_east;
            case SOUTH_EAST:
                return counterclockwise_south_east;
            case SOUTH:
                return counterclockwise_south;
            case SOUTH_WEST:
                return counterclockwise_south_west;
            case WEST:
                return counterclockwise_west;
            case NORTH_WEST:
                return counterclockwise_north_west;
        }
        return counterclockwise_north;
    }

    static Direction counterclockwiseFrom(Direction d) {
        switch (d) {
            case NORTH:
                return Direction.NORTH_WEST;
            case NORTH_EAST:
                return Direction.NORTH;
            case EAST:
                return Direction.NORTH_EAST;
            case SOUTH_EAST:
                return Direction.EAST;
            case SOUTH:
                return Direction.SOUTH_EAST;
            case SOUTH_WEST:
                return Direction.SOUTH;
            case WEST:
                return Direction.SOUTH_WEST;
            case NORTH_WEST:
                return Direction.WEST;
            case NONE:
                return Direction.NONE;
            case OMNI:
                return Direction.OMNI;
        }
        return Direction.NONE;
    }

    static Direction clockwiseFrom(Direction d) {
        switch (d) {
            case NORTH:
                return Direction.NORTH_EAST;
            case NORTH_EAST:
                return Direction.EAST;
            case EAST:
                return Direction.SOUTH_EAST;
            case SOUTH_EAST:
                return Direction.SOUTH;
            case SOUTH:
                return Direction.SOUTH_WEST;
            case SOUTH_WEST:
                return Direction.WEST;
            case WEST:
                return Direction.NORTH_WEST;
            case NORTH_WEST:
                return Direction.NORTH;
            case NONE:
                return Direction.NONE;
            case OMNI:
                return Direction.OMNI;
        }
        return Direction.NONE;
    }

    static Direction opposite(Direction d) {
        switch (d) {
            case NORTH:
                return Direction.SOUTH;
            case NORTH_EAST:
                return Direction.SOUTH_WEST;
            case EAST:
                return Direction.WEST;
            case SOUTH_EAST:
                return Direction.NORTH_WEST;
            case SOUTH:
                return Direction.NORTH;
            case SOUTH_WEST:
                return Direction.NORTH_EAST;
            case WEST:
                return Direction.EAST;
            case NORTH_WEST:
                return Direction.SOUTH_EAST;
            case NONE:
                return Direction.NONE;
            case OMNI:
                return Direction.OMNI;
        }
        return Direction.NONE;
    }

    static Direction[] orth_north = {Direction.EAST, Direction.WEST};
    static Direction[] orth_north_east = {Direction.SOUTH_EAST, Direction.NORTH_WEST};
    static Direction[] orth_east = {Direction.NORTH, Direction.SOUTH};
    static Direction[] orth_south_east = {Direction.NORTH_EAST, Direction.SOUTH_WEST};
    static Direction[] orthogonalDirections(Direction d) {
        switch (d) {
            case NORTH:
            case SOUTH:
                return orth_north;
            case NORTH_EAST:
            case SOUTH_WEST:
                return orth_north_east;
            case EAST:
            case WEST:
                return orth_east;
            case SOUTH_EAST:
            case NORTH_WEST:
                return orth_south_east;
        }
        return all_directions;
    }
}
