/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.validation;

import org.openkilda.messaging.info.flow.PathDiscrepancyEntity;
import org.openkilda.model.Meter;
import org.openkilda.model.Switch;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.share.utils.rule.validation.SimpleSwitchRule;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class SimpleSwitchRuleComparator {
    private final SwitchRepository switchRepository;

    public SimpleSwitchRuleComparator(@NonNull SwitchRepository switchRepository) {
        this.switchRepository = switchRepository;
    }

    public List<PathDiscrepancyEntity> findDiscrepancy(SimpleSwitchRule expected, List<SimpleSwitchRule> actual)
            throws SwitchNotFoundException {
        return findDiscrepancy(expected, actual, new ArrayList<>(), new ArrayList<>());
    }

    /**
     * Compare expected and actual rules and gather discrepancy list.
     */
    public List<PathDiscrepancyEntity> findDiscrepancy(SimpleSwitchRule expected, List<SimpleSwitchRule> actual,
                                                       List<Long> pktCounts, List<Long> byteCounts)
            throws SwitchNotFoundException {
        List<PathDiscrepancyEntity> discrepancies = new ArrayList<>();
        SimpleSwitchRule matched = findMatched(expected, actual);

        if (matched == null) {
            discrepancies.add(new PathDiscrepancyEntity(String.valueOf(expected), "all", String.valueOf(expected), ""));
            pktCounts.add(-1L);
            byteCounts.add(-1L);
        } else {
            discrepancies.addAll(getRuleDiscrepancies(expected, matched));
            pktCounts.add(matched.getPktCount());
            byteCounts.add(matched.getByteCount());
        }

        return discrepancies;
    }

    private SimpleSwitchRule findMatched(SimpleSwitchRule expected, List<SimpleSwitchRule> actual) {

        //try to match on the cookie
        SimpleSwitchRule matched = actual.stream()
                .filter(rule -> rule.getCookie() != 0 && rule.getCookie() == expected.getCookie())
                .findFirst()
                .orElse(null);

        //if no cookie match, then try to match on in_port and in_vlan
        if (matched == null) {
            matched = actual.stream()
                    .filter(rule -> rule.getInPort() == expected.getInPort()
                            && rule.getInVlan() == expected.getInVlan())
                    .findFirst()
                    .orElse(null);
        }

        //if cookie or in_port and in_vlan doesn't match, try to match on out_port and out_vlan
        if (matched == null) {
            matched = actual.stream()
                    .filter(rule -> rule.getOutPort() == expected.getOutPort()
                            && Objects.equals(rule.getOutVlan(), expected.getOutVlan()))
                    .findFirst()
                    .orElse(null);
        }
        return matched;
    }

    private List<PathDiscrepancyEntity> getRuleDiscrepancies(SimpleSwitchRule expected, SimpleSwitchRule matched)
            throws SwitchNotFoundException {
        List<PathDiscrepancyEntity> discrepancies = new ArrayList<>();
        if (matched.getCookie() != expected.getCookie()) {
            discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "cookie",
                    String.valueOf(expected.getCookie()), String.valueOf(matched.getCookie())));
        }
        if (matched.getInPort() != expected.getInPort()) {
            discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "inPort",
                    String.valueOf(expected.getInPort()), String.valueOf(matched.getInPort())));
        }
        if (matched.getInVlan() != expected.getInVlan()) {
            discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "inVlan",
                    String.valueOf(expected.getInVlan()), String.valueOf(matched.getInVlan())));
        }
        if (matched.getTunnelId() != expected.getTunnelId()) {
            discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "tunnelId",
                    String.valueOf(expected.getTunnelId()), String.valueOf(matched.getTunnelId())));
        }
        if (matched.getOutPort() != expected.getOutPort()) {
            discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "outPort",
                    String.valueOf(expected.getOutPort()), String.valueOf(matched.getOutPort())));
        }
        if (!Objects.equals(matched.getOutVlan(), expected.getOutVlan())) {
            discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "outVlan",
                    String.valueOf(expected.getOutVlan()), String.valueOf(matched.getOutVlan())));
        }
        //meters on OF_12 switches are not supported, so skip them.
        if ((matched.getVersion() == null || matched.getVersion().compareTo("OF_12") > 0)
                && !(matched.getMeterId() == null && expected.getMeterId() == null)) {

            if (!Objects.equals(matched.getMeterId(), expected.getMeterId())) {
                discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "meterId",
                        String.valueOf(expected.getMeterId()), String.valueOf(matched.getMeterId())));
            } else {

                Switch sw = switchRepository.findById(expected.getSwitchId())
                        .orElseThrow(() -> new SwitchNotFoundException(expected.getSwitchId()));
                boolean isESwitch =
                        Switch.isNoviflowESwitch(sw.getOfDescriptionManufacturer(), sw.getOfDescriptionHardware());

                if (!equalsRate(matched.getMeterRate(), expected.getMeterRate(), isESwitch)) {
                    discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "meterRate",
                            String.valueOf(expected.getMeterRate()), String.valueOf(matched.getMeterRate())));
                }
                if (!equalsBurstSize(matched.getMeterBurstSize(), expected.getMeterBurstSize(), isESwitch)) {
                    discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "meterBurstSize",
                            String.valueOf(expected.getMeterBurstSize()), String.valueOf(matched.getMeterBurstSize())));
                }
                if (!Arrays.equals(matched.getMeterFlags(), expected.getMeterFlags())) {
                    discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "meterFlags",
                            Arrays.toString(expected.getMeterFlags()), Arrays.toString(matched.getMeterFlags())));
                }
            }
        }

        if (matched.getGroupId() != expected.getGroupId()) {
            discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "groupId",
                    String.valueOf(expected.getGroupId()), String.valueOf(matched.getGroupId())));
        }
        if (!Objects.equals(matched.getGroupBuckets(), expected.getGroupBuckets())) {
            discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "groupBuckets",
                    String.valueOf(expected.getGroupBuckets()), String.valueOf(matched.getGroupBuckets())));
        }
        return discrepancies;
    }

    private boolean equalsRate(Long actual, Long expected, boolean isESwitch) {
        if (actual == null || expected == null) {
            return Objects.equals(actual, expected);
        }

        return Meter.equalsRate(actual, expected, isESwitch);
    }

    private boolean equalsBurstSize(Long actual, Long expected, boolean isESwitch) {
        if (actual == null || expected == null) {
            return Objects.equals(actual, expected);
        }

        return Meter.equalsBurstSize(actual, expected, isESwitch);
    }
}
