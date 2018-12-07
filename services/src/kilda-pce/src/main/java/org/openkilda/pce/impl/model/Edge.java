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

import org.openkilda.model.Isl;
import org.openkilda.model.SwitchId;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Represents graph edge.
 */
@Value
@EqualsAndHashCode(exclude = {"cost", "latency"})
public class Edge {

    private SwitchId srcSwitch;
    private SwitchId destSwitch;
    private int srcPort;
    private int destPort;

    private int cost;
    private int latency;

    /**
     * Creates {@link Edge} instance from {@link Isl}.
     * @param isl isl to create from.
     * @return new {@link Edge} instance.
     */
    public static Edge fromIsl(Isl isl) {
        return new Edge(
                isl.getSrcSwitch().getSwitchId(),
                isl.getDestSwitch().getSwitchId(),
                isl.getSrcPort(),
                isl.getDestPort(),
                isl.getCost(),
                isl.getLatency());
    }

    /**
     * Return new {@link Edge} with swapped source and destination.
     */
    public Edge swap() {
        return new Edge(
                destSwitch,
                srcSwitch,
                destPort,
                srcPort,
                cost,
                latency);
    }
}
