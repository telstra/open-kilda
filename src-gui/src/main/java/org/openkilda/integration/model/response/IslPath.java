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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"switch_id", "port_no", "seq_id", "segment_latency"})
public class IslPath {

    @JsonProperty("switch_id")
    private String switchId;
    @JsonProperty("port_no")
    private Integer portNo;
    @JsonProperty("seq_id")
    private Integer seqId;
    @JsonProperty("segment_latency")
    private Long segmentLatency;

    public String getSwitchId() {
        return switchId;
    }

    public void setSwitchId(final String switchId) {
        this.switchId = switchId;
    }

    public Integer getPortNo() {
        return portNo;
    }

    public void setPortNo(final Integer portNo) {
        this.portNo = portNo;
    }

    public Integer getSeqId() {
        return seqId;
    }

    public void setSeqId(final Integer seqId) {
        this.seqId = seqId;
    }

    public Long getSegmentLatency() {
        return segmentLatency;
    }

    public void setSegmentLatency(final Long segmentLatency) {
        this.segmentLatency = segmentLatency;
    }

    @Override
    public String toString() {
        return "IslPath [switchId=" + switchId + ", portNo=" + portNo + ", seqId=" + seqId
                + ", segmentLatency=" + segmentLatency + "]";
    }


}
