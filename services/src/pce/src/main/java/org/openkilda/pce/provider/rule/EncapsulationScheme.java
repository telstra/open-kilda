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

import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.model.rule.FlowInstall;

public interface EncapsulationScheme {
    FlowInstall buildIngressInstallationFlowRule(FlowDto flow);

    FlowInstall buildIngressUpdateFlowRule(FlowDto flow);

    FlowInstall buildIngressDeletionFlowRule(FlowDto flow);

    FlowInstall buildEgressInstallationFlowRule(FlowDto flow);

    FlowInstall buildEgressUpdateFlowRule(FlowDto flow);

    FlowInstall buildEgressDeletionFlowRule(FlowDto flow);

    FlowInstall buildTransitInstallationFlowRule(FlowDto flow);

    FlowInstall buildTransitUpdateFlowRule(FlowDto flow);

    FlowInstall buildTransitDeletionFlowRule(FlowDto flow);

    FlowInstall buildInstallationMeter(FlowDto flow);

    FlowInstall buildUpdateMeter(FlowDto flow);

    FlowInstall buildDeletionMeter(FlowDto flow);

    FlowInstall buildSuspendIngressFlowRule(FlowDto flow);

    FlowInstall buildSuspendEgressFlowRule(FlowDto flow);

    default FlowInstall buildResumeIngressFlowRule(FlowDto flow) {
        return buildIngressUpdateFlowRule(flow);
    }

    default FlowInstall buildResumeEgressFlowRule(FlowDto flow) {
        return buildEgressUpdateFlowRule(flow);
    }
}
