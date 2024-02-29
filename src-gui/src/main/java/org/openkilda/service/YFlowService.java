/* Copyright 2023 Telstra Open Source
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

package org.openkilda.service;

import org.openkilda.integration.service.FlowsIntegrationService;
import org.openkilda.model.YFlowRerouteResult;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class YFlowService {

    @Autowired
    private FlowsIntegrationService flowsIntegrationService;

    /**
     * Re-route Y-flow by Y-flow id.
     *
     * @param yFlowId the y-flow id
     * @return flow path
     */
    public YFlowRerouteResult rerouteFlow(String yFlowId) {
        log.info(String.format("The re-routing request for y-flow is in progress, y-flow ID: %s", yFlowId));
        return flowsIntegrationService.rerouteYFlow(yFlowId);
    }

}
