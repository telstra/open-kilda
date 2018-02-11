package org.openkilda.wfm.topology;

import org.openkilda.wfm.topology.stats.metrics.FlowMetricGenBolt;

import java.util.HashMap;
import java.util.Map;

public class TestFlowGenMetricsBolt extends FlowMetricGenBolt{
    private long cookie;
    private String flowId;
    private String srcSw;
    private String dstSw;
    private String direction;

    public TestFlowGenMetricsBolt(long cookie, String flowId, String srcSw, String dstSw) {
        this.cookie = cookie;
        this.flowId = flowId;
        this.srcSw = srcSw;
        this.dstSw = dstSw;
    }

    @Override
    protected Map getFlowWithCookie(Long cookie) {
        Map<String, Object> result = new HashMap<>();
        result.put("cookie", cookie);
        result.put("flowid", flowId);
        result.put("src_switch", srcSw);
        result.put("dst_switch", dstSw);

        Map<String, Object> cypherResult = new HashMap<>();
        cypherResult.put("r", result);
        return cypherResult;
    }
}
