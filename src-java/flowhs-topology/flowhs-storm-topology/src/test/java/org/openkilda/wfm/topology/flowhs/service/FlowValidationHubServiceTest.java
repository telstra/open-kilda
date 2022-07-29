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

package org.openkilda.wfm.topology.flowhs.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.flow.FlowValidationRequest;
import org.openkilda.messaging.command.switches.DumpGroupsForFlowHsRequest;
import org.openkilda.messaging.command.switches.DumpMetersForFlowHsRequest;
import org.openkilda.messaging.command.switches.DumpRulesForFlowHsRequest;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.rule.SwitchGroupEntries;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.exceptions.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exceptions.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.validation.FlowValidationService;
import org.openkilda.wfm.topology.flowhs.fsm.validation.FlowValidationTestBase;

import com.google.common.collect.Lists;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

public class FlowValidationHubServiceTest extends FlowValidationTestBase {
    private static final String TEST_KEY = "test_key";

    private static FlowResourcesManager flowResourcesManager;
    private static FlowValidationService flowValidationService;
    private FlowValidationHubService flowValidationHubService;

    @BeforeClass
    public static void setUpOnce() {
        FlowValidationTestBase.setUpOnce();
        flowResourcesManager = new FlowResourcesManager(persistenceManager, flowResourcesConfig);
        flowValidationService = new FlowValidationService(persistenceManager, flowResourcesManager,
                MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT);
    }

    @Test
    public void testMainPath() throws DuplicateKeyException, UnknownKeyException {
        FlowValidationHubCarrier carrier = new FlowValidationHubCarrier() {
            @Override
            public void sendSpeakerRequest(String flowId, CommandData commandData) {
                assertTrue(commandData instanceof DumpRulesForFlowHsRequest
                        || commandData instanceof DumpMetersForFlowHsRequest
                        || commandData instanceof DumpGroupsForFlowHsRequest);

                List<SwitchId> switchIds =
                        Lists.newArrayList(TEST_SWITCH_ID_A, TEST_SWITCH_ID_B, TEST_SWITCH_ID_C, TEST_SWITCH_ID_E);
                if (commandData instanceof DumpRulesForFlowHsRequest) {
                    assertTrue(switchIds.contains(((DumpRulesForFlowHsRequest) commandData).getSwitchId()));
                } else if (commandData instanceof DumpMetersForFlowHsRequest) {
                    assertTrue(switchIds.contains(((DumpMetersForFlowHsRequest) commandData).getSwitchId()));
                } else {
                    assertTrue(switchIds.contains(((DumpGroupsForFlowHsRequest) commandData).getSwitchId()));
                }
            }

            @Override
            public void sendNorthboundResponse(List<? extends InfoData> message) {
                assertEquals(4, message.size());
                try {
                    assertEquals(flowValidationService
                            .validateFlow(TEST_FLOW_ID_A, getSwitchFlowEntriesWithTransitVlan(),
                                    getSwitchMeterEntries(), getSwitchGroupEntries()), message);
                } catch (FlowNotFoundException | SwitchNotFoundException e) {
                    //tested in the FlowValidationServiceTest
                }
            }

            @Override
            public void sendNorthboundResponse(Message message) {
                fail();
            }

            @Override
            public void cancelTimeoutCallback(String key) {
                assertEquals(TEST_KEY, key);
            }

            @Override
            public void sendInactive() {

            }
        };
        flowValidationHubService = new FlowValidationHubService(carrier, persistenceManager, flowResourcesManager,
                MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT);

        buildTransitVlanFlow("");
        flowValidationHubService.handleFlowValidationRequest(TEST_KEY, new CommandContext(),
                new FlowValidationRequest(TEST_FLOW_ID_A));
        for (SwitchFlowEntries switchFlowEntries : getSwitchFlowEntriesWithTransitVlan()) {
            flowValidationHubService.handleAsyncResponse(TEST_KEY, switchFlowEntries);
        }
        for (SwitchMeterEntries switchMeterEntries : getSwitchMeterEntries()) {
            flowValidationHubService.handleAsyncResponse(TEST_KEY, switchMeterEntries);
        }
        for (SwitchGroupEntries switchGroupEntries : getSwitchGroupEntries()) {
            flowValidationHubService.handleAsyncResponse(TEST_KEY, switchGroupEntries);
        }
    }

