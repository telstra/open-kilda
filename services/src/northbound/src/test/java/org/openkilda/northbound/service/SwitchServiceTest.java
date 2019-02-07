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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.switches.SyncRulesResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.MessageExchanger;
import org.openkilda.northbound.config.KafkaConfig;
import org.openkilda.northbound.converter.SwitchMapper;
import org.openkilda.northbound.converter.SwitchMapperImpl;
import org.openkilda.northbound.dto.switches.RulesSyncResult;
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

import java.util.Collections;
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

        SyncRulesResponse rules = new SyncRulesResponse(Collections.singletonList(missingRule),
                Collections.singletonList(properRule), Collections.singletonList(excessRule),
                Collections.singletonList(missingRule));
        messageExchanger.mockResponse(correlationId, rules);

        RulesSyncResult result = switchService.syncRules(switchId).get();
        assertThat(result.getMissingRules(), is(Collections.singletonList(missingRule)));
        assertThat(result.getInstalledRules(), is(Collections.singletonList(missingRule)));
        assertThat(result.getExcessRules(), is(Collections.singletonList(excessRule)));
    }

    @Test
    public void shouldNotSynchronizeRulesIfNotMissingRules() throws ExecutionException, InterruptedException {
        String correlationId = "not-sync-rules";
        RequestCorrelationId.create(correlationId);

        Long excessRule = 101L;
        Long properRule = 10L;
        SwitchId switchId = new SwitchId(1L);

        // the switch has 1 excess, 1 proper and no missing rules
        InfoData validationResult = new SyncRulesResponse(Collections.emptyList(),
                Collections.singletonList(properRule), Collections.singletonList(excessRule), Collections.emptyList());
        messageExchanger.mockResponse(correlationId, validationResult);

        RulesSyncResult result = switchService.syncRules(switchId).get();
        assertThat(result.getMissingRules(), is(empty()));
        assertThat(result.getExcessRules(), is(Collections.singletonList(excessRule)));
        assertThat(result.getProperRules(), is(Collections.singletonList(properRule)));
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
    }

}
