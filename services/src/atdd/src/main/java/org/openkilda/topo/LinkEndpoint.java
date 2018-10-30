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

package org.openkilda.topo;

import java.util.Optional;

/**
 * LinkEndpoint captures the key elements of a link endpoint.
 */
public class LinkEndpoint implements ITopoSlug {

    private final Switch topoSwitch;
    private final Port switchPort;
    private final PortQueue portQueue;
    private transient String slug;

    private static final String NULL_ID = "0";


    /**
     * Constructor.
     *
     * @param topoSwitch At least a valid switch is needed.
     * @param switchPort can be null; id will be 0.
     * @param portQueue can be null; id will be 0.
     */
    public LinkEndpoint(Switch topoSwitch, Port switchPort, PortQueue portQueue) {
        if (topoSwitch == null) {
            throw new IllegalArgumentException("Switch can't be null");
        }
        this.topoSwitch = topoSwitch;
        this.switchPort = (switchPort != null) ? switchPort : new Port(this.topoSwitch, NULL_ID);
        this.portQueue = (portQueue != null) ? portQueue : new PortQueue(this.switchPort, NULL_ID);
    }

    public LinkEndpoint(PortQueue portQueue) {
        // check for nulls all the way up
        this(Optional.ofNullable(portQueue).map(PortQueue::getParent).map(Port::getParent).orElse(null),
                Optional.ofNullable(portQueue).map(PortQueue::getParent).orElse(null),
                portQueue);
    }


    public Switch getTopoSwitch() {
        return topoSwitch;
    }

    public Port getSwitchPort() {
        return switchPort;
    }

    public PortQueue getPortQueue() {
        return portQueue;
    }

    /**
     * Gets slug.
     *
     * @return the slug
     */
    public String getSlug() {
        if (slug == null) {
            slug = TopoSlug.toString(this);
        }
        return slug;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LinkEndpoint)) {
            return false;
        }

        LinkEndpoint that = (LinkEndpoint) o;

        if (topoSwitch != null ? !topoSwitch.equals(that.topoSwitch) : that.topoSwitch != null) {
            return false;
        }
        if (switchPort != null ? !switchPort.equals(that.switchPort) : that.switchPort != null) {
            return false;
        }
        return portQueue != null ? portQueue.equals(that.portQueue) : that.portQueue == null;
    }

    @Override
    public int hashCode() {
        int result = topoSwitch != null ? topoSwitch.hashCode() : 0;
        result = 31 * result + (switchPort != null ? switchPort.hashCode() : 0);
        result = 31 * result + (portQueue != null ? portQueue.hashCode() : 0);
        return result;
    }

    public static void main(String[] args) {
    }

}
