package org.bitbucket.openkilda.pce.model;

import static com.google.common.base.MoreObjects.toStringHelper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/**
 * Represents flow entity.
 */
@JsonSerialize
public class Flow implements Serializable {
    /**
     * Flow id.
     */
    @JsonProperty("flowid")
    private String flowId;

    /**
     * FLow bandwidth.
     */
    @JsonProperty("bandwidth")
    private long bandwidth;

    /**
     * Flow cookie.
     */
    @JsonProperty("cookie")
    private long cookie;

    /**
     * Flow description.
     */
    @JsonProperty("description")
    private String description;

    /**
     * Flow last updated timestamp.
     */
    @JsonProperty("last_updated")
    private String lastUpdated;

    /**
     * Flow source switch.
     */
    @JsonProperty("src_switch")
    private String sourceSwitch;

    /**
     * Flow destination switch.
     */
    @JsonProperty("dst_switch")
    private String destinationSwitch;

    /**
     * Flow source port.
     */
    @JsonProperty("src_port")
    private int sourcePort;

    /**
     * Flow destination port.
     */
    @JsonProperty("dst_port")
    private int destinationPort;

    /**
     * Flow source vlan id.
     */
    @JsonProperty("src_vlan")
    private int sourceVlan;

    /**
     * Flow destination vlan id.
     */
    @JsonProperty("dst_vlan")
    private int destinationVlan;

    /**
     * Flow transit vlan id.
     */
    @JsonProperty("transit_vlan")
    private int transitVlan;

    /**
     * Flow switch path.
     */
    @JsonProperty("flowpath")
    private Set<Node> flowPath;

    /**
     * Flow isl path.
     */
    @JsonProperty("isl_path")
    private Set<Node> islPath;

    /**
     * Default constructor.
     */
    public Flow() {
    }

