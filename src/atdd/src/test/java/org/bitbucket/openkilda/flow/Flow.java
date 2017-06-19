package org.bitbucket.openkilda.flow;

import static com.google.common.base.MoreObjects.toStringHelper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public class Flow {
    @JsonProperty("flowid")
    private String flowId;
    @JsonProperty("bandwidth")
    private long bandwidth;
    @JsonProperty("cookie")
    private long cookie;
    @JsonProperty("description")
    private String description;
    @JsonProperty("last_updated")
    private String lastUpdated;
    @JsonProperty("src_switch")
    private String sourceSwitch;
    @JsonProperty("dst_switch")
    private String destinationSwitch;
    @JsonProperty("src_port")
    private int sourcePort;
    @JsonProperty("dst_port")
    private int destinationPort;
    @JsonProperty("src_vlan")
    private int sourceVlan;
    @JsonProperty("dst_vlan")
    private int destinationVlan;
    @JsonProperty("transit_vlan")
    private int transitVlan;
    @JsonProperty("flowpath")
    private List<String> flowPath;

    @JsonCreator
    public Flow(@JsonProperty("flowid") final String flowId,
                @JsonProperty("bandwidth") final long bandwidth,
                @JsonProperty("cookie") final long cookie,
                @JsonProperty("description") final String description,
                @JsonProperty("last_updated") final String lastUpdated,
                @JsonProperty("src_switch") final String sourceSwitch,
                @JsonProperty("dst_switch") final String destinationSwitch,
                @JsonProperty("src_port") final int sourcePort,
                @JsonProperty("dst_port") final int destinationPort,
                @JsonProperty("src_vlan") final int sourceVlan,
                @JsonProperty("dst_vlan") final int destinationVlan,
                @JsonProperty("transit_vlan") final int transitVlan,
                @JsonProperty("flowpath") final List<String> flowPath) {
        this.flowId = flowId;
        this.bandwidth = bandwidth;
        this.cookie = cookie;
        this.description = description;
        this.lastUpdated = lastUpdated;
        this.sourceSwitch = sourceSwitch;
        this.destinationSwitch = destinationSwitch;
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
        this.sourceVlan = sourceVlan;
        this.destinationVlan = destinationVlan;
        this.transitVlan = transitVlan;
        this.flowPath = flowPath;
    }

    /**
     * Returns flow id.
     *
     * @return flow id
     */
    public String getFlowId() {
        return flowId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Flow)) return false;
        Flow flow = (Flow) o;
        return bandwidth == flow.bandwidth &&
                sourcePort == flow.sourcePort &&
                destinationPort == flow.destinationPort &&
                sourceVlan == flow.sourceVlan &&
                destinationVlan == flow.destinationVlan &&
                Objects.equals(flowId, flow.flowId) &&
                Objects.equals(description, flow.description) &&
                Objects.equals(sourceSwitch, flow.sourceSwitch) &&
                Objects.equals(destinationSwitch, flow.destinationSwitch);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("flowid", flowId)
                .add("description", description)
                .add("bandwidth", bandwidth)
                .add("src_switch", sourceSwitch)
                .add("src_port", sourcePort)
                .add("src_vlan", sourceVlan)
                .add("dst_switch", destinationSwitch)
                .add("dst_port", destinationPort)
                .add("dst_vlan", destinationVlan)
                .add("cookie", cookie)
                .add("transit_vlan", transitVlan)
                .add("last_updated", lastUpdated)
                .toString();
    }
}
