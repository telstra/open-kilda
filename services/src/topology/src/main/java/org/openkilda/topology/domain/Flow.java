/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.topology.domain;

import static com.google.common.base.Objects.toStringHelper;
import static org.openkilda.topology.service.impl.FlowServiceImpl.DIRECT_FLOW_COOKIE;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallTransitFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.payload.flow.OutputVlanType;

import org.neo4j.ogm.annotation.EndNode;
import org.neo4j.ogm.annotation.GraphId;
import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.RelationshipEntity;
import org.neo4j.ogm.annotation.StartNode;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Flow Relationship Entity.
 */
@RelationshipEntity(type = "flow")
public class Flow implements Serializable {
    /**
     * Graph id.
     */
    @GraphId
    Long id;

    /**
     * Start relationship node entity.
     */
    @StartNode
    private Switch start;

    /**
     * End relationship node entity.
     */
    @EndNode
    private Switch end;

    /**
     * Flow id.
     * Indexed for search.
     */
    @Property(name = "flow_id")
    @Index
    private String flowId;

    /**
     * Flow cookie.
     * Indexed for search.
     */
    @Property(name = "cookie")
    @Index
    private long cookie;

    /**
     * Flow path.
     * TODO: store list of isl
     */
    @Property(name = "flow_path")
    private List<String> flowPath;

    /**
     * Source switch datapath id.
     */
    @Property(name = "src_switch")
    private String sourceSwitch;

    /**
     * Source port number.
     */
    @Property(name = "src_port")
    private int sourcePort;

    /**
     * Source vlan id.
     */
    @Property(name = "src_vlan")
    private int sourceVlan;

    /**
     * Destination switch datapath id.
     */
    @Property(name = "dst_switch")
    private String destinationSwitch;

    /**
     * Destination port number.
     */
    @Property(name = "dst_port")
    private int destinationPort;

    /**
     * Destination vlan id.
     */
    @Property(name = "dst_vlan")
    private int destinationVlan;

    /**
     * Flow bandwidth.
     */
    @Property(name = "bandwidth")
    private long bandwidth;

    /**
     * Flow description.
     */
    @Property(name = "description")
    private String description;

    /**
     * Last updated timestamp.
     */
    @Property(name = "last_updated")
    private String lastUpdated;

    /**
     * Transit vlan id.
     */
    @Property(name = "transit_vlan")
    private int transitVlan;

    /**
     * Flow output vlan type.
     */
    @Property(name = "flow_type")
    private OutputVlanType flowType;

    /**
     * Internal Neo4j constructor.
     */
    private Flow() {
    }

    /**
     * Constructs entity.
     *
     * @param start             start relationship node entity
     * @param end               end relationship node entity
     * @param flowId            fow id
     * @param cookie            flow cookie
     * @param bandwidth         flow bandwidth
     * @param flowPath          flow path
     * @param sourceSwitch      source switch datapath id
     * @param sourcePort        source port number
     * @param sourceVlan        source vlan id
     * @param destinationSwitch destination switch datapath id
     * @param destinationPort   destination port number
     * @param destinationVlan   destination vlan id
     * @param description       flow description
     * @param lastUpdated       last updated timestamp
     * @param transitVlan       transit vlan id
     */
    public Flow(final Switch start, final Switch end,
                final String flowId, final long cookie, final List<String> flowPath, final long bandwidth,
                final String sourceSwitch, final int sourcePort, final int sourceVlan,
                final String destinationSwitch, final int destinationPort, final int destinationVlan,
                final String description, final String lastUpdated, final int transitVlan) {
        this.start = start;
        this.end = end;
        this.flowId = flowId;
        this.cookie = cookie;
        this.flowPath = flowPath;
        this.sourceSwitch = sourceSwitch;
        this.sourcePort = sourcePort;
        this.sourceVlan = sourceVlan;
        this.destinationSwitch = destinationSwitch;
        this.destinationPort = destinationPort;
        this.destinationVlan = destinationVlan;
        this.bandwidth = bandwidth;
        this.description = description;
        this.lastUpdated = lastUpdated;
        this.transitVlan = transitVlan;
        this.flowType = getFlowType(sourceVlan, destinationVlan);
    }

