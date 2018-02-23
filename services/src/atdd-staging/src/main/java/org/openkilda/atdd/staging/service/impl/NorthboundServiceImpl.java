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

package org.openkilda.atdd.staging.service.impl;

import org.modelmapper.ModelMapper;
import org.openkilda.atdd.staging.clients.northbound.api.FlowControllerApi;
import org.openkilda.atdd.staging.service.NorthboundService;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class NorthboundServiceImpl implements NorthboundService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NorthboundServiceImpl.class);

    @Autowired
    private FlowControllerApi flowControllerApi;

    @Autowired
    private ModelMapper modelMapper;

    //    @CircuitBreaker(maxAttempts = 2, openTimeout = 5000l, resetTimeout = 10000l, exclude = TimeoutException.class)
    public List<FlowPayload> getFlows() {
        FlowPayload flow = modelMapper.map(
                flowControllerApi.getFlowsUsingGET(UUID.randomUUID().toString()), FlowPayload.class);
        return Collections.singletonList(flow);
    }
}
