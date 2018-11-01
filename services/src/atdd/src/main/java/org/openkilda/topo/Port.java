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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class Port implements ITopoSlug {

    /** The PortQueue.getSwitchId() is used as the key */
    private final Map<String, PortQueue> portQueues = new ConcurrentHashMap<String, PortQueue>(10);
    private final String id;
    private final Switch parent;
    private String slug;

    public Port(Switch parent, String id) {
        this.id = id;
        this.parent = parent;
    }

    public Map<String, PortQueue> getPortQueues() {
        return portQueues;
    }

    public String getId() {
        return id;
    }

    public Switch getParent() {
        return parent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Port port = (Port) o;

        // TODO: does portQueues.equals work if the objects are equivalent (ie not same obj)
        if (!Objects.equals(portQueues, port.portQueues)) {
            return false;
        }
        if (id != null ? !id.equals(port.id) : port.id != null) {
            return false;
        }
        return parent != null ? parent.equals(port.parent) : port.parent == null;
    }

    @Override
    public int hashCode() {
        int result = portQueues != null ? portQueues.hashCode() : 0;
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (parent != null ? parent.hashCode() : 0);
        return result;
    }

    @Override
    public String getSlug() {
        if (slug == null) {
            slug = TopoSlug.toString(this);
        }
        return slug;
    }
}
