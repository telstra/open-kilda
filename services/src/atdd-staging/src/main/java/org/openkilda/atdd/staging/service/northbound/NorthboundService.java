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

package org.openkilda.atdd.staging.service.northbound;

import org.openkilda.messaging.model.HealthCheck;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.northbound.dto.flows.FlowValidationDto;
import org.openkilda.northbound.dto.switches.RulesValidationResult;
import org.openkilda.northbound.dto.switches.RulesSyncResult;

import java.util.List;

public interface NorthboundService {

    HealthCheck getHealthCheck();

    FlowPayload getFlow(String flowId);

    FlowPayload addFlow(FlowPayload payload);

    FlowPayload updateFlow(String flowId, FlowPayload payload);

    FlowPayload deleteFlow(String flowId);

    FlowPathPayload getFlowPath(String flowId);

    FlowIdStatusPayload getFlowStatus(String flowId);

    List<FlowPayload> getAllFlows();

    RulesSyncResult synchronizeSwitchRules(String switchId);

    List<FlowValidationDto> validateFlow(String flowId);

    RulesValidationResult validateSwitchRules(String switchId);
}
