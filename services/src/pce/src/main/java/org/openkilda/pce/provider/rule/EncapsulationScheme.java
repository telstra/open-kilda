/* Copyright 2017 Telstra Open Source
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

package org.openkilda.pce.provider.rule;

import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.rule.FlowInstall;

public interface EncapsulationScheme {
    FlowInstall buildIngressInstallationFlowRule(Flow flow);

    FlowInstall buildIngressUpdateFlowRule(Flow flow);

    FlowInstall buildIngressDeletionFlowRule(Flow flow);

    FlowInstall buildEgressInstallationFlowRule(Flow flow);

    FlowInstall buildEgressUpdateFlowRule(Flow flow);

    FlowInstall buildEgressDeletionFlowRule(Flow flow);

    FlowInstall buildTransitInstallationFlowRule(Flow flow);

    FlowInstall buildTransitUpdateFlowRule(Flow flow);

    FlowInstall buildTransitDeletionFlowRule(Flow flow);

    FlowInstall buildInstallationMeter(Flow flow);

    FlowInstall buildUpdateMeter(Flow flow);

    FlowInstall buildDeletionMeter(Flow flow);

    FlowInstall buildSuspendIngressFlowRule(Flow flow);

    FlowInstall buildSuspendEgressFlowRule(Flow flow);

    default FlowInstall buildResumeIngressFlowRule(Flow flow) {
        return buildIngressUpdateFlowRule(flow);
    }

    default FlowInstall buildResumeEgressFlowRule(Flow flow) {
        return buildEgressUpdateFlowRule(flow);
    }
}
