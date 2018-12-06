package org.openkilda.messaging.model;

import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.ToString;

import java.io.Serializable;
import java.util.Objects;

@JsonSerialize
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ToString(callSuper = true)
public class DiscoveryNode implements Serializable {

    // Never stop checking for an ISL.
    public static final int FORLORN_NEVER = -1;

    @JsonProperty("switch_id")
    private final SwitchId switchId;

    @JsonProperty("port_id")
    private final String portId;

    /** How many attempts have we made .. will fail after X attempts and no response */
    @JsonProperty("attempts")
    private int attempts;

    @JsonProperty("time_counter")
    private int timeCounter;

    @JsonProperty("check_interval")
    private int checkInterval;

    // Only increases if we get a response.
    @JsonProperty("consecutive_failure")
    private int consecutiveFailure;

    @JsonProperty("consecutive_success")
    private int consecutiveSuccess;

    @JsonProperty("forlorn_threshold")
    private int forlornThreshold;
    /**
     * We'll use this flag to identify ports that have successfully found an ISL.
     * It can be used to track the state of a link:
     *  - If foundIsl is false, and we find one, then send update to TE
     *  - If foundIsl is false, and we get failures, don't send anything to TE
     *  - If foundIsl is true, and we get success, and timeCounter > 0, then update TE
     *  - If foundIsl is true, and we get failure, and timeCounter = 0, then update TE
     *<p/>
     *  TODO: we should consider adding a policy to determine how/when to notify TE upon ISL Failure.
     *          We've discussed something like "on 3rd failure", but should look at flapping history, etc.
     *          At Present, before "update TE on state change" added, we notifief TE of each event.
     * <p/>
     * To enable richer business rules (flapping), probably should include some history about counts.
     * To be clear, this class isn't where the business logic / policy goes; it is just the
     * holder of information.
     */

    @JsonProperty("found_isl")
    private boolean foundIsl;

    /**
     * TODO: forlornThreshold is understandable (ie point at which to stop checking), but regarding
     * method signatures, it is very similar to DiscoverManager, which uses consecutive failure
     * limit, which is a different concept compared to forlorn.
     */
    public DiscoveryNode(SwitchId switchId, String portId, int checkInterval, int forlornThreshold) {
        this.switchId = switchId;
        this.portId = portId;
        this.timeCounter = 0;
        this.checkInterval = checkInterval;
        this.forlornThreshold = forlornThreshold;
        this.consecutiveFailure = 0;
        this.consecutiveSuccess = 0;
        this.foundIsl = false;
    }

    @JsonCreator
    public DiscoveryNode(@JsonProperty("switch_id") final SwitchId switchId,
            @JsonProperty("port_id") final String portId,
            @JsonProperty("attempts") final int attempts,
            @JsonProperty("time_counter") final int timeCounter,
            @JsonProperty("check_interval") final int checkInterval,
            @JsonProperty("consecutive_failure") final int consecutiveFailure,
            @JsonProperty("consecutive_success") final int consecutiveSuccess,
            @JsonProperty("forlorn_threshold") final int forlornThreshold,
            @JsonProperty("found_isl") final boolean foundIsl) {

        this.switchId = switchId;
        this.portId = portId;
        this.attempts = attempts;
        this.timeCounter = timeCounter;
        this.checkInterval = checkInterval;
        this.forlornThreshold = forlornThreshold;
        this.consecutiveFailure = consecutiveFailure;
        this.consecutiveSuccess = consecutiveSuccess;
        this.foundIsl = foundIsl;
    }

    public void setFoundIsl(boolean foundIsl) {
        this.foundIsl = foundIsl;
    }

    public boolean isFoundIsl() {
        return this.foundIsl;
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
     * Check whether consecutiveFailure is greater than limit.
     *
     * @return true if consecutiveFailure is greater than limit
     */
    public boolean forlorn() {
        if (forlornThreshold == FORLORN_NEVER) { // never gonna give a link up.
            return false;
        }
        return consecutiveFailure > forlornThreshold;
    }

    public void clearConsecutiveFailure() {
        consecutiveFailure = 0;
    }

    public void clearConsecutiveSuccess() {
        consecutiveSuccess = 0;
    }

    public int getConsecutiveFailure() {
        return this.consecutiveFailure;
    }

    public int getConsecutiveSuccess() {
        return this.consecutiveSuccess;
    }

    public void incConsecutiveFailure() {
        consecutiveFailure++;
    }

    public void incConsecutiveSuccess() {
        consecutiveSuccess++;
    }

    public int getTicks() {
        return timeCounter;
    }

    public void incTick() {
        timeCounter++;
    }

    public void resetTickCounter() {
        timeCounter = 0;
    }

    /**
     * Check whether attempts is greater than attemptLimit.
     *
     * @param attemptLimit the limit to test against
     * @return true if attempts is greater than attemptLimit.
     */
    public boolean maxAttempts(Integer attemptLimit) {
        return (attemptLimit < attempts);
    }

    public void incAttempts() {
        attempts++;
    }

    public int getAttempts() {
        return attempts;
    }

    public boolean timeToCheck() {
        return timeCounter >= checkInterval;
    }

    public SwitchId getSwitchId() {
        return switchId;
    }

    public String getPortId() {
        return portId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DiscoveryNode)) {
            return false;
        }
        DiscoveryNode that = (DiscoveryNode) o;
        return Objects.equals(getSwitchId(), that.getSwitchId()) && Objects.equals(getPortId(), that.getPortId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSwitchId(), getPortId());
    }
}
