/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.isllatency.service;

import static org.openkilda.wfm.topology.isllatency.LatencyAction.COPY_REVERSE_ROUND_TRIP_LATENCY;
import static org.openkilda.wfm.topology.isllatency.LatencyAction.DO_NOTHING;
import static org.openkilda.wfm.topology.isllatency.LatencyAction.USE_ONE_WAY_LATENCY;

import org.openkilda.messaging.info.event.IslOneWayLatency;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.topology.isllatency.LatencyAction;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DecisionMakerService {
    private SwitchRepository switchRepository;
    private Map<SwitchId, Boolean> switchSupportGroupMap;

    public DecisionMakerService(RepositoryFactory repositoryFactory) {
        switchRepository = repositoryFactory.createSwitchRepository();
        switchSupportGroupMap = new HashMap<>();
    }


    /**
     * Makes a decision what latency (one way/round-trip/none) must be used.
     *
     * @param data one way latency data
     *
     * @return decision in {@code LatencyAction} format.
     */
    public LatencyAction handleOneWayIslLatency(IslOneWayLatency data) {
        if (data.isSrcSwitchSupportsCopyField()) {
            if (data.isDstSwitchSupportsCopyField()) {
                return DO_NOTHING;
            } else {
                if (data.isDstSwitchSupportsGroups()) {
                    return COPY_REVERSE_ROUND_TRIP_LATENCY;
                } else {
                    return USE_ONE_WAY_LATENCY;
                }
            }
        } else {
            if (data.isDstSwitchSupportsCopyField()) {
                boolean isSrcSwitchSupportsCopyField;
                try {
                    isSrcSwitchSupportsCopyField = isSwitchSupportsGroups(data.getSrcSwitchId());
                } catch (SwitchNotFoundException e) {
                    log.warn("Couldn't set latency {} for ISL with source {}_{}. Packet id:{}. There is no such ISL",
                            data.getLatency(), data.getSrcSwitchId(), data.getSrcPortNo(), data.getPacketId());
                    return DO_NOTHING;
                }

                if (isSrcSwitchSupportsCopyField) {
                    return DO_NOTHING;
                } else {
                    return USE_ONE_WAY_LATENCY;
                }
            } else {
                return USE_ONE_WAY_LATENCY;
            }
        }
    }

    private boolean isSwitchSupportsGroups(SwitchId switchId) throws SwitchNotFoundException {
        if (switchSupportGroupMap.containsKey(switchId)) {
            return switchSupportGroupMap.get(switchId);
        }

        Switch sw = switchRepository.findById(switchId).orElseThrow(() -> new SwitchNotFoundException(switchId));
        boolean supports = !Switch.isCentecSwitch(sw.getOfDescriptionManufacturer());
        switchSupportGroupMap.put(switchId, supports);
        return supports;
    }
}
