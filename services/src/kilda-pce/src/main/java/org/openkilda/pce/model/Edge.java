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

import org.openkilda.model.Isl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(of = {"srcSwitch", "destSwitch", "srcPort", "destPort"})
public class Edge {
    @NonNull
    private Node srcSwitch;
    @NonNull
    private Node destSwitch;
    private int srcPort;
    private int destPort;

    private int cost;
    private long availableBandwidth;
    private long latency;

    @Setter
    private int diversityWeight;

    /**
     * Gets sum of weights, that filling is ruled by AvailableNetwork construction.
     *
     * @return the edge total static weight.
     */
    public long getStaticWeight() {
        return diversityWeight;
    }

    /**
     * Swap edge source and destination.
     *
     * @return new {@link Edge} instance, with swapped source and destination.
     */
    public Edge swap() {
        return this.toBuilder()
                .srcSwitch(this.destSwitch)
                .srcPort(this.destPort)
                .destSwitch(this.srcSwitch)
                .destPort(this.srcPort)
                .build();
    }

    /**
     * Constructs {@link Edge} builder and maps properties from passed {@link Isl} to them.
     *
     * @param isl the isl.
     * @return a builder instance
     */
    public static Edge.EdgeBuilder fromIslToBuilder(Isl isl) {
        return Edge.builder()
                .srcPort(isl.getSrcPort())
                .destPort(isl.getDestPort())
                .cost(isl.getCost())
                .latency(isl.getLatency())
                .availableBandwidth(isl.getAvailableBandwidth());
    }
}
