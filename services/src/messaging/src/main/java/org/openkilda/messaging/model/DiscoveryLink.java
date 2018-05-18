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

package org.openkilda.messaging.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@ToString
public class DiscoveryLink implements Serializable {

    /**
     * Means to never stop to check even failed ISL.
     */
    public static final int ENDLESS_ATTEMPTS = -1;

    @JsonProperty("src_switch")
    private NetworkEndpoint srcEndpoint;

    @JsonProperty("dst_switch")
    private NetworkEndpoint dstEndpoint;

    /** How many attempts have we made .. will fail after X attempts and no response */
    @JsonProperty("attempts")
    private int attempts;

    @JsonProperty("time_counter")
    private int timeCounter;

    @JsonProperty("check_interval")
    private int checkInterval;

    /**
     * Only increases if we get a response.
     */
    @JsonProperty("consecutive_failure")
    private int consecutiveFailure;

    @JsonProperty("consecutive_success")
    private int consecutiveSuccess;

    @JsonProperty("max_attempts")
    private int maxAttempts;

    @JsonProperty("active")
    private boolean active;

    /**
     * TODO: forlornThreshold is understandable (ie point at which to stop checking), but regarding
     * method signatures, it is very similar to DiscoverManager, which uses consecutive failure
     * limit, which is a different concept compared to forlorn.
     */
    public DiscoveryLink(String srcSwitch, int srcPort, int checkInterval, int maxAttempts) {
        this.srcEndpoint = new NetworkEndpoint(srcSwitch, srcPort);
        this.dstEndpoint = null;
        this.timeCounter = 0;
        this.checkInterval = checkInterval;
        this.maxAttempts = maxAttempts;
        this.consecutiveFailure = 0;
        this.consecutiveSuccess = 0;
    }

    /**
     * Constructor.
     */
    public DiscoveryLink(String srcSwitch, int srcPort, String dstSwitch, int dstPort,
            int checkInterval, int maxAttempts, boolean active) {
        this.srcEndpoint = new NetworkEndpoint(srcSwitch, srcPort);
        this.dstEndpoint = new NetworkEndpoint(dstSwitch, dstPort);
        this.timeCounter = 0;
        this.checkInterval = checkInterval;
        this.maxAttempts = maxAttempts;
        this.consecutiveFailure = 0;
        this.consecutiveSuccess = 0;
        this.active = active;
    }

    /**
     * Main constructor using for deserialization by jackson.
     */
    @JsonCreator
    public DiscoveryLink(@JsonProperty("src_switch") final String srcSwitch,
            @JsonProperty("src_port") final int srcPort,
            @JsonProperty("dst_switch") final String dstSwitch,
            @JsonProperty("dst_port") final int dstPort,
            @JsonProperty("attempts") final int attempts,
            @JsonProperty("time_counter") final int timeCounter,
            @JsonProperty("check_interval") final int checkInterval,
            @JsonProperty("consecutive_failure") final int consecutiveFailure,
            @JsonProperty("consecutive_success") final int consecutiveSuccess,
            @JsonProperty("forlorn_еhreshold") final int maxAttempts,
            @JsonProperty("active") final boolean active) {
        this.srcEndpoint = new NetworkEndpoint(srcSwitch, srcPort);
        this.dstEndpoint = new NetworkEndpoint(dstSwitch, dstPort);
        this.attempts = attempts;
        this.timeCounter = timeCounter;
        this.checkInterval = checkInterval;
        this.maxAttempts = maxAttempts;
        this.consecutiveFailure = consecutiveFailure;
        this.consecutiveSuccess = consecutiveSuccess;
        this.active = active;
    }

    /**
     * Is being used when ISL is discovered and we know what the destination of the link.
     */
    public void activate(NetworkEndpoint dstEndpoint) {
        this.dstEndpoint = dstEndpoint;
        this.active = true;
    }

    /**
     * Deactivating of an ISL. We do not remove the destination of the ISL, because such data helps us to define
     * whether ISL is moved or not.
     */
    public void deactivate() {
        this.active = false;
        this.consecutiveFailure = 0;
    }

    /**
     * Whereas renew is called when a successful Discovery is received, it isn't the place to
     * put "foundIsl". This is out of fear that renew() could be called from somewhere else. The
     * semantics of "renew" doesn't say "found ISL"
     */
    public void renew() {
        attempts = 0;
        timeCounter = 0;
    }

    /**
     * Checks if ISL should be excluded from discovery.
     * @return true if link should be excluded from discovery plan and discovery packets should not be sent.
     */
    public boolean isExcludedFromDiscovery() {
        if (maxAttempts == ENDLESS_ATTEMPTS) { // never gonna give a link up.
            return false;
        }
        return consecutiveFailure > maxAttempts;
    }

    public void clearConsecutiveFailure() {
        consecutiveFailure = 0;
    }

    public void clearConsecutiveSuccess() {
        consecutiveSuccess = 0;
    }

    public void incConsecutiveFailure() {
        consecutiveFailure++;
    }

    public void incConsecutiveSuccess() {
        consecutiveSuccess++;
    }

    public void incTick() {
        timeCounter++;
    }

    public void resetTickCounter() {
        timeCounter = 0;
    }

    /**
     * Check if we should stop to verify ISL.
     * @return true if attempts is greater than attemptLimit.
     */
    public boolean maxAttempts(Integer attemptLimit) {
        return attemptLimit < attempts;
    }

    public void incAttempts() {
        attempts++;
    }

    public boolean timeToCheck() {
        return timeCounter >= checkInterval;
    }

    /**
     * Checks whether destination switch/port of that link differs.
     * @param dstSwitch destination switch.
     * @param dstPort destination port.
     * @return true if destination changed.
     */
    public boolean isDestinationChanged(String dstSwitch, int dstPort) {
        // check if the link was previously not discovered
        if (this.dstEndpoint == null) {
            return false;
        }

        return !Objects.equals(this.dstEndpoint, new NetworkEndpoint(dstSwitch, dstPort));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DiscoveryLink)) {
            return false;
        }
        DiscoveryLink that = (DiscoveryLink) o;
        return Objects.equals(getSrcEndpoint(), that.getSrcEndpoint())
                && Objects.equals(getDstEndpoint(), that.getDstEndpoint());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSrcEndpoint(), getDstEndpoint());
    }

}
