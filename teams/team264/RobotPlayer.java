package team264;

import battlecode.common.Direction;
import battlecode.common.GameConstants;
import battlecode.common.RobotController;
import battlecode.common.RobotType;
import battlecode.common.*;
import java.util.*;

public class RobotPlayer {
	static Random rand;
	
	public static void run(RobotController rc) {
        while (true) {
            switch (rc.getType()) {
                case HQ:
                    HQPlayer.run(rc);
                    break;
                case SOLDIER:
                    SoldierPlayer.run(rc);
                    break;
                case PASTR:
                    PasturePlayer.run(rc);
                    break;
                case NOISETOWER:
                    NoiseTowerPlayer.run(rc);
                    break;
            }
            rc.yield();
        }
	}
}
