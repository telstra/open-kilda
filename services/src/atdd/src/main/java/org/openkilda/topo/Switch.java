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

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Switch implements ITopoSlug {

    /** The Port.getSwitchId() is used as the key */
    @JsonIgnore
    private final Map<String, Port> ports = new ConcurrentHashMap<String, Port>(48);
    private final String id;
    @JsonIgnore
    private String slug;

    public Switch(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public Map<String, Port> getPorts() {
        return ports;
    }

    @Override
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
        if (!(o instanceof Switch)) {
            return false;
        }

        Switch theSwitch = (Switch) o;

        if (!ports.equals(theSwitch.ports)) {
            return false;
        }
        return id != null ? id.equals(theSwitch.id) : theSwitch.id == null;
    }

    @Override
    public int hashCode() {
        int result = ports.hashCode();
        result = 31 * result + (id != null ? id.hashCode() : 0);
        return result;
    }
}