    /**
     * Instance constructor.
     *
     * @param flowId            flow id
     * @param bandwidth         bandwidth
     * @param cookie            cookie
     * @param description       description
     * @param lastUpdated       last updated timestamp
     * @param sourceSwitch      source switch
     * @param destinationSwitch destination switch
     * @param sourcePort        source port
     * @param destinationPort   destination port
     * @param sourceVlan        source vlan id
     * @param destinationVlan   destination vlan id
     * @param transitVlan       transit vlan id
     * @param flowPath          flow switch path
     * @param islPath           flow isl path
     */
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
                @JsonProperty("flowpath") final Set<Node> flowPath,
                @JsonProperty("isl_path") final Set<Node> islPath) {
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
        this.islPath = islPath;
    }

    /**
     * Gets flow id.
     *
     * @return flow id
     */
    public String getFlowId() {
        return flowId;
    }

    /**
     * Sets flow id.
     *
     * @param flowId flow id
     */
    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    /**
     * Gets flow bandwidth.
     *
     * @return flow bandwidth
     */
    public long getBandwidth() {
        return bandwidth;
    }

    /**
     * Sets flow bandwidth.
     *
     * @param bandwidth flow bandwidth
     */
    public void setBandwidth(long bandwidth) {
        this.bandwidth = bandwidth;
    }

    /**
     * Gets flow cookie.
     *
     * @return flow cookie
     */
    public long getCookie() {
        return cookie;
    }

    /**
     * Sets flow cookie.
     *
     * @param cookie flow cookie
     */
    public void setCookie(long cookie) {
        this.cookie = cookie;
    }

    /**
     * Gets flow description.
     *
     * @return flow description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets flow description.
     *
     * @param description flow description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets flow last updated timestamp.
     *
     * @return flow last updated timestamp
     */
    public String getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Sets flow last updated timestamp.
     *
     * @param lastUpdated flow last updated timestamp
     */
    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    /**
     * Gets flow source switch.
     *
     * @return flow source switch
     */
    public String getSourceSwitch() {
        return sourceSwitch;
    }

    /**
     * Sets flow source switch.
     *
     * @param sourceSwitch flow source switch
     */
    public void setSourceSwitch(String sourceSwitch) {
        this.sourceSwitch = sourceSwitch;
    }

    /**
     * Gets flow destination switch.
     *
     * @return flow destination switch
     */
    public String getDestinationSwitch() {
        return destinationSwitch;
    }

    /**
     * Sets flow destination switch.
     *
     * @param destinationSwitch flow destination switch
     */
    public void setDestinationSwitch(String destinationSwitch) {
        this.destinationSwitch = destinationSwitch;
    }

    /**
     * Gets flow source port.
     *
     * @return flow source port
     */
    public int getSourcePort() {
        return sourcePort;
    }

    /**
     * Sets flow source port.
     *
     * @param sourcePort flow source port
     */
    public void setSourcePort(int sourcePort) {
        this.sourcePort = sourcePort;
    }

    /**
     * Gets flow destination port.
     *
     * @return flow destination port
     */
    public int getDestinationPort() {
        return destinationPort;
    }

    /**
     * Sets flow destination port.
     *
     * @param destinationPort flow destination port
     */
    public void setDestinationPort(int destinationPort) {
        this.destinationPort = destinationPort;
    }

    /**
     * Gets flow source vlan id.
     *
     * @return flow source vlan id
     */
    public int getSourceVlan() {
        return sourceVlan;
    }

    /**
     * Sets flow source vlan id.
     *
     * @param sourceVlan flow source vlan id
     */
    public void setSourceVlan(int sourceVlan) {
        this.sourceVlan = sourceVlan;
    }

    /**
     * Gets flow destination vlan id.
     *
     * @return flow destination vlan id
     */
    public int getDestinationVlan() {
        return destinationVlan;
    }

    /**
     * Sets flow destination vlan id.
     *
     * @param destinationVlan flow destination vlan id
     */
    public void setDestinationVlan(int destinationVlan) {
        this.destinationVlan = destinationVlan;
    }

    /**
     * Gets flow transit vlan id.
     *
     * @return flow transit vlan id
     */
    public int getTransitVlan() {
        return transitVlan;
    }

    /**
     * Sets flow transit vlan id.
     *
     * @param transitVlan flow transit vlan id
     */
    public void setTransitVlan(int transitVlan) {
        this.transitVlan = transitVlan;
    }

    /**
     * Gets flow switch path.
     *
     * @return flow switch path
     */
    public Set<Node> getFlowPath() {
        return flowPath;
    }

    /**
     * Sets flow switch path.
     *
     * @param flowPath flow switch path
     */
    public void setFlowPath(Set<Node> flowPath) {
        this.flowPath = flowPath;
    }

    /**
     * Gets flow isl path.
     *
     * @return flow isl path
     */
    public Set<Node> getIslPath() {
        return islPath;
    }

    /**
     * Sets flow isl path.
     *
     * @param islPath flow isl path
     */
    public void setIslPath(Set<Node> islPath) {
        this.islPath = islPath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        Flow flow = (Flow) object;
        return getBandwidth() == flow.getBandwidth() &&
                getCookie() == flow.getCookie() &&
                getSourcePort() == flow.getSourcePort() &&
                getDestinationPort() == flow.getDestinationPort() &&
                getSourceVlan() == flow.getSourceVlan() &&
                getDestinationVlan() == flow.getDestinationVlan() &&
                getTransitVlan() == flow.getTransitVlan() &&
                Objects.equals(getFlowId(), flow.getFlowId()) &&
                Objects.equals(getDescription(), flow.getDescription()) &&
                Objects.equals(getLastUpdated(), flow.getLastUpdated()) &&
                Objects.equals(getSourceSwitch(), flow.getSourceSwitch()) &&
                Objects.equals(getDestinationSwitch(), flow.getDestinationSwitch()) &&
                Objects.equals(getFlowPath(), flow.getFlowPath()) &&
                Objects.equals(getIslPath(), flow.getIslPath());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(flowId, bandwidth, cookie, description, lastUpdated, sourceSwitch, destinationSwitch,
                sourcePort, destinationPort, sourceVlan, destinationVlan, transitVlan, flowPath, islPath);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("flowid", flowId)
                .add("src_switch", sourceSwitch)
                .add("src_port", sourcePort)
                .add("src_vlan", sourceVlan)
                .add("dst_switch", destinationSwitch)
                .add("dst_port", destinationPort)
                .add("dst_vlan", destinationVlan)
                .add("bandwidth", bandwidth)
                .add("description", description)
                .add("cookie", cookie)
                .add("transit_vlan", transitVlan)
                .add("last_updated", lastUpdated)
                .toString();
    }
}
