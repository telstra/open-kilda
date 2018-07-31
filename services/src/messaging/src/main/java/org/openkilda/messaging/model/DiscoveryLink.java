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

    @JsonProperty("source")
    private NetworkEndpoint source;

    @JsonProperty("destination")
    private NetworkEndpoint destination;

    /**
     * How many attempts have we made, in other words defines how many discovery messages we have sent to speaker.
     */
    @JsonProperty("attempts")
    private int attempts;

    /**
     * Indicates how many discovery packets were sent by speaker to switches.
     */
    @JsonProperty("ack_attempts")
    private int ackAttempts;

    @JsonProperty("time_counter")
    private int timeCounter;

    @JsonProperty("check_interval")
    private int checkInterval;

    /**
     * Only increases if we do not get a response.
     */
    @JsonProperty("consecutive_failure")
    private int consecutiveFailure;

    @JsonProperty("consecutive_success")
    private int consecutiveSuccess;

    @JsonProperty("consecutive_failure_limit")
    private int consecutiveFailureLimit;

    @JsonProperty("active")
    private boolean active;

    /**
     * Constructor with non-defined destination of the ISL.
     */
    public DiscoveryLink(String srcSwitch, int srcPort, int checkInterval, int consecutiveFailureLimit) {
        this.source = new NetworkEndpoint(srcSwitch, srcPort);
        this.destination = null;
        this.timeCounter = 0;
        this.checkInterval = checkInterval;
        this.consecutiveFailureLimit = consecutiveFailureLimit;
        this.consecutiveFailure = 0;
        this.consecutiveSuccess = 0;
    }

    /**
     * Constructor with defined destination of the ISL.
     */
    public DiscoveryLink(String srcSwitch, int srcPort, String dstSwitch, int dstPort,
            int checkInterval, int consecutiveFailureLimit, boolean active) {
        this.source = new NetworkEndpoint(srcSwitch, srcPort);
        this.destination = new NetworkEndpoint(dstSwitch, dstPort);
        this.timeCounter = 0;
        this.checkInterval = checkInterval;
        this.consecutiveFailureLimit = consecutiveFailureLimit;
        this.consecutiveFailure = 0;
        this.consecutiveSuccess = 0;
        this.active = active;
    }

    /**
     * Main constructor for deserialization by jackson.
     */
    @JsonCreator
    public DiscoveryLink(@JsonProperty("source") final NetworkEndpoint source,
            @JsonProperty("destination") final NetworkEndpoint destination,
            @JsonProperty("attempts") final int attempts,
            @JsonProperty("ack_attempts") final int ackAttempts,
            @JsonProperty("time_counter") final int timeCounter,
            @JsonProperty("check_interval") final int checkInterval,
            @JsonProperty("consecutive_failure") final int consecutiveFailure,
            @JsonProperty("consecutive_success") final int consecutiveSuccess,
            @JsonProperty("consecutive_failure_limit") final int consecutiveFailureLimit,
            @JsonProperty("active") final boolean active) {
        this.source = source;
        this.destination = destination;
        this.attempts = attempts;
        this.ackAttempts = ackAttempts;
        this.timeCounter = timeCounter;
        this.checkInterval = checkInterval;
        this.consecutiveFailureLimit = consecutiveFailureLimit;
        this.consecutiveFailure = consecutiveFailure;
        this.consecutiveSuccess = consecutiveSuccess;
        this.active = active;
    }

    /**
     * Activate the ISL.
     */
    public void activate(NetworkEndpoint destination) {
        this.destination = destination;
        this.active = true;
    }

    /**
     * Deactivating of an ISL. We do not remove the destination of the ISL, because such data helps us to define
     * whether ISL is moved or not.
     */
    public void deactivate() {
        this.active = false;
        this.consecutiveFailure = 0;
        this.consecutiveSuccess = 0;
    }

    /**
     * Whereas renew is called when a successful Discovery is received, it isn't the place to
     * put "foundIsl". This is out of fear that renew() could be called from somewhere else. The
     * semantics of "renew" doesn't say "found ISL"
     */
    public void renew() {
        attempts = 0;
        ackAttempts = 0;
        timeCounter = 0;
    }

    /**
     * Checks if discovery should be suspended for that link or we can try to discover it.
     * @return true if link should be excluded from discovery plan and discovery packets should not be sent.
     */
    public boolean isNewAttemptAllowed() {
        if (consecutiveFailureLimit == ENDLESS_ATTEMPTS) { // never gonna give a link up.
            return true;
        }
        return consecutiveFailure < consecutiveFailureLimit;
    }

    public void clearConsecutiveFailure() {
        consecutiveFailure = 0;
    }

    public void clearConsecutiveSuccess() {
        consecutiveSuccess = 0;
    }

    public void fail() {
        consecutiveFailure++;
        active = false;
    }

    public void success() {
        consecutiveSuccess++;
    }

    public void tick() {
        timeCounter++;
    }

    public void resetTickCounter() {
        timeCounter = 0;
    }

    /**
     * Checks if limit of not acknowledged attempts is exceeded.
     * @return true if attempts is greater than attemptLimit.
     */
    public boolean isAttemptsLimitExceeded(int attemptsLimit) {
        return attempts > attemptsLimit;
    }

    /**
     * Increases amount of attempts.
     */
    public void incAttempts() {
        attempts++;
    }

    /**
     * Checks if we should stop trying to discover isl, because of the limit of attempts.
     * @return true if attempts is greater than attemptLimit.
     */
    public boolean isAckAttemptsLimitExceeded(int attemptsLimit) {
        return ackAttempts > attemptsLimit;
    }

    /**
     * Increases amount of acknowledged attempts.
     */
    public void incAcknowledgedAttempts() {
        ackAttempts++;
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
        if (this.destination == null) {
            return false;
        }

        return !Objects.equals(this.destination, new NetworkEndpoint(dstSwitch, dstPort));
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
        return Objects.equals(getSource(), that.getSource())
                && Objects.equals(getDestination(), that.getDestination());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSource(), getDestination());
    }

}
