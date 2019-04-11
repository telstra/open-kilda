package org.openkilda.wfm.topology.reroute.bolts;

import org.openkilda.wfm.topology.reroute.model.FlowThrottlingData;

public interface SendRerouteRequestCarrier {
    void sendRerouteRequest(String flowId, FlowThrottlingData payload, String reason);
}
