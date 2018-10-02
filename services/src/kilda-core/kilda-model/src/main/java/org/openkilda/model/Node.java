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

package org.openkilda.model;

import lombok.Value;

import java.util.Objects;

/**
 * Defines the payload payload of a Message representing a path node info.
 */
@Value
public class Node {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Switch id.
     */
    private SwitchId switchId;

    /**
     * Port number.
     */
    private int portNo;

    /**
     * Sequence id.
     */
    private int seqId;

    /**
     * Segment latency.
     */
    private Long segmentLatency;

    /**
     * If a per segment cookie is set.
     */
    private Long cookie;

    /**
     * Instance creator.
     *
     * @param switchId       switch id
     * @param portNo         port number
     * @param seqId          sequence id
     * @param segmentLatency segment latency
     */
    public Node(SwitchId switchId, int portNo, int seqId, final Long cookie, Long segmentLatency) {
        this.switchId = switchId;
        this.portNo = portNo;
        this.seqId = seqId;
        this.cookie = cookie;
        this.segmentLatency = segmentLatency;
    }

    /**
     * Instance creator without segment latency value.
     *
     * @param switchId switch id
     * @param portNo   port number
     * @param seqId    sequence id
     */
    public Node(final SwitchId switchId, final int portNo, final int seqId) {
        this.switchId = switchId;
        this.portNo = portNo;
        this.seqId = seqId;
        this.segmentLatency = null;
        this.cookie = null;
    }

    /**
     * Returns switch id.
     *
     * @return switch id
     */
    public SwitchId getSwitchId() {
        return switchId;
    }

    /**
     * Returns port number.
     *
     * @return port number
     */
    public int getPortNo() {
        return portNo;
    }

    /**
     * Returns sequence id.
     *
     * @return sequence id
     */
    public int getSeqId() {
        return seqId;
    }

    /**
     * Returns segment latency.
     *
     * @return segment latency
     */
    public Long getSegLatency() {
        return segmentLatency;
    }


    public Long getCookie() {
        return cookie;
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

        Node that = (Node) object;
        return Objects.equals(getSwitchId(), that.getSwitchId())
                && Objects.equals(getPortNo(), that.getPortNo());
    }
}
