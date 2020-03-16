/* Copyright 2018 Telstra Open Source
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

package org.openkilda.integration.model.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;

/**
 * The Class Path.
 *
 * @author Gaurav Chugh
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"port_no", "segment_latency", "seq_id", "switch_id"})
public class PathNode implements Serializable, Comparable<PathNode> {

    @JsonProperty("port_no")
    private Integer portNo;

    @JsonProperty("segment_latency")
    private Integer segmentLatency;

    @JsonProperty("seq_id")
    private Integer seqId;

    @JsonProperty("switch_id")
    private String switchId;

    @JsonProperty("switch_name")
    private String switchName;

    @JsonProperty("in_port_no")
    private Integer inPortNo;

    @JsonProperty("out_port_no")
    private Integer outPortNo;

    @JsonCreator
    public PathNode() {

    }

    /**
     * Instantiates a new path node.
     *
     * @param seqId the seq id
     * @param inPortNo the in port no
     * @param outPortNo the out port no
     * @param switchId the switch id
     * @param switchName the switch name
     */
    @JsonCreator
    public PathNode(@JsonProperty("seq_id") Integer seqId,
            @JsonProperty("in_port_no") Integer inPortNo,
            @JsonProperty("out_port_no") Integer outPortNo,
            @JsonProperty("switch_id") String switchId,
            @JsonProperty("switch_name") String switchName) {
        setSeqId(seqId);
        setInPortNo(inPortNo);
        setOutPortNo(outPortNo);
        setSwitchId(switchId);
        setSwitchName(switchName);
    }

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = -4515006227265225751L;

    /**
     * Gets the port no.
     *
     * @return the port no
     */
    public Integer getPortNo() {
        return portNo;
    }

    /**
     * Sets the port no.
     *
     * @param portNo the new port no
     */
    public void setPortNo(final Integer portNo) {
        this.portNo = portNo;
    }

    /**
     * Gets the segment latency.
     *
     * @return the segment latency
     */
    public Integer getSegmentLatency() {
        return segmentLatency;
    }

    /**
     * Sets the segment latency.
     *
     * @param segmentLatency the new segment latency
     */
    public void setSegmentLatency(final Integer segmentLatency) {
        this.segmentLatency = segmentLatency;
    }

    /**
     * Gets the seq id.
     *
     * @return the seq id
     */
    public Integer getSeqId() {
        return seqId;
    }

    /**
     * Sets the seq id.
     *
     * @param seqId the new seq id
     */
    public void setSeqId(final Integer seqId) {
        this.seqId = seqId;
    }

    /**
     * Gets the switch id.
     *
     * @return the switch id
     */
    public String getSwitchId() {
        return switchId;
    }

    /**
     * Sets the switch id.
     *
     * @param switchId the new switch id
     */
    public void setSwitchId(final String switchId) {
        this.switchId = switchId;
    }

    /**
     * Gets the in port no.
     *
     * @return the in port no
     */
    public Integer getInPortNo() {
        return inPortNo;
    }

    /**
     * Sets the in port no.
     *
     * @param inPortNo the new in port no
     */
    public void setInPortNo(final Integer inPortNo) {
        this.inPortNo = inPortNo;
    }

    /**
     * Gets the out port no.
     *
     * @return the out port no
     */
    public Integer getOutPortNo() {
        return outPortNo;
    }

    /**
     * Sets the out port no.
     *
     * @param outPortNo the new out port no
     */
    public void setOutPortNo(final Integer outPortNo) {
        this.outPortNo = outPortNo;
    }

    /**
     * Gets the switch name.
     *
     * @return the switch name
     */
    public String getSwitchName() {
        return switchName;
    }

    /**
     * Sets the switch name.
     *
     * @param switchName the new switch name
     */
    public void setSwitchName(String switchName) {
        this.switchName = switchName;
    }

    @Override
    public String toString() {
        return "PathNode [portNo=" + portNo + ", segmentLatency=" + segmentLatency + ", seqId="
                + seqId + ", switchId=" + switchId + ", switchName=" + switchName + ", inPortNo="
                + inPortNo + ", outPortNo=" + outPortNo + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((seqId == null) ? 0 : seqId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        PathNode other = (PathNode) obj;
        if (seqId == null) {
            if (other.seqId != null) {
                return false;
            }
        } else if (!seqId.equals(other.seqId)) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(PathNode obj) {
        final int before = -1;
        final int equal = 0;
        final int after = 1;
        return this.equals(obj) ? equal : this.seqId < obj.seqId ? before : after;
    }



}