    /**
     * Identifies flow type.
     *
     * @param sourceVlanId      flow source vlan id
     * @param destinationVlanId flow source vlan id
     * @return flow type name
     */
    private static OutputVlanType getFlowType(final int sourceVlanId, final int destinationVlanId) {
        OutputVlanType flowType;

        if (sourceVlanId == 0 && destinationVlanId == 0) {
            flowType = OutputVlanType.NONE;
        } else if (sourceVlanId == 0) {
            flowType = OutputVlanType.PUSH;
        } else if (destinationVlanId == 0) {
            flowType = OutputVlanType.POP;
        } else {
            flowType = OutputVlanType.REPLACE;
        }

        return flowType;
    }

    /**
     * Returns output vlan type for reverse flow.
     *
     * @param directFlowType direct flow output vlan type
     * @return reverse flow output vlan type
     */
    private static OutputVlanType getReverseFlowType(final OutputVlanType directFlowType) {
        OutputVlanType reverseFlowType;
        switch (directFlowType) {
            case POP:
                reverseFlowType = OutputVlanType.PUSH;
                break;
            case PUSH:
                reverseFlowType = OutputVlanType.POP;
                break;
            case REPLACE:
            case NONE:
            default:
                reverseFlowType = directFlowType;
        }
        return reverseFlowType;
    }

    /**
     * Gets start relationship node entity.
     *
     * @return start relationship node entity
     */
    public Switch getStart() {
        return start;
    }

    /**
     * Gets end relationship node entity.
     *
     * @return end relationship node entity
     */
    public Switch getEnd() {
        return end;
    }

    /**
     * Gets source switch datapath id.
     *
     * @return source switch datapath id.
     */
    public String getSourceSwitch() {
        return sourceSwitch;
    }

    /**
     * Gets source port number.
     *
     * @return source port number.
     */
    public int getSourcePort() {
        return sourcePort;
    }

    /**
     * Gets destination switch datapath id.
     *
     * @return destination switch datapath id.
     */
    public String getDestinationSwitch() {
        return destinationSwitch;
    }

    /**
     * Gets destination port number.
     *
     * @return destination port number.
     */
    public int getDestinationPort() {
        return destinationPort;
    }

    /**
     * Gets flow path.
     *
     * @return flow path
     */
    public List<String> getFlowPath() {
        return flowPath;
    }

    /**
     * Sets flow path.
     *
     * @param flowPath flow path
     */
    public void setFlowPath(List<String> flowPath) {
        this.flowPath = flowPath;
    }

    /**
     * Gets flow id.
     *
     * @return fow id
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
     * Gets last updated timestamp.
     *
     * @return last updated timestamp
     */
    public String getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Sets last updated timestamp.
     *
     * @param lastUpdated last updated timestamp
     */
    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    /**
     * Gets source vlan id.
     *
     * @return source vlan id
     */
    public int getSourceVlan() {
        return sourceVlan;
    }

    /**
     * Sets source vlan id.
     *
     * @param sourceVlan source vlan id
     */
    public void setSourceVlan(int sourceVlan) {
        this.sourceVlan = sourceVlan;
    }

    /**
     * Gets destination vlan id.
     *
     * @return destination vlan id
     */
    public int getDestinationVlan() {
        return destinationVlan;
    }

    /**
     * Sets destination vlan id.
     *
     * @param destinationVlan destination vlan id
     */
    public void setDestinationVlan(int destinationVlan) {
        this.destinationVlan = destinationVlan;
    }

    /**
     * Gets transit vlan id.
     *
     * @return transit vlan id
     */
    public int getTransitVlan() {
        return transitVlan;
    }

    /**
     * Sets transit vlan id.
     *
     * @param transitVlan transit vlan id
     */
    public void setTransitVlan(int transitVlan) {
        this.transitVlan = transitVlan;
    }

    /**
     * Gets flow output vlan type.
     *
     * @return flow output vlan type
     */
    public OutputVlanType getFlowType() {
        return flowType;
    }

