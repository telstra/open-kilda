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
import org.openkilda.messaging.MessageData;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.flow.FlowValidationRequest;
import org.openkilda.messaging.command.switches.DumpGroupsForFlowHsRequest;
import org.openkilda.messaging.command.switches.DumpMetersForFlowHsRequest;
import org.openkilda.messaging.command.switches.DumpRulesForFlowHsRequest;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.flow.FlowDumpResponse;
import org.openkilda.messaging.info.group.GroupDumpResponse;
import org.openkilda.messaging.info.meter.MeterDumpResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.RuleManagerImpl;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.validation.FlowValidationService;
import org.openkilda.wfm.topology.flowhs.fsm.validation.FlowValidationTestBase;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Set;

public class FlowValidationHubServiceTest extends FlowValidationTestBase {
    private static final String TEST_KEY = "test_key";

    private static FlowValidationService flowValidationService;
    private static RuleManagerConfig ruleManagerConfig;
    private FlowValidationHubService flowValidationHubService;

    @BeforeClass
    public static void setUpOnce() {
        FlowValidationTestBase.setUpOnce();
        ruleManagerConfig = configurationProvider.getConfiguration(RuleManagerConfig.class);
        flowValidationService = new FlowValidationService(persistenceManager, new RuleManagerImpl(ruleManagerConfig));
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
                    Set<SwitchId> switchIdSet =
                            Sets.newHashSet(flowValidationService.getSwitchIdListByFlowId(TEST_FLOW_ID_A));
                    assertEquals(flowValidationService
                            .validateFlow(TEST_FLOW_ID_A, getFlowDumpResponseWithTransitVlan(),
                                    getMeterDumpResponses(), getSwitchGroupEntries(), switchIdSet), message);
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
        flowValidationHubService = new FlowValidationHubService(carrier, persistenceManager,
                new RuleManagerImpl(ruleManagerConfig));

        buildTransitVlanFlow("");
        flowValidationHubService.handleFlowValidationRequest(TEST_KEY, new CommandContext(),
                new FlowValidationRequest(TEST_FLOW_ID_A));

        List<MessageData> responses = Lists.newArrayList();
        responses.addAll(getFlowDumpResponseWithTransitVlan());
        responses.addAll(getMeterDumpResponses());
        responses.addAll(getSwitchGroupEntries());

        for (MessageData response : responses) {
            flowValidationHubService.handleAsyncResponse(TEST_KEY, response);
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
        flowValidationHubService = new FlowValidationHubService(carrier, persistenceManager,
                new RuleManagerImpl(ruleManagerConfig));

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

        flowValidationHubService = new FlowValidationHubService(carrier, persistenceManager,
                new RuleManagerImpl(ruleManagerConfig));

        buildTransitVlanFlow("");
        flowValidationHubService.handleFlowValidationRequest(TEST_KEY, new CommandContext(),
                new FlowValidationRequest("test"));

        flowValidationHubService.handleFlowValidationRequest(TEST_KEY, new CommandContext(),
                new FlowValidationRequest(TEST_FLOW_ID_A));
        transactionManager.doInTransaction(() ->
                flowRepository.remove(flowRepository.findById(TEST_FLOW_ID_A).get()));
        for (FlowDumpResponse switchFlowEntries : getFlowDumpResponseWithTransitVlan()) {
            flowValidationHubService.handleAsyncResponse(TEST_KEY, switchFlowEntries);
        }
        for (MeterDumpResponse switchMeterEntries : getMeterDumpResponses()) {
            flowValidationHubService.handleAsyncResponse(TEST_KEY, switchMeterEntries);
        }
        for (GroupDumpResponse switchGroupEntries : getSwitchGroupEntries()) {
            flowValidationHubService.handleAsyncResponse(TEST_KEY, switchGroupEntries);
        }
    }
}
