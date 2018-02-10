package org.openkilda.wfm.topology.stats;

import org.openkilda.wfm.topology.FlowCookieException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class FlowResult {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlowResult.class);
    private long cookie;
    private String flowId;
    private String srcSw;
    private String dstSw;
    private String direction;

    // Required fields in the query result set.
    final List<String> requiredKeys = Arrays.asList("cookie", "flowid", "src_switch", "dst_switch");

    /**
     * Results of cypher flow query mapped to an object
     *
     * @param flow
     * @throws Exception
     */
    public FlowResult(Map<String, Object> flow) throws Exception {
        if (!flow.keySet().containsAll(requiredKeys)) {
            throw new Exception("Map returned by GraphDB does not have all required fields for a flow.");
        }

        cookie = (long) flow.get("cookie");
        flowId = flow.get("flowid").toString();
        srcSw = flow.get("src_switch").toString();
        dstSw = flow.get("dst_switch").toString();
        try {
            direction = findDirection((long) flow.get("cookie"));
        } catch (FlowCookieException e) {
            LOGGER.error("Error getting direction for " + cookie, e);
            direction = "unknown";
        }
    }

    @Override
    public String toString() {
        return "FlowResult{" +
                "cookie=" + cookie +
                ", flowId='" + flowId + '\'' +
                ", srcSw='" + srcSw + '\'' +
                ", dstSw='" + dstSw + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FlowResult)) return false;
        FlowResult that = (FlowResult) o;
        return cookie == that.cookie &&
                Objects.equals(flowId, that.flowId) &&
                Objects.equals(srcSw, that.srcSw) &&
                Objects.equals(dstSw, that.dstSw);
    }

    @Override
    public int hashCode() {

        return Objects.hash(cookie, flowId, srcSw, dstSw);
    }

    /**
     * Trys to determine the direction of the flow based on the cookie.
     *
     * @param cookie
     * @return
     */
    public String findDirection(long cookie) throws FlowCookieException {
        // Kilda flow first number represents direction with 4 = forward and 2 = reverse
        // Legacy flow Cookies 0x10400000005d803 is first switch in forward direction
        //                     0x18400000005d803 is first switch in reverse direction
        // first number represents switch seqId in path
        // second number represents forward/reverse
        // third number no idea
        // rest is the same for the flow
        String direction = "unknown";
        try {
            direction = getLegacyDirection(cookie);
        } catch (FlowCookieException e) {
            direction = getKildaDirection(cookie);
        }
        return direction;
    }

    public boolean isLegacyCookie(long cookie) {
        // A legacy cookie will have a value of 0 for the high order nibble
        // and the second nibble of >= 1
        // and the third nibble will be 0 or 8
        // and the fourth octet will be 4
        long firstNibble = cookie >>> 60 & 0xf;
        long switchSeqId = cookie >>> 56 & 0xf;
        long param = cookie >>> 48 & 0xf;
        return (firstNibble == 0) && (switchSeqId > 0) && (param == 4);
    }

    public boolean isKildaCookie(long cookie) {
        // A Kilda cookie (with a smallish number of flows) will have a 8, 2 or 4 in the highest nibble
        // and the second, third, forth nibble will be 0
        long flowType = cookie >>> 60 & 0xf;
        long nibbles = cookie >>> 48 & 0xfff;
        return ((flowType == 2) || (flowType == 4) || (flowType == 8)) && nibbles == 0;
    }

    public String getKildaDirection(long cookie) throws FlowCookieException {
        // high order nibble represents type of flow with a 2 representing a forward flow
        // and a 4 representing the reverse flow
        if (!isKildaCookie(cookie)) {
            throw new FlowCookieException(cookie + " is not a Kilda flow");
        }
        long direction = cookie >>> 60 & 0xff;
        if ((direction != 2) && (direction != 4)) {
            throw new FlowCookieException("unknown direction for " + cookie);
        }
        return direction == 4 ? "forward" : "reverse";
    }

    public String getLegacyDirection(long cookie) throws FlowCookieException {
        // Direction is the 3rd nibble from the top
        // If nibble is 0 it is forward and 8 is reverse
        if (!isLegacyCookie(cookie)) {
            throw new FlowCookieException(cookie + " is not a legacy flow");
        }
        long direction = cookie >>> 52 & 0xf;
        if ((direction != 0) && (direction != 8)) {
            throw new FlowCookieException("unknown direction for " + cookie);
        }
        return direction == 0 ? "forward" : "reverse";
    }

    public long getCookie() {
        return cookie;
    }

    public String getFlowId() {
        return flowId;
    }

    public String getSrcSw() {
        return srcSw;
    }

    public String getDstSw() {
        return dstSw;
    }

    public String getDirection() {
        return direction;
    }
}
