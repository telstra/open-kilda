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

package org.openkilda.northbound.service.impl;

import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.openkilda.messaging.model.ValidationFilter.FLOW_INFO;
import static org.openkilda.messaging.model.ValidationFilter.GROUPS;
import static org.openkilda.messaging.model.ValidationFilter.LOGICAL_PORTS;
import static org.openkilda.messaging.model.ValidationFilter.METERS;
import static org.openkilda.messaging.model.ValidationFilter.RULES;

import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.switches.GroupSyncEntry;
import org.openkilda.messaging.info.switches.LogicalPortsSyncEntry;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.MetersSyncEntry;
import org.openkilda.messaging.info.switches.RulesSyncEntry;
import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.MessageExchanger;
import org.openkilda.northbound.config.KafkaConfig;
import org.openkilda.northbound.dto.v1.switches.RulesSyncDto;
import org.openkilda.northbound.dto.v1.switches.RulesSyncResult;
import org.openkilda.northbound.dto.v1.switches.SwitchSyncResult;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.service.SwitchService;
import org.openkilda.northbound.utils.RequestCorrelationId;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.concurrent.ExecutionException;

@RunWith(SpringJUnit4ClassRunner.class)
public class SwitchServiceTest {

    public static final SwitchId SWITCH_ID = new SwitchId(1);
    @Autowired
    private SwitchService switchService;

    @Autowired
    private MessageExchanger messageExchanger;

    @Before
    public void reset() {
        messageExchanger.resetMockedResponses();
    }

    @Test
    public void shouldSynchronizeRules() throws Exception {
        String correlationId = createCorrelationId("sync-rules");

        Long missingRule = 100L;
        Long misconfiguredRule = 11L;
        Long excessRule = 101L;
        Long properRule = 10L;

        RulesSyncEntry rulesSyncEntry = new RulesSyncEntry(singletonList(missingRule), singletonList(misconfiguredRule),
                singletonList(properRule), singletonList(excessRule), singletonList(missingRule),
                singletonList(excessRule));
        SwitchSyncResponse rules = new SwitchSyncResponse(SWITCH_ID, rulesSyncEntry, MetersSyncEntry.builder().build(),
                GroupSyncEntry.builder().build(), LogicalPortsSyncEntry.builder().build());
        messageExchanger.mockResponse(correlationId, rules);

        RulesSyncResult result = switchService.syncRules(SWITCH_ID).get();
        assertThat(result.getMissingRules(), is(singletonList(missingRule)));
        assertThat(result.getInstalledRules(), is(singletonList(missingRule)));
        assertThat(result.getExcessRules(), is(singletonList(excessRule)));
        assertThat(result.getInstalledRules(), is(singletonList(missingRule)));
    }

    @Test
    public void shouldSynchronizeSwitch() throws ExecutionException, InterruptedException {
        String correlationId = createCorrelationId("not-sync-rules");

        Long missingRule = 100L;
        Long misconfiguredRule = 11L;
        Long excessRule = 101L;
        Long properRule = 10L;

        RulesSyncEntry rulesEntry = new RulesSyncEntry(singletonList(missingRule), singletonList(misconfiguredRule),
                singletonList(properRule), singletonList(excessRule), singletonList(missingRule),
                singletonList(excessRule));
        InfoData validationResult = new SwitchSyncResponse(SWITCH_ID, rulesEntry,
                MetersSyncEntry.builder().proper(singletonList(getMeterInfo(properRule))).build(),
                GroupSyncEntry.builder().build(), LogicalPortsSyncEntry.builder().build());
        messageExchanger.mockResponse(correlationId, validationResult);

        SwitchSyncResult result = switchService.syncSwitch(SWITCH_ID, true).get();
        RulesSyncDto rules = result.getRules();
        assertThat(rules.getMissing(), is(singletonList(missingRule)));
        assertThat(rules.getMisconfigured(), is(singletonList(misconfiguredRule)));
        assertThat(rules.getInstalled(), is(singletonList(missingRule)));
        assertThat(rules.getExcess(), is(singletonList(excessRule)));
        assertThat(rules.getInstalled(), is(singletonList(missingRule)));
        assertThat(rules.getRemoved(), is(singletonList(excessRule)));
    }

    @Test(expected = MessageException.class)
    public void errorWhileParsingIncludeStringV2ValidationParameters() {
        String correlationId = createCorrelationId("invalid-include");
        messageExchanger.mockResponse(correlationId, null);
        switchService.validateSwitch(SWITCH_ID, "meter|groups", "flow_info");
    }

    @Test(expected = MessageException.class)
    public void errorWhileParsingExcludeStringV2ValidationParameters() {
        String correlationId = createCorrelationId("invalid-exclude");
        messageExchanger.mockResponse(correlationId, null);
        switchService.validateSwitch(SWITCH_ID, "meters|groups", "flo_info");
    }

