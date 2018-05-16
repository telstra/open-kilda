package org.openkilda.messaging.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.io.Serializable;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class DiscoveryLink implements Serializable {

    /** Never stop checking for an ISL */
    public final static int ENDLESS_ATTEMPTS = -1;

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

    /** Only increases if we get a response **/
    @JsonProperty("consecutive_failure")
    private int consecutiveFailure;

    @JsonProperty("consecutive_success")
    private int consecutiveSuccess;

    @JsonProperty("max_attempts")
    private int maxAttempts;

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

    public DiscoveryLink(String srcSwitch, int srcPort, String dstSwitch, int dstPort,
            int checkInterval, int maxAttempts) {
        this.srcEndpoint = new NetworkEndpoint(srcSwitch, srcPort);
        this.dstEndpoint = new NetworkEndpoint(dstSwitch, dstPort);
        this.timeCounter = 0;
        this.checkInterval = checkInterval;
        this.maxAttempts = maxAttempts;
        this.consecutiveFailure = 0;
        this.consecutiveSuccess = 0;
    }

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
            @JsonProperty("forlorn_еhreshold") final int maxAttempts) {
        this.srcEndpoint = new NetworkEndpoint(srcSwitch, srcPort);
        this.dstEndpoint = new NetworkEndpoint(dstSwitch, dstPort);
        this.attempts = attempts;
        this.timeCounter = timeCounter;
        this.checkInterval = checkInterval;
        this.maxAttempts = maxAttempts;
        this.consecutiveFailure = consecutiveFailure;
        this.consecutiveSuccess = consecutiveSuccess;
    }

    public void setDstEndpoint(NetworkEndpoint dstEndpoint) {
        this.dstEndpoint = dstEndpoint;
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

    public boolean isDiscovered() {
        return dstEndpoint != null;
    }

    public boolean isDestinationChanged(String dstSwitch, int dstPort) {
        // check if the link was previously not discovered
        if (this.dstEndpoint == null) {
            return false;
        }

        return !Objects.equals(this.dstEndpoint, new NetworkEndpoint(dstSwitch, dstPort));
    }

    public void resetDestination() {
        dstEndpoint = null;
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
        return Objects.equals(getSrcEndpoint(), that.getSrcEndpoint()) &&
                Objects.equals(getDstEndpoint(), that.getDstEndpoint());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSrcEndpoint(), getDstEndpoint());
    }

    @Override
    public String toString() {
        return "DiscoveryLink{" +
                "srcEndpoint=" + srcEndpoint +
                ", dstEndpoint=" + dstEndpoint +
                ", attempts=" + attempts +
                ", checkInterval=" + checkInterval +
                ", consecutiveFailure=" + consecutiveFailure +
                ", consecutiveSuccess=" + consecutiveSuccess +
                '}';
    }
}
