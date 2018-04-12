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

import java.util.Objects;

/**
 * The ISL is directed and connects to a pair of EndPoints.
 *
 * For equals and hashcode, only the src and dst information are used, not the properties.
 */
public class SimpleIsl {
    /** If another default is desired, you can control that at object creation */
    public static final int DEFAULT_COST = 700;

    public String src_dpid;
    public String dst_dpid;
    public int src_port;
    public int dst_port;
    public int cost;
    public int latency;


    public SimpleIsl(String src_dpid, String dst_dpid, int src_port, int dst_port, int cost, int latency) {
        this.src_dpid = src_dpid;
        this.dst_dpid = dst_dpid;
        this.src_port = src_port;
        this.dst_port = dst_port;
        this.cost = (cost == 0) ? DEFAULT_COST : cost;
        this.latency = latency;
    }

    /**
     * Use this comparison if very strong equality is needed (most likely rare; probably only testing)
     *
     * @return true if every field is the same.
     */
    public boolean identical(Object o) {
        if (this.equals(o)){
            SimpleIsl simpleIsl = (SimpleIsl) o;
            return cost == simpleIsl.cost && latency == simpleIsl.latency;
        }
        return false;
    }

    /**
     * Since equals and hashcode may be used in collection operations, we ignore properties like
     * cost and latency. Otherwise, you could end up with two isls in a list that shouldn't be there
     * - ie you cant have two isls over the same switch/port.
     *
     * @return true if src and dst match.  To compare properties as well, use identical.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimpleIsl)) return false;
        SimpleIsl simpleIsl = (SimpleIsl) o;
        return src_port == simpleIsl.src_port &&
                dst_port == simpleIsl.dst_port &&
                Objects.equals(src_dpid, simpleIsl.src_dpid) &&
                Objects.equals(dst_dpid, simpleIsl.dst_dpid);
    }

    @Override
    public int hashCode() {

        return Objects.hash(src_dpid, dst_dpid, src_port, dst_port);
    }

    @Override
    public String toString() {
        return "SimpleIsl{" +
                "src_dpid='" + src_dpid + '\'' +
                ", dst_dpid='" + dst_dpid + '\'' +
                ", src_port=" + src_port +
                ", dst_port=" + dst_port +
                ", cost=" + cost +
                ", latency=" + latency +
                '}';
    }
}
