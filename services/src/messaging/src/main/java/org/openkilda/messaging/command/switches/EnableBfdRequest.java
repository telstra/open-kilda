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

package org.openkilda.messaging.command.switches;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class EnableBfdRequest extends CommandData {
    @JsonProperty("src_switch")
    private String srcSw;
    @JsonProperty("dst_switch")
    private String dstSw;
    @JsonProperty("interval")
    private int interval;
    @JsonProperty("keep_alive_timeout")
    private int keepAliveTimeout;
    @JsonProperty("multiplier")
    private int multiplier;
    @JsonProperty("discriminator")
    private int discriminator;
    @JsonProperty("src_port")
    private int srcPort;

    @JsonCreator
    public EnableBfdRequest(@JsonProperty("src_switch") String srcSw,
                            @JsonProperty("dst_switch") String dstSw,
                            @JsonProperty("interval") int interval,
                            @JsonProperty("keep_alive_timeout") int keepAliveTimeout,
                            @JsonProperty("multiplier") int multiplier,
                            @JsonProperty("discriminator") int discriminator,
                            @JsonProperty("src_port") int srcPort) {

        if (!Utils.validateSwitchId(srcSw)) {
            throw new IllegalArgumentException("srcSw has invalid value");
        }
        this.srcSw = srcSw;
        if (!Utils.validateSwitchId(dstSw)) {
            throw new IllegalArgumentException("dstSw has invalid value");
        }
        this.dstSw = dstSw;
        this.interval = interval;
        this.keepAliveTimeout = keepAliveTimeout;
        this.multiplier = multiplier;
        this.discriminator = discriminator;
        this.srcPort = srcPort;
    }

    public String getSrcSw() {
        return srcSw;
    }

    public void setSrcSw(String srcSw) {
        this.srcSw = srcSw;
    }

    public String getDstSw() {
        return dstSw;
    }

    public void setDstSw(String dstSw) {
        this.dstSw = dstSw;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public int getKeepAliveTimeout() {
        return keepAliveTimeout;
    }

    public void setKeepAliveTimeout(int keepAliveTimeout) {
        this.keepAliveTimeout = keepAliveTimeout;
    }

    public int getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(int multiplier) {
        this.multiplier = multiplier;
    }

    public int getDiscriminator() {
        return discriminator;
    }

    public void setDiscriminator(int discriminator) {
        this.discriminator = discriminator;
    }

    public int getSrcPort() {
        return srcPort;
    }

    public void setSrcPort(int srcPort) {
        this.srcPort = srcPort;
    }

    @Override
    public String toString() {
        return "EnableBfdRequest{"
                + "srcSw='" + srcSw + '\''
                + ", dstSw='" + dstSw + '\''
                + ", interval=" + interval
                + ", keepAliveTimeout=" + keepAliveTimeout
                + ", multiplier=" + multiplier
                + ", discriminator=" + discriminator
                + ", srcPort=" + srcPort
                + ", timestamp=" + timestamp
                + '}';
    }
}
