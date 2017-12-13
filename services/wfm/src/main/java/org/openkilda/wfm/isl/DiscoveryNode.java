package org.openkilda.wfm.isl;

import java.io.Serializable;
import java.util.Objects;

public class DiscoveryNode implements Serializable {
    private final static int NEVER = 999999;
    private final String switchId;
    private final String portId;
    private int age;
    private int timeCounter;
    private int checkInterval;
    private int consequitiveFailures;
    private int forlornThreshold;


    public DiscoveryNode(String switchId, String portId, int checkInterval, int forlornThreshold) {
        this.switchId = switchId;
        this.portId = portId;
        this.age = 0;
        this.timeCounter = 0;
        this.checkInterval = checkInterval;
        this.forlornThreshold = forlornThreshold;
        consequitiveFailures = 0;
    }

    public DiscoveryNode(String switchId, String portId) {
        //FIXME: extract checkInterval & forlornThreshold from a config.
        this(switchId, portId, 1, NEVER);
    }

    public DiscoveryNode(String switchId) {
        //FIXME: extract checkInterval & forlornThreshold from a config.
        this(switchId, "", 1, NEVER);
    }

    public void renew() {
        age = 0;
        timeCounter = 0;
    }

    public boolean forlorn() {
        if (forlornThreshold == NEVER) { // never gonna give a link up.
             return false;
        }
        return consequitiveFailures >= forlornThreshold;
    }

    public void redeem() {
        consequitiveFailures = 0;
    }

    public void incAge() {
        age++;
    }

    public void logTick() {
        timeCounter++;
    }

    public void resetTickCounter() {
        timeCounter = 0;
    }

    public boolean isStale(Integer ageLimit) {
        return ageLimit <= age;
    }

    public void countFailure() {
        consequitiveFailures++;
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
                ", age=" + age +
                '}';
    }
}
