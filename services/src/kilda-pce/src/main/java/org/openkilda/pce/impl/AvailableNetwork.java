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

package org.openkilda.pce.impl;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.minBy;
import static java.util.stream.Collectors.toList;

import org.openkilda.model.Isl;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;

import com.google.common.annotations.VisibleForTesting;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Semantically, this class represents an "available network". That means everything in it is available for path
 * allocation.
 */
@Slf4j
@ToString
public class AvailableNetwork {
    @VisibleForTesting
    final Map<SwitchId, Switch> switches = new HashMap<>();

    public Switch getSwitch(SwitchId dpid) {
        return switches.get(dpid);
    }

    /**
     * Creates switches (if they are not created yet) and ISL between them.
     */
    public void addLink(Isl isl) {
        Switch srcSwitch = getOrInitSwitch(isl.getSrcSwitch());
        Switch dstSwitch = getOrInitSwitch(isl.getDestSwitch());

        // Remove if the same already exists.
        if (srcSwitch.getOutgoingLinks().removeIf(link -> link.getDestSwitch().equals(isl.getDestSwitch())
                && link.getDestPort() == isl.getDestPort())) {
            log.warn("Duplicate ISL has been added to AvailableNetwork: {}", isl);
        }

        if (dstSwitch.getIncomingLinks().removeIf(link -> link.getSrcSwitch().equals(isl.getSrcSwitch())
                && link.getSrcPort() == isl.getSrcPort())) {
            log.warn("Duplicate ISL has been added to AvailableNetwork: {}", isl);
        }

        // Clone the object to unbind the src and dest fields in Isl entity.
        Isl copiedIsl = isl.toBuilder().srcSwitch(srcSwitch).destSwitch(dstSwitch).build();
        srcSwitch.getOutgoingLinks().add(copiedIsl);
        dstSwitch.getIncomingLinks().add(copiedIsl);
    }

    private Switch getOrInitSwitch(Switch sw) {
        // Clone the object to unbind the relations' fields in Switch entity.
        return switches.computeIfAbsent(sw.getSwitchId(),
                swId -> sw.toBuilder().outgoingLinks(new ArrayList<>()).incomingLinks(new ArrayList<>()).build());
    }

    /**
     * Call this function to reduce the network to single (directed) links between src and dst switches.
     */
    public void reduceByCost() {
        for (Switch sw : switches.values()) {
            if (sw.getOutgoingLinks().isEmpty()) {
                log.warn("Switch {} has NO OUTBOUND isls", sw.getSwitchId());
            } else {
                List<Isl> reducedOutgoingLinks = sw.getOutgoingLinks().stream()
                        .collect(groupingBy(Isl::getDestSwitch, minBy(comparingInt(Isl::getCost)))).values().stream()
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(toList());
                sw.setOutgoingLinks(reducedOutgoingLinks);
            }

            if (sw.getIncomingLinks().isEmpty()) {
                log.warn("Switch {} has NO INBOUND isls", sw.getSwitchId());
            } else {
                List<Isl> reducedIncomingLinks = sw.getIncomingLinks().stream()
                        .collect(groupingBy(Isl::getSrcSwitch, minBy(comparingInt(Isl::getCost)))).values().stream()
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(toList());
                sw.setIncomingLinks(reducedIncomingLinks);
            }
        }
    }
}
