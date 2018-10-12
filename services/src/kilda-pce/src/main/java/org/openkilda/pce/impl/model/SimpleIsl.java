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

package org.openkilda.pce.impl.model;

import org.openkilda.model.SwitchId;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

/**
 * The ISL is directed and connects to a pair of EndPoints.
 * <p/>
 * For equals and hashcode, only the src and dst information are used, not the properties.
 */
@Value
@EqualsAndHashCode(exclude = {"cost", "latency"})
@Builder
public class SimpleIsl {
    /**
     * If another default is desired, you can control that at object creation.
     */
    private static final int DEFAULT_COST = 700;

    @NonNull
    private SwitchId srcDpid;
    @NonNull
    private SwitchId dstDpid;
    private int srcPort;
    private int dstPort;
    private int cost;
    private int latency;


    public SimpleIsl(SwitchId srcDpid, SwitchId dstDpid, int srcPort, int dstPort, int cost, int latency) {
        this.srcDpid = srcDpid;
        this.dstDpid = dstDpid;
        this.srcPort = srcPort;
        this.dstPort = dstPort;
        this.cost = (cost == 0) ? DEFAULT_COST : cost;
        this.latency = latency;
    }

    /**
     * Use this comparison if very strong equality is needed (most likely rare; probably only testing).
     *
     * @return true if every field is the same.
     */
    public boolean identical(Object o) {
        if (this.equals(o)) {
            SimpleIsl simpleIsl = (SimpleIsl) o;
            return cost == simpleIsl.cost && latency == simpleIsl.latency;
        }
        return false;
    }

}