    @Test(expected = MessageException.class)
    public void errorWhileParsingExcludeStringWithIncludeValuesV2ValidationParameters() {
        String correlationId = createCorrelationId("invalid-exclude");
        messageExchanger.mockResponse(correlationId, null);
        switchService.validateSwitch(SWITCH_ID, "meters|groups", "meters");
    }

    @Test(expected = MessageException.class)
    public void errorWhileParsingExcludeStringWithIncludeValuesAndEmptyIncludeV2ValidationParameters() {
        String correlationId = createCorrelationId("invalid-exclude");
        messageExchanger.mockResponse(correlationId, null);
        switchService.validateSwitch(SWITCH_ID, null, "meters");
    }

    @Test(expected = MessageException.class)
    public void errorWhileParsingIncludeStringWithExcludeValuesV2ValidationParameters() {
        String correlationId = createCorrelationId("invalid-exclude");
        messageExchanger.mockResponse(correlationId, null);
        switchService.validateSwitch(SWITCH_ID, "flow_info", null);
    }

    @Test(expected = MessageException.class)
    public void errorWhileV1filterToIncludeV2ValidationParameters() {
        String correlationId = createCorrelationId("invalid-exclude");
        messageExchanger.mockResponse(correlationId, null);
        switchService.validateSwitch(SWITCH_ID, "meter_flow_info", null);
    }

    @Test(expected = MessageException.class)
    public void errorWhileV1filterToExcludeV2ValidationParameters() {
        String correlationId = createCorrelationId("invalid-exclude");
        messageExchanger.mockResponse(correlationId, null);
        switchService.validateSwitch(SWITCH_ID, null, "meter_flow_info");
    }

    @Test
    public void fullListOfIncludeV2ValidationParameters() {
        String correlationId = createCorrelationId("full-include");
        messageExchanger.mockResponse(correlationId, null);
        switchService.validateSwitch(SWITCH_ID, "meters|groups|rules|logical_ports", null);

        CommandMessage message = (CommandMessage) messageExchanger.getCapturedMessage(correlationId);
        SwitchValidateRequest request = (SwitchValidateRequest) message.getData();
        assertEquals(Sets.newHashSet(METERS, GROUPS, RULES, LOGICAL_PORTS, FLOW_INFO), request.getValidationFilters());
    }

    @Test
    public void emptyListOfIncludeV2ValidationParameters() {
        String correlationId = createCorrelationId("full-include");

        messageExchanger.mockResponse(correlationId, null);
        switchService.validateSwitch(SWITCH_ID, null, null);

        CommandMessage message = (CommandMessage) messageExchanger.getCapturedMessage(correlationId);
        SwitchValidateRequest request = (SwitchValidateRequest) message.getData();
        assertEquals(Sets.newHashSet(METERS, GROUPS, RULES, LOGICAL_PORTS, FLOW_INFO), request.getValidationFilters());
    }

    @Test
    public void listOfIncludeExcludeFlowInfoV2ValidationParameters() {
        String correlationId = createCorrelationId("full-include");

        messageExchanger.mockResponse(correlationId, null);
        switchService.validateSwitch(SWITCH_ID, "meters|rules|logical_ports", "flow_info");

        CommandMessage message = (CommandMessage) messageExchanger.getCapturedMessage(correlationId);
        SwitchValidateRequest request = (SwitchValidateRequest) message.getData();
        assertEquals(Sets.newHashSet(METERS, RULES, LOGICAL_PORTS), request.getValidationFilters());
    }

    @Test
    public void fullListOfIncludeExcludeFlowInfoV2ValidationParameters() {
        String correlationId = createCorrelationId("full-include");

        messageExchanger.mockResponse(correlationId, null);
        switchService.validateSwitch(SWITCH_ID, null, "flow_info");

        CommandMessage message = (CommandMessage) messageExchanger.getCapturedMessage(correlationId);
        SwitchValidateRequest request = (SwitchValidateRequest) message.getData();
        assertEquals(Sets.newHashSet(METERS, GROUPS, RULES, LOGICAL_PORTS), request.getValidationFilters());
    }

    private static String createCorrelationId(String correlationId) {
        RequestCorrelationId.create(correlationId);
        return correlationId;
    }

    private MeterInfoEntry getMeterInfo(Long cookie) {
        return MeterInfoEntry.builder()
                .meterId(1L)
                .cookie(cookie)
                .flowId("flowId")
                .rate(1L)
                .burstSize(1L)
                .flags(new String[]{"f1", "f2"})
                .build();
    }

    @TestConfiguration
    @Import(KafkaConfig.class)
    @ComponentScan({"org.openkilda.northbound.converter"})
    @PropertySource({"classpath:northbound.properties"})
    static class Config {
        @Bean
        public MessagingChannel messagingChannel() {
            return new MessageExchanger();
        }

        @Bean
        public SwitchService switchService(MessagingChannel messagingChannel) {
            return new SwitchServiceImpl(messagingChannel);
        }
    }
}
