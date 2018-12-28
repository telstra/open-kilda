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

package org.openkilda.messaging.info.event;

import static com.google.common.base.MoreObjects.toStringHelper;

import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;

/**
 * Defines the payload payload of a Message representing a path node info.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PathNode implements Serializable {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Switch id.
     */
    @JsonProperty("switch_id")
    private SwitchId switchId;

    /**
     * Port number.
     */
    @JsonProperty("port_no")
    private int portNo;

    /**
     * Sequence id.
     */
    @JsonProperty("seq_id")
    private int seqId;

    /**
     * Segment latency.
     */
    @JsonProperty("segment_latency")
    private Long segmentLatency;

    /**
     * If a per segment cookie is set.
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) // Needed to exclude when not set
    @JsonProperty("cookie")
    private Long cookie;

    /**
     * Default constructor.
     */
    public PathNode() {
    }

    /**
     * Copy constructor.
     *
     * @param that {@link PathNode} instance
     */
    public PathNode(PathNode that) {
        this.seqId = that.seqId;
        this.portNo = that.portNo;
        this.switchId = that.switchId;
        this.segmentLatency = that.segmentLatency;
        this.cookie = that.cookie;
    }

    /**
     * Instance creator without segment latency value.
     *
     * @param switchId switch id
     * @param portNo   port number
     * @param seqId    sequence id
     */
    public PathNode(final SwitchId switchId, final int portNo, final int seqId) {
        this.switchId = switchId;
        this.portNo = portNo;
        this.seqId = seqId;
    }

    public PathNode(final SwitchId switchId, final int portNo, final int seqId, final Long segmentLatency) {
        this.switchId = switchId;
        this.portNo = portNo;
        this.seqId = seqId;
        this.segmentLatency = segmentLatency;
        this.cookie = null;
    }

    /**
     * Instance creator.
     *
     * @param switchId       switch id
     * @param portNo         port number
     * @param seqId          sequence id
     * @param segmentLatency segment latency
     */
    @JsonCreator
    public PathNode(@JsonProperty("switch_id") final SwitchId switchId,
                    @JsonProperty("port_no") final int portNo,
                    @JsonProperty("seq_id") final int seqId,
                    @JsonProperty("cookie") final Long cookie,
                    @JsonProperty("segment_latency") final Long segmentLatency) {
        this.switchId = switchId;
        this.portNo = portNo;
        this.seqId = seqId;
        this.cookie = cookie;
        this.segmentLatency = segmentLatency;
    }

    /**
     * Returns switch id.
     *
     * @return switch id
     */
    @JsonProperty("switch_id")
    public SwitchId getSwitchId() {
        return switchId;
    }

    /**
     * Sets switch id.
     *
     * @param switchId switch id to set
     */
    @JsonProperty("switch_id")
    public void setSwitchId(final SwitchId switchId) {
        this.switchId = switchId;
    }

    /**
     * Returns port number.
     *
     * @return port number
     */
    @JsonProperty("port_no")
    public int getPortNo() {
        return portNo;
    }

    /**
     * Sets port number.
     *
     * @param portNo port number to set
     */
    @JsonProperty("port_no")
    public void setPortNo(final int portNo) {
        this.portNo = portNo;
    }

    /**
     * Returns sequence id.
     *
     * @return sequence id
     */
    @JsonProperty("seq_id")
    public int getSeqId() {
        return seqId;
    }

    /**
     * Sets sequence id.
     *
     * @param seqId sequence id to set
     */
    @JsonProperty("seq_id")
    public void setSeqId(final int seqId) {
        this.seqId = seqId;
    }

    /**
     * Returns segment latency.
     *
     * @return segment latency
     */
    @JsonProperty("segment_latency")
    public Long getSegLatency() {
        return segmentLatency;
    }

    /**
     * Sets segment latency.
     *
     * @param latency segment latency to set
     */
    @JsonProperty("segment_latency")
    public void setSegLatency(final long latency) {
        this.segmentLatency = latency;
    }


    @JsonProperty("cookie")
    public Long getCookie() {
        return cookie;
    }

    @JsonProperty("cookie")
    public void setCookie(Long cookie) {
        this.cookie = cookie;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("switch_id", switchId)
                .add("port_no", portNo)
                .add("seq_id", seqId)
                .add("segment_latency", segmentLatency)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(switchId, portNo);
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

        PathNode that = (PathNode) object;
        return Objects.equals(getSwitchId(), that.getSwitchId())
                && Objects.equals(getPortNo(), that.getPortNo());
    }
}
