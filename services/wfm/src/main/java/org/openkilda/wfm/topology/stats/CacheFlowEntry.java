package org.openkilda.wfm.topology.stats;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.io.Serializable;

@Value
public class CacheFlowEntry implements Serializable {

    @NonNull
    private String flowId;

    private String ingressSwitch;
    private String egressSwitch;

    public CacheFlowEntry(String flowId) {
        this(flowId, null, null);
    }

    @Builder(toBuilder = true)
    public CacheFlowEntry(String flowId, String ingressSwitch, String egressSwitch) {
        this.flowId = flowId;
        this.ingressSwitch = ingressSwitch;
        this.egressSwitch = egressSwitch;
    }

    public CacheFlowEntry replace(String sw, MeasurePoint point) {
        CacheFlowEntryBuilder replacement = toBuilder();
        switch (point) {
            case INGRESS:
                replacement.ingressSwitch(sw);
                break;
            case EGRESS:
                replacement.egressSwitch(sw);
                break;
            default:
                throw new IllegalArgumentException(String.format("Unsupported measurement point value %s", point));
        }
        return replacement.build();
    }
}
