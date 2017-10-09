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

package org.openkilda.topology.controller;

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;

import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowsResponse;
import org.openkilda.messaging.payload.flow.FlowsPayload;
import org.openkilda.topology.model.Topology;
import org.openkilda.topology.service.FlowService;
import org.openkilda.topology.service.TopologyService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for topology requests.
 */
@RestController
@PropertySource("classpath:topology.properties")
public class TopologyController {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(TopologyController.class);

    /**
     * The topology service instance.
     */
    @Autowired
    private TopologyService topologyService;

    /**
     * The flow service instance.
     */
    @Autowired
    private FlowService flowService;

    /**
     * Cleans topology.
     *
     * @param correlationId correlation ID header value
     * @return network topology
     */
    @RequestMapping(
            value = "/clear",
            method = RequestMethod.GET,
            produces = APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<Topology> clear(
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.debug("Clear topology: {}={}", CORRELATION_ID, correlationId);
        Topology topology = topologyService.clear(correlationId);
        return new ResponseEntity<>(topology, new HttpHeaders(), HttpStatus.OK);
    }

    /**
     * Dumps topology.
     *
     * @param correlationId correlation ID header value
     * @return network topology
     */
    @RequestMapping(
            value = "/network",
            method = RequestMethod.GET,
            produces = APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<Topology> network(
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.debug("Get topology: {}={}", CORRELATION_ID, correlationId);
        Topology topology = topologyService.network(correlationId);
        return new ResponseEntity<>(topology, new HttpHeaders(), HttpStatus.OK);
    }

    /**
     * Dumps flows.
     *
     * @param correlationId correlation ID header value
     * @return flows
     */
    @RequestMapping(
            value = "/flows",
            method = RequestMethod.GET,
            produces = APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<FlowsPayload> flows(
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.debug("Dump flows: {}={}", CORRELATION_ID, correlationId);
        InfoMessage message = flowService.getFlows(null, correlationId);
        FlowsResponse response = (FlowsResponse) message.getData();
        return new ResponseEntity<>(response.getPayload(), new HttpHeaders(), HttpStatus.OK);
    }
}
