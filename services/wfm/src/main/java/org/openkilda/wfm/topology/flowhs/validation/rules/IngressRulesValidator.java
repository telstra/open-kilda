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

package org.openkilda.wfm.topology.flowhs.validation.rules;

import org.openkilda.floodlight.flow.request.InstallIngressRule;
import org.openkilda.floodlight.flow.response.FlowRuleResponse;
import org.openkilda.model.SwitchFeature;

import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Set;

@Slf4j
public class IngressRulesValidator extends RulesValidator {

    private final Set<SwitchFeature> switchFeatures;

    public IngressRulesValidator(InstallIngressRule expected, FlowRuleResponse actual,
                                 Set<SwitchFeature> switchFeatures) {
        super(expected, actual);

        this.switchFeatures = switchFeatures;
    }

    @Override
    public boolean validate() {
        boolean valid = super.validate();

        InstallIngressRule expectedIngress = (InstallIngressRule) expected;
        if (!Objects.equals(expectedIngress.getInputVlanId(), actual.getInVlan())) {
            log.warn("Input vlan mismatch for the flow {} on the switch {}. Expected {}, actual {}",
                    expected.getFlowId(), expected.getSwitchId(), expectedIngress.getInputVlanId(), actual.getInVlan());
            valid = false;
        }

        if (switchFeatures.contains(SwitchFeature.METERS) && !Objects.equals(expectedIngress.getMeterId(),
                actual.getMeterId())) {
            log.warn("Meter mismatch for the flow {} on the switch {}. Expected {}, actual {}",
                    expected.getFlowId(), expected.getSwitchId(), expectedIngress.getMeterId(), actual.getMeterId());
            valid = false;
        }

        return valid;
    }
}
