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

package org.openkilda.messaging.info.stats;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * TODO: add javadoc.
 */
public class PortStatsEntry implements Serializable {

    @JsonProperty
    private int portNo;

    @JsonProperty
    private long rxPackets;

    @JsonProperty
    private long txPackets;

    @JsonProperty
    private long rxBytes;

    @JsonProperty
    private long txBytes;

    @JsonProperty
    private long rxDropped;

    @JsonProperty
    private long txDropped;

    @JsonProperty
    private long rxErrors;

    @JsonProperty
    private long txErrors;

    @JsonProperty
    private long rxFrameErr;

    @JsonProperty
    private long rxOverErr;

    @JsonProperty
    private long rxCrcErr;

    @JsonProperty
    private long collisions;

    @JsonCreator
    public PortStatsEntry(@JsonProperty("portNo") int portNo,
                          @JsonProperty("rxPackets") long rxPackets,
                          @JsonProperty("txPackets") long txPackets,
                          @JsonProperty("rxBytes") long rxBytes,
                          @JsonProperty("txBytes") long txBytes,
                          @JsonProperty("rxDropped") long rxDropped,
                          @JsonProperty("txDropped") long txDropped,
                          @JsonProperty("rxErrors") long rxErrors,
                          @JsonProperty("txErrors") long txErrors,
                          @JsonProperty("rxFrameErr") long rxFrameErr,
                          @JsonProperty("rxOverErr") long rxOverErr,
                          @JsonProperty("rxCrcErr") long rxCrcErr,
                          @JsonProperty("collisions") long collisions) {
        this.portNo = portNo;
        this.rxPackets = rxPackets;
        this.txPackets = txPackets;
        this.rxBytes = rxBytes;
        this.txBytes = txBytes;
        this.rxDropped = rxDropped;
        this.txDropped = txDropped;
        this.rxErrors = rxErrors;
        this.txErrors = txErrors;
        this.rxFrameErr = rxFrameErr;
        this.rxOverErr = rxOverErr;
        this.rxCrcErr = rxCrcErr;
        this.collisions = collisions;
    }

    public int getPortNo() {
        return portNo;
    }

    public long getRxPackets() {
        return rxPackets;
    }

    public long getTxPackets() {
        return txPackets;
    }

    public long getRxBytes() {
        return rxBytes;
    }

    public long getTxBytes() {
        return txBytes;
    }

    public long getRxDropped() {
        return rxDropped;
    }

    public long getTxDropped() {
        return txDropped;
    }

    public long getRxErrors() {
        return rxErrors;
    }

    public long getTxErrors() {
        return txErrors;
    }

    public long getRxFrameErr() {
        return rxFrameErr;
    }

    public long getRxOverErr() {
        return rxOverErr;
    }

    public long getRxCrcErr() {
        return rxCrcErr;
    }

    public long getCollisions() {
        return collisions;
    }
}
