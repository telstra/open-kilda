package org.openkilda.wfm.topology.stats;

public class FlowDirectionHelper {


    public enum Direction
    {
        UNKNOWN,
        FORWARD,
        REVERSE
    }


    /**
     * Trys to determine the direction of the flow based on the cookie.
     *
     * @param cookie
     * @return
     */
    static public Direction findDirection(long cookie) throws FlowCookieException {
        // Kilda flow first number represents direction with 4 = forward and 2 = reverse
        // Legacy flow Cookies 0x10400000005d803 is first switch in forward direction
        //                     0x18400000005d803 is first switch in reverse direction
        // first number represents switch seqId in path
        // second number represents forward/reverse
        // third number no idea
        // rest is the same for the flow
        Direction direction = Direction.UNKNOWN;
        try {
            direction = getLegacyDirection(cookie);
        } catch (FlowCookieException e) {
            direction = getKildaDirection(cookie);
        }
        return direction;
    }

    static public boolean isLegacyCookie(long cookie) {
        // A legacy cookie will have a value of 0 for the high order nibble
        // and the second nibble of >= 1
        // and the third nibble will be 0 or 8
        // and the fourth octet will be 4
        long firstNibble = cookie >>> 60 & 0xf;
        long switchSeqId = cookie >>> 56 & 0xf;
        long param = cookie >>> 48 & 0xf;
        return (firstNibble == 0) && (switchSeqId > 0) && (param == 4);
    }

    static public boolean isKildaCookie(long cookie) {
        // A Kilda cookie (with a smallish number of flows) will have a 8, 2 or 4 in the highest nibble
        // and the second, third, forth nibble will be 0
        long flowType = cookie >>> 60 & 0xf;
        long nibbles = cookie >>> 48 & 0xfff;
        return ((flowType == 2) || (flowType == 4) || (flowType == 8)) && nibbles == 0;
    }

    static public Direction getKildaDirection(long cookie) throws FlowCookieException {
        // high order nibble represents type of flow with a 2 representing a forward flow
        // and a 4 representing the reverse flow
        if (!isKildaCookie(cookie)) {
            throw new FlowCookieException(cookie + " is not a Kilda flow");
        }
        long direction = cookie >>> 60 & 0xff;
        if ((direction != 2) && (direction != 4)) {
            throw new FlowCookieException("unknown direction for " + cookie);
        }
        return direction == 4 ? Direction.FORWARD : Direction.REVERSE;
    }

    static public Direction getLegacyDirection(long cookie) throws FlowCookieException {
        // Direction is the 3rd nibble from the top
        // If nibble is 0 it is forward and 8 is reverse
        if (!isLegacyCookie(cookie)) {
            throw new FlowCookieException(cookie + " is not a legacy flow");
        }
        long direction = cookie >>> 52 & 0xf;
        if ((direction != 0) && (direction != 8)) {
            throw new FlowCookieException("unknown direction for " + cookie);
        }
        return direction == 0 ? Direction.FORWARD : Direction.REVERSE;
    }

}
