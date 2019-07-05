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

package org.openkilda.northbound.service;

import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.MetersSyncEntry;
import org.openkilda.messaging.info.switches.RulesSyncEntry;
import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.MessageExchanger;
import org.openkilda.northbound.config.KafkaConfig;
import org.openkilda.northbound.converter.FlowMapper;
import org.openkilda.northbound.converter.FlowMapperImpl;
import org.openkilda.northbound.converter.SwitchMapper;
import org.openkilda.northbound.converter.SwitchMapperImpl;
import org.openkilda.northbound.dto.v1.switches.RulesSyncDto;
import org.openkilda.northbound.dto.v1.switches.RulesSyncResult;
import org.openkilda.northbound.dto.v1.switches.SwitchSyncResult;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.service.impl.SwitchServiceImpl;
import org.openkilda.northbound.utils.RequestCorrelationId;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.ExecutionException;

@RunWith(SpringRunner.class)
public class SwitchServiceTest {

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
        String correlationId = "sync-rules";
        RequestCorrelationId.create(correlationId);

        Long missingRule = 100L;
        Long excessRule = 101L;
        Long properRule = 10L;
        SwitchId switchId = new SwitchId(1L);

        RulesSyncEntry rulesSyncEntry = new RulesSyncEntry(singletonList(missingRule), singletonList(properRule),
                singletonList(excessRule), singletonList(missingRule), singletonList(excessRule));
        SwitchSyncResponse rules = new SwitchSyncResponse(rulesSyncEntry, MetersSyncEntry.builder().build());
        messageExchanger.mockResponse(correlationId, rules);

        RulesSyncResult result = switchService.syncRules(switchId).get();
        assertThat(result.getMissingRules(), is(singletonList(missingRule)));
        assertThat(result.getInstalledRules(), is(singletonList(missingRule)));
        assertThat(result.getExcessRules(), is(singletonList(excessRule)));
        assertThat(result.getInstalledRules(), is(singletonList(missingRule)));
    }

    @Test
    public void shouldSynchronizeSwitch() throws ExecutionException, InterruptedException {
        String correlationId = "not-sync-rules";
        RequestCorrelationId.create(correlationId);

        Long missingRule = 100L;
        Long excessRule = 101L;
        Long properRule = 10L;
        SwitchId switchId = new SwitchId(1L);

        RulesSyncEntry rulesEntry = new RulesSyncEntry(singletonList(missingRule), singletonList(properRule),
                singletonList(excessRule), singletonList(missingRule), singletonList(excessRule));
        InfoData validationResult = new SwitchSyncResponse(rulesEntry,
                MetersSyncEntry.builder().proper(singletonList(getMeterInfo(properRule))).build());
        messageExchanger.mockResponse(correlationId, validationResult);

        SwitchSyncResult result = switchService.syncSwitch(switchId, true).get();
        RulesSyncDto rules = result.getRules();
        assertThat(rules.getMissing(), is(singletonList(missingRule)));
        assertThat(rules.getInstalled(), is(singletonList(missingRule)));
        assertThat(rules.getExcess(), is(singletonList(excessRule)));
        assertThat(rules.getInstalled(), is(singletonList(missingRule)));
        assertThat(rules.getRemoved(), is(singletonList(excessRule)));
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
    @PropertySource({"classpath:northbound.properties"})
    static class Config {
        @Bean
        public MessagingChannel messagingChannel() {
            return new MessageExchanger();
        }

        @Bean
        public SwitchService switchService() {
            return new SwitchServiceImpl();
        }

        @Bean
        public SwitchMapper switchMapper() {
            return new SwitchMapperImpl();
        }

        @Bean
        public FlowMapper flowMapper() {
            return new FlowMapperImpl();
        }
    }

}
