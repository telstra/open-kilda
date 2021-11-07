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

package org.openkilda.wfm.topology.switchmanager.model;

import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.MeterSpeakerCommandData;
import org.openkilda.rulemanager.SpeakerCommandData;

import lombok.Value;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Value
public class ValidationResult {
    List<FlowEntry> flowEntries;
    boolean processMeters;

    ValidateRulesResult validateRulesResult;
    ValidateMetersResult validateMetersResult;
    ValidateGroupsResult validateGroupsResult;
    ValidateLogicalPortsResult validateLogicalPortsResult;

    Collection<SpeakerCommandData> expectedOfElements;

    /**
     * Get expected rules by cookies.
     */
    public List<FlowSpeakerCommandData> getRulesByCookies(Set<Long> cookies) {
        return expectedOfElements.stream()
                .filter(ofElement -> ofElement instanceof FlowSpeakerCommandData)
                .map(ofElement -> (FlowSpeakerCommandData) ofElement)
                .filter(rule -> cookies.contains(rule.getCookie().getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Get expected meters by meter ids.
     */
    public List<MeterSpeakerCommandData> getMeterByMeterId(Set<Long> meterIds) {
        return expectedOfElements.stream()
                .filter(ofElement -> ofElement instanceof MeterSpeakerCommandData)
                .map(ofElement -> (MeterSpeakerCommandData) ofElement)
                .filter(meter -> meterIds.contains(meter.getMeterId().getValue()))
                .collect(Collectors.toList());
    }
}