    @Test
    public void testTimeout() throws DuplicateKeyException, UnknownKeyException {
        FlowValidationHubCarrier carrier = new FlowValidationHubCarrier() {
            @Override
            public void sendSpeakerRequest(String flowId, CommandData commandData) {
                assertTrue(commandData instanceof DumpRulesForFlowHsRequest
                        || commandData instanceof DumpMetersForFlowHsRequest
                        || commandData instanceof DumpGroupsForFlowHsRequest);

                List<SwitchId> switchIds =
                        Lists.newArrayList(TEST_SWITCH_ID_A, TEST_SWITCH_ID_B, TEST_SWITCH_ID_C, TEST_SWITCH_ID_E);
                if (commandData instanceof DumpRulesForFlowHsRequest) {
                    assertTrue(switchIds.contains(((DumpRulesForFlowHsRequest) commandData).getSwitchId()));
                } else if (commandData instanceof DumpMetersForFlowHsRequest) {
                    assertTrue(switchIds.contains(((DumpMetersForFlowHsRequest) commandData).getSwitchId()));
                } else {
                    assertTrue(switchIds.contains(((DumpGroupsForFlowHsRequest) commandData).getSwitchId()));
                }
            }

            @Override
            public void sendNorthboundResponse(List<? extends InfoData> message) {
                fail();
            }

            @Override
            public void sendNorthboundResponse(Message message) {
                assertEquals(ErrorType.OPERATION_TIMED_OUT, ((ErrorMessage) message).getData().getErrorType());
            }

            @Override
            public void cancelTimeoutCallback(String key) {
                assertEquals(TEST_KEY, key);
            }

            @Override
            public void sendInactive() {

            }
        };
        flowValidationHubService = new FlowValidationHubService(carrier, persistenceManager, flowResourcesManager,
                MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT);

        buildTransitVlanFlow("");
        flowValidationHubService.handleFlowValidationRequest(TEST_KEY, new CommandContext(),
                new FlowValidationRequest(TEST_FLOW_ID_A));
        flowValidationHubService.handleTimeout(TEST_KEY);
    }

    @Test
    public void testFlowNotFoundError() throws DuplicateKeyException, UnknownKeyException {
        FlowValidationHubCarrier carrier = new FlowValidationHubCarrier() {
            @Override
            public void sendSpeakerRequest(String flowId, CommandData commandData) {
                assertTrue(commandData instanceof DumpRulesForFlowHsRequest
                        || commandData instanceof DumpMetersForFlowHsRequest
                        || commandData instanceof DumpGroupsForFlowHsRequest);

                List<SwitchId> switchIds =
                        Lists.newArrayList(TEST_SWITCH_ID_A, TEST_SWITCH_ID_B, TEST_SWITCH_ID_C, TEST_SWITCH_ID_E);
                if (commandData instanceof DumpRulesForFlowHsRequest) {
                    assertTrue(switchIds.contains(((DumpRulesForFlowHsRequest) commandData).getSwitchId()));
                } else if (commandData instanceof DumpMetersForFlowHsRequest) {
                    assertTrue(switchIds.contains(((DumpMetersForFlowHsRequest) commandData).getSwitchId()));
                } else {
                    assertTrue(switchIds.contains(((DumpGroupsForFlowHsRequest) commandData).getSwitchId()));
                }
            }

            @Override
            public void sendNorthboundResponse(List<? extends InfoData> message) {
                fail();
            }

            @Override
            public void sendNorthboundResponse(Message message) {
                assertEquals(ErrorType.NOT_FOUND, ((ErrorMessage) message).getData().getErrorType());
            }

            @Override
            public void cancelTimeoutCallback(String key) {
                assertEquals(TEST_KEY, key);
            }

            @Override
            public void sendInactive() {
            }
        };

        flowValidationHubService = new FlowValidationHubService(carrier, persistenceManager, flowResourcesManager,
                MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT);

        buildTransitVlanFlow("");
        flowValidationHubService.handleFlowValidationRequest(TEST_KEY, new CommandContext(),
                new FlowValidationRequest("test"));

        flowValidationHubService.handleFlowValidationRequest(TEST_KEY, new CommandContext(),
                new FlowValidationRequest(TEST_FLOW_ID_A));
        transactionManager.doInTransaction(() ->
                flowRepository.remove(flowRepository.findById(TEST_FLOW_ID_A).get()));
        for (SwitchFlowEntries switchFlowEntries : getSwitchFlowEntriesWithTransitVlan()) {
            flowValidationHubService.handleAsyncResponse(TEST_KEY, switchFlowEntries);
        }
        for (SwitchMeterEntries switchMeterEntries : getSwitchMeterEntries()) {
            flowValidationHubService.handleAsyncResponse(TEST_KEY, switchMeterEntries);
        }
        for (SwitchGroupEntries switchGroupEntries : getSwitchGroupEntries()) {
            flowValidationHubService.handleAsyncResponse(TEST_KEY, switchGroupEntries);
        }
    }
}
