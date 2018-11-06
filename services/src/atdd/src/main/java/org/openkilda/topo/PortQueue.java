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

/**
 * PortQueue Identifies a queue on a port.
 */
public class PortQueue implements ITopoSlug {

    private final Port parent;
    private final String id;
    private int priority = -1;
    private String slug;

    public PortQueue(Port parent, String id) {
        this.parent = parent;
        this.id = id;
    }

    public PortQueue(Port parent, String id, int priority) {
        this.parent = parent;
        this.id = id;
        this.priority = priority;
    }

    public Port getParent() {
        return parent;
    }

    public String getId() {
        return id;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
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
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PortQueue portQueue = (PortQueue) o;

        if (priority != portQueue.priority) {
            return false;
        }
        if (!parent.equals(portQueue.parent)) {
            return false;
        }
        return id.equals(portQueue.id);
    }

    @Override
    public int hashCode() {
        int result = parent.hashCode();
        result = 31 * result + id.hashCode();
        result = 31 * result + priority;
        return result;
    }

    @Override
    public String toString() {
        return "PortQueue{"
                + "parent=" + parent
                + ", id='" + id + '\''
                + ", priority=" + priority
                + '}';
    }
}
