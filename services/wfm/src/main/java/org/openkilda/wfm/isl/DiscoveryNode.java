package org.openkilda.wfm.isl;

import java.io.Serializable;
import java.util.Objects;

public class DiscoveryNode implements Serializable {
    private final String switchId;
    private final String portId;
    private int age;

    public DiscoveryNode(String switchId, String portId) {
        this.switchId = switchId;
        this.portId = portId;
        this.age = 0;
    }

    public void renew() {
        age = 0;
    }

    public void incAge() {
        age++;
    }

    public boolean isStale(Integer ageLimit) {
        return ageLimit <= age;
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