    /**
     * Sets flow output vlan type.
     *
     * @param flowType flow output vlan type
     */
    public void setFlowType(OutputVlanType flowType) {
        this.flowType = flowType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("start", start)
                .add("end", end)
                .add("flow_id", flowId)
                .add("cookie", cookie)
                .add("bandwidth", bandwidth)
                .add("flow_path", flowPath)
                .add("src_switch", sourceSwitch)
                .add("src_port", sourcePort)
                .add("src_vlan", sourceVlan)
                .add("dst_switch", destinationSwitch)
                .add("dst_port", destinationPort)
                .add("dst_vlan", destinationVlan)
                .add("description", description)
                .add("last_updated", lastUpdated)
                .add("transit_vlan", transitVlan)
                .add("flow_type", flowType)
                .toString();
    }

    /**
     * Returns set of installation commands.
     *
     * @param path          sorted path
     * @param correlationId correlation id
     * @return set of {@link CommandMessage} instances
     */
    public Set<CommandMessage> getInstallationCommands(final List<Isl> path, final String correlationId) {
        Set<CommandMessage> commands = new HashSet<>();
        Isl firstIsl = path.get(0);
        Isl lastIsl = path.get(path.size() - 1);
        if ((cookie & DIRECT_FLOW_COOKIE) == DIRECT_FLOW_COOKIE) {
            commands.add(new CommandMessage(new InstallIngressFlow(0L, flowId, cookie,
                    sourceSwitch, sourcePort, firstIsl.getSourcePort(), sourceVlan,
                    transitVlan, flowType, bandwidth, 0L), now(), correlationId, Destination.WFM));

            commands.add(new CommandMessage(new InstallEgressFlow(0L, flowId, cookie,
                    destinationSwitch, lastIsl.getDestinationPort(), destinationPort,
                    transitVlan, destinationVlan, flowType), now(), correlationId, Destination.WFM));
        } else {
            commands.add(new CommandMessage(new InstallIngressFlow(0L, flowId, cookie,
                    destinationSwitch, sourcePort, lastIsl.getDestinationPort(), destinationVlan,
                    transitVlan, getReverseFlowType(flowType), bandwidth, 0L), now(), correlationId, Destination.WFM));

            commands.add(new CommandMessage(new InstallEgressFlow(0L, flowId, cookie,
                    sourceSwitch, firstIsl.getSourcePort(), sourcePort,
                    transitVlan, sourceVlan, getReverseFlowType(flowType)), now(), correlationId, Destination.WFM));
        }

        for (int i = 0; i < path.size() - 1; i++) {
            Isl currentIsl = path.get(i);
            Isl nextIsl = path.get(i + 1);
            if ((cookie & DIRECT_FLOW_COOKIE) == DIRECT_FLOW_COOKIE) {
                commands.add(new CommandMessage(new InstallTransitFlow(0L, flowId, cookie,
                        currentIsl.getDestinationSwitch(), currentIsl.getDestinationPort(), nextIsl.getSourcePort(),
                        transitVlan), now(), correlationId, Destination.WFM));
            } else {
                commands.add(new CommandMessage(new InstallTransitFlow(0L, flowId, cookie,
                        currentIsl.getDestinationSwitch(), nextIsl.getSourcePort(), currentIsl.getDestinationPort(),
                        transitVlan), now(), correlationId, Destination.WFM));
            }
        }

        return commands;
    }

    /**
     * Returns set of deletion commands.
     *
     * @param correlationId correlation id
     * @return set of {@link CommandMessage} instances
     */
    public Set<CommandMessage> getDeletionCommands(final String correlationId) {
        Set<CommandMessage> commands = new HashSet<>();

        for (String sw : flowPath) {
            commands.add(new CommandMessage(new RemoveFlow(0L, flowId, cookie, sw, 0L),
                    System.currentTimeMillis(), correlationId, Destination.WFM));
        }

        return commands;
    }

    /**
     * Returns timestamp.
     *
     * @return current time
     */
    private long now() {
        return System.currentTimeMillis();
    }
}
