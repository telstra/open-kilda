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

import org.neo4j.ogm.annotation.EndNode;
import org.neo4j.ogm.annotation.GraphId;
import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.RelationshipEntity;
import org.neo4j.ogm.annotation.StartNode;

import java.io.Serializable;

/**
 * Isl Relationship Entity.
 */
@RelationshipEntity(type = "isl")
public class Isl implements Serializable {
    /**
     * Graph id.
     */
    @GraphId
    Long id;

    /**
     * Start node relationship entity.
     */
    @StartNode
    private Switch start;

    /**
     * End node relationship entity.
     */
    @EndNode
    private Switch end;

    /**
     * Source switch datapath id.
     * Indexed for search.
     */
    @Property(name = "src_switch")
    @Index
    private String sourceSwitch;

    /**
     * Source port number.
     * Indexed for search.
     */
    @Property(name = "src_port")
    @Index
    private int sourcePort;

    /**
     * Destination switch datapath id.
     * Indexed for search.
     */
    @Property(name = "dst_switch")
    @Index
    private String destinationSwitch;

    /**
     * Destination port number.
     * Indexed for search.
     */
    @Property(name = "dst_port")
    @Index
    private int destinationPort;

    /**
     * Isl latency.
     */
    @Property(name = "latency")
    private long latency;

    /**
     * Port speed.
     */
    @Property(name = "speed")
    private long speed;

    /**
     * Available bandwidth.
     */
    @Property(name = "bandwidth")
    private long bandwidth;

    /**
     * Internal Neo4j constructor.
     */
    private Isl() {
    }

    /**
     * Constructs entity.
     *
     * @param start             start relationship node entity
     * @param end               end relationship node entity
     * @param sourceSwitch      source switch datapath id
     * @param sourcePort        source port number
     * @param destinationSwitch destination switch datapath id
     * @param destinationPort   destination port number
     * @param latency           isl latency
     * @param speed             port speed
     * @param bandwidth         available bandwidth
     */
    public Isl(final Switch start,
               final Switch end,
               final String sourceSwitch,
               final int sourcePort,
               final String destinationSwitch,
               final int destinationPort,
               final long latency,
               final long speed,
               final long bandwidth) {
        this.start = start;
        this.end = end;
        this.sourceSwitch = sourceSwitch;
        this.sourcePort = sourcePort;
        this.destinationSwitch = destinationSwitch;
        this.destinationPort = destinationPort;
        this.latency = latency;
        this.speed = speed;
        this.bandwidth = bandwidth;
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
     * @return source switch datapath id
     */
    public String getSourceSwitch() {
        return sourceSwitch;
    }

    /**
     * Gets source port number.
     *
     * @return source port number
     */
    public int getSourcePort() {
        return sourcePort;
    }

    /**
     * Gets destination switch datapath id.
     *
     * @return destination switch datapath id
     */
    public String getDestinationSwitch() {
        return destinationSwitch;
    }

    /**
     * Gets destination port number.
     *
     * @return destination port number
     */
    public int getDestinationPort() {
        return destinationPort;
    }

    /**
     * Gets isl latency.
     *
     * @return isl latency
     */
    public long getLatency() {
        return latency;
    }

    /**
     * Sets isl latency.
     *
     * @param latency isl latency
     */
    public void setLatency(long latency) {
        this.latency = latency;
    }

    /**
     * Gets port speed.
     *
     * @return port speed
     */
    public long getSpeed() {
        return speed;
    }

    /**
     * Sets port speed.
     *
     * @param speed port speed
     */
    public void setSpeed(long speed) {
        this.speed = speed;
    }

    /**
     * Gets available bandwidth.
     *
     * @return available bandwidth
     */
    public long getBandwidth() {
        return bandwidth;
    }

    /**
     * Sets available bandwidth.
     *
     * @param bandwidth available bandwidth
     */
    public void setBandwidth(long bandwidth) {
        this.bandwidth = bandwidth;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("start", start)
                .add("end", end)
                .add("src_switch", sourceSwitch)
                .add("src_port", sourcePort)
                .add("dst_switch", destinationSwitch)
                .add("dst_port", destinationPort)
                .add("latency", latency)
                .add("speed", speed)
                .add("bandwidth", bandwidth)
                .toString();
    }
}
