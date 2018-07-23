/* Copyright 2018 Telstra Open Source
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
package org.openkilda.pce.model;


import java.util.*;

/**
 * This class is just a summary of what a switch is; sufficient for path computation.
 *
 */
public class SimpleSwitch implements Comparable<SimpleSwitch> {
    /**
     * The DPID is used as the primary key in most/all places.
     */
    public String dpid;
    /**
     * We are mostly interested in who this switch connects with. It might have multiple connections
     * to the same switch over different ports. Allow for this by creating an map of lists.
     *
     * key (String) = The destination switch dpid.
     */
    public Map<String, Set<SimpleIsl>> outbound;

    /** The default contructor is private to prevent null dpid / outboud scenarios */
    private SimpleSwitch() {
        this("");
    }

    public SimpleSwitch(String dpid) {
        this.dpid = dpid;
        this.outbound = new HashMap<>();
    }

    public SimpleSwitch addOutbound(SimpleIsl isl) {
        outbound.computeIfAbsent(isl.getDstDpid(), newSet -> new HashSet<>()).add(isl);
        return this;
    }

    @Override
    public int compareTo(SimpleSwitch other) {
        return (dpid != null)
                ? dpid.compareTo(other.dpid)
                : (other.dpid == null) ? 0 : -1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimpleSwitch)) return false;
        SimpleSwitch that = (SimpleSwitch) o;
        return Objects.equals(dpid, that.dpid) &&
                Objects.equals(outbound, that.outbound);
    }

    @Override
    public int hashCode() {

        return Objects.hash(dpid, outbound);
    }

    @Override
    public String toString() {
        String result = "\n\tdpid='" + dpid + '\'' + ", outbound=";
        for (String dst : outbound.keySet()) {
            result += "\n\t\tdestination: " + dst + ", isls(" + outbound.get(dst).size() + "): " + outbound.get(dst);
        }
        return result;
    }
}
