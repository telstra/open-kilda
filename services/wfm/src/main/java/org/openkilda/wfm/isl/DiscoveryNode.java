package org.openkilda.wfm.isl;

import java.io.Serializable;
import java.util.Objects;

public class DiscoveryNode implements Serializable {
    // FIXME(surabujin): ... is there any better way to define this value?
    private final static int NEVER = 999999;
    private final String switchId;
    private final String portId;
    /** How many attempts have we made .. will fail after X attempts and no response */
    private int attempts;
    private int timeCounter;
    private int checkInterval;
    /** Only increases if we get a response **/
    private int consecutiveFailure;
    private int consecutiveSuccess;
    private int forlornThreshold;
    /**
     * We'll use this flag to identify ports that have successfully found an ISL.
     * It can be used to track the state of a link:
     *  - If foundIsl is false, and we find one, then send update to TE
     *  - If foundIsl is false, and we get failures, don't send anything to TE
     *  - If foundIsl is true, and we get success, and timeCounter > 0, then update TE
     *  - If foundIsl is true, and we get failure, and timeCounter = 0, then update TE
     *
     *  TODO: we should consider adding a policy to determine how/when to notify TE upon ISL Failure.
     *          We've discussed something like "on 3rd failure", but should look at flapping history, etc.
     *          At Present, before "update TE on state change" added, we notifief TE of each event.
     *
     * To enable richer business rules (flapping), probably should include some history about counts.
     * To be clear, this class isn't where the business logic / policy goes; it is just the
     * holder of information.
     */
    private boolean foundIsl;

    public DiscoveryNode(String switchId, String portId, int checkInterval, int forlornThreshold) {
        this.switchId = switchId;
        this.portId = portId;
        this.timeCounter = 0;
        this.checkInterval = checkInterval;
        this.forlornThreshold = forlornThreshold;
        this.consecutiveFailure = 0;
        this.consecutiveSuccess = 0;
        this.foundIsl = false;
    }

    public DiscoveryNode(String switchId, String portId) {
        //FIXME: extract checkInterval & forlornThreshold from a config.
        this(switchId, portId, 0, NEVER);
    }

    public DiscoveryNode(String switchId) {
        //FIXME: extract checkInterval & forlornThreshold from a config.
        this(switchId, "", 0, NEVER);
    }

    public void setFoundIsl(boolean foundIsl){
        this.foundIsl = foundIsl;
    }

    public boolean isFoundIsl(){
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

    public boolean forlorn() {
        if (forlornThreshold == NEVER) { // never gonna give a link up.
             return false;
        }
        return consecutiveFailure >= forlornThreshold;
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

    public void incAttempts() {
        attempts++;
    }

    public void logTick() {
        timeCounter++;
    }

    public void resetTickCounter() {
        timeCounter = 0;
    }

    /**
     * @return true if attempts is greater than or equal to the limit.
     */
    public boolean maxAttempts(Integer attemptLimit) {
        return attemptLimit <= attempts;
    }

    public boolean timeToCheck() {
        return timeCounter >= checkInterval;
    }

    public String getSwitchId() {
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
        return Objects.equals(getSwitchId(), that.getSwitchId()) &&
                Objects.equals(getPortId(), that.getPortId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSwitchId(), getPortId());
    }

    @Override
    public String toString() {
        return "DiscoveryNode{" +
                "switchId='" + switchId + '\'' +
                ", portId='" + portId + '\'' +
                ", attempts=" + attempts +
                ", consecutiveFailure=" + consecutiveFailure +
                ", consecutiveSuccess=" + consecutiveSuccess +
                '}';
    }
}
