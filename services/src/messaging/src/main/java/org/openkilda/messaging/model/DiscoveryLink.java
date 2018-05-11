package org.openkilda.messaging.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.Objects;

@JsonSerialize
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiscoveryLink implements Serializable {

    /** Never stop checking for an ISL */
    public final static int FORLORN_NEVER = -1;

    @JsonProperty("src_switch")
    private final String srcSwitch;

    @JsonProperty("src_port")
    private final int srcPort;

    @JsonProperty("dst_switch")
    private String dstSwitch;

    @JsonProperty("dst_port")
    private int dstPort;

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

    @JsonProperty("forlorn_threshold")
    private int forlornThreshold;

    /**
     * TODO: forlornThreshold is understandable (ie point at which to stop checking), but regarding
     * method signatures, it is very similar to DiscoverManager, which uses consecutive failure
     * limit, which is a different concept compared to forlorn.
     */
    public DiscoveryLink(String srcSwitch, int srcPort, int checkInterval, int forlornThreshold) {
        this.srcSwitch = srcSwitch;
        this.srcPort = srcPort;
        this.dstSwitch = null;
        this.dstPort = 0;
        this.timeCounter = 0;
        this.checkInterval = checkInterval;
        this.forlornThreshold = forlornThreshold;
        this.consecutiveFailure = 0;
        this.consecutiveSuccess = 0;
    }

    public DiscoveryLink(String srcSwitch, int srcPort, String dstSwitch, int dstPort,
            int checkInterval, int forlornThreshold) {
        this.srcSwitch = srcSwitch;
        this.srcPort = srcPort;
        this.dstSwitch = dstSwitch;
        this.dstPort = dstPort;
        this.timeCounter = 0;
        this.checkInterval = checkInterval;
        this.forlornThreshold = forlornThreshold;
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
            @JsonProperty("forlorn_threshold") final int forlornThreshold) {
        this.srcSwitch = srcSwitch;
        this.srcPort = srcPort;
        this.dstSwitch = dstSwitch;
        this.dstPort = dstPort;
        this.attempts = attempts;
        this.timeCounter = timeCounter;
        this.checkInterval = checkInterval;
        this.forlornThreshold = forlornThreshold;
        this.consecutiveFailure = consecutiveFailure;
        this.consecutiveSuccess = consecutiveSuccess;
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

    public int getConsecutiveFailure(){
        return this.consecutiveFailure;
    }

    public int getConsecutiveSuccess(){
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

    public String getSrcSwitch() {
        return srcSwitch;
    }

    public int getSrcPort() {
        return srcPort;
    }

    public String getDstSwitch() {
        return dstSwitch;
    }

    public void setDstSwitch(String dstSwitch) {
        this.dstSwitch = dstSwitch;
    }

    public int getDstPort() {
        return dstPort;
    }

    public void setDstPort(int dstPort) {
        this.dstPort = dstPort;
    }

    public boolean isDiscovered() {
        return StringUtils.isNotEmpty(dstSwitch) && dstPort != 0;
    }

    public boolean isDestinationChanged(String dstSwitch, int dstPort) {
        return !StringUtils.equals(this.dstSwitch, dstSwitch) || this.dstPort != dstPort;
    }

    public void resetDestination() {
        dstSwitch = null;
        dstPort = 0;
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
        return Objects.equals(getSrcSwitch(), that.getSrcSwitch()) &&
                Objects.equals(getSrcPort(), that.getSrcPort()) &&
                Objects.equals(getDstSwitch(), that.getDstSwitch()) &&
                Objects.equals(getDstPort(), that.getDstPort());
    }

    @Override
    public int hashCode() {
        return Objects.hash((getSrcSwitch()), getSrcPort(), getDstSwitch(), getDstPort());
    }

    @Override
    public String toString() {
        return "DiscoveryLink{" +
                "srcSwitch='" + srcSwitch + '\'' +
                ", srcPort=" + srcPort +
                ", dstSwitch='" + dstSwitch + '\'' +
                ", dstPort=" + dstPort +
                ", attempts=" + attempts +
                ", consecutiveFailure=" + consecutiveFailure +
                ", consecutiveSuccess=" + consecutiveSuccess +
                '}';
    }
}
