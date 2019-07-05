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

import org.openkilda.floodlight.flow.request.InstallFlowRule;
import org.openkilda.floodlight.flow.response.FlowRuleResponse;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@Slf4j
@AllArgsConstructor
public class RulesValidator {

    protected final InstallFlowRule expected;
    protected final FlowRuleResponse actual;

    /**
     * Verify whether actual and expected rules are the same.
     * @return a result of validation.
     */
    public boolean validate() {
        boolean valid = true;
        if (!Objects.equals(expected.getCookie(), actual.getCookie())) {
            log.warn("Installed flow {} doesn't have required cookie {} on the switch {}",
                    expected.getFlowId(), expected.getCookie(), expected.getSwitchId());
            valid = false;
        }

        if (!Objects.equals(expected.getInputPort(), actual.getInPort())) {
            log.warn("Input port mismatch for the flow {} on the switch {}. Expected {}, actual {}",
                    expected.getFlowId(), expected.getSwitchId(), expected.getInputPort(), actual.getInPort());
            valid = false;
        }

        if (!Objects.equals(expected.getOutputPort(), actual.getOutPort())) {
            log.warn("Output port mismatch for the flow {} on the switch {}. Expected {}, actual {}",
                    expected.getFlowId(), expected.getSwitchId(), expected.getOutputPort(), actual.getOutPort());
            valid = false;
        }

        if (!Objects.equals(expected.getOutputPort(), actual.getOutPort())) {
            log.warn("Output port mismatch for the flow {} on the switch {}. Expected {}, actual {}",
                    expected.getFlowId(), expected.getSwitchId(), expected.getOutputPort(), actual.getOutPort());
            valid = false;
        }

        return valid;
    }
}
