/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.switchmanager.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.openkilda.messaging.command.switches.DeleteRulesAction.OVERWRITE_DEFAULTS;

import org.openkilda.floodlight.api.request.rulemanager.FlowCommand;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.messaging.command.switches.SwitchRulesDeleteRequest;
import org.openkilda.model.SwitchId;
import org.openkilda.model.bitops.BitField;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.CookieBase;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.rulemanager.DataAdapter;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.FlowSpeakerData.FlowSpeakerDataBuilder;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.wfm.topology.switchmanager.bolt.SwitchManagerHub;
import org.openkilda.wfm.topology.switchmanager.bolt.SwitchManagerHub.OfCommandAction;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
public class SwitchRuleServiceTest {
    private static final String KEY = "some key";
    private static final BitField SERVICE_BIT_FIELD = new BitField(0x8000_0000_0000_0000L);
    private static final BitField FIRST_FLOW_BIT_FIELD = new BitField(0x2000_0000_0000_0000L);
    private static final BitField SECOND_FLOW_BIT_FIELD = new BitField(0x4000_0000_0000_0000L);

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private SwitchRepository switchRepository;
    @Mock
    private PersistenceManager persistenceManager;
    @Mock
    private RepositoryFactory repositoryFactory;
    @Mock
    private SwitchManagerCarrier carrier;
    @Mock
    private RuleManager ruleManager;
    @Mock
    private FlowPathRepository flowPathRepository;
    @Mock
    private FlowMirrorPathRepository flowMirrorPathRepository;

    @Captor
    private ArgumentCaptor<SwitchManagerHub.OfCommandAction> commandsCaptor;
    @Captor
    private ArgumentCaptor<List<OfCommand>> listCaptor;

    @Parameter
    public List<SpeakerData> speakerDataList;
    @Parameter(value = 1)
    public Integer expectedServiceRulesCount;

    private SwitchRuleService switchRuleService;

    @Before
    public void setUp() {
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
        when(switchRepository.exists(any(SwitchId.class))).thenReturn(true);
        when(repositoryFactory.createFlowPathRepository()).thenReturn(flowPathRepository);
        when(repositoryFactory.createFlowMirrorPathRepository()).thenReturn(flowMirrorPathRepository);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
        when(flowPathRepository.findByEndpointSwitch(any(SwitchId.class))).thenReturn(Collections.emptyList());
        when(flowPathRepository.findBySegmentSwitch(any(SwitchId.class))).thenReturn(Collections.emptyList());
        when(flowMirrorPathRepository.findByEndpointSwitch(any(SwitchId.class))).thenReturn(Collections.emptyList());
        when(flowMirrorPathRepository.findBySegmentSwitch(any(SwitchId.class))).thenReturn(Collections.emptyList());
        when(ruleManager.buildRulesForSwitch(any(SwitchId.class), any(DataAdapter.class)))
                .thenReturn(speakerDataList);

        switchRuleService = new SwitchRuleService(carrier, persistenceManager, ruleManager);
    }

    @Test
    public void shouldDeleteOnlyServiceRulesWhenOverwriteDefaultsAction() {
        SwitchRulesDeleteRequest request = new SwitchRulesDeleteRequest(
                new SwitchId("1"),
                OVERWRITE_DEFAULTS,
                null);

        switchRuleService.deleteRules(KEY, request);

        verify(carrier).sendOfCommandsToSpeaker(anyString(), listCaptor.capture(),
                commandsCaptor.capture(), any(SwitchId.class));

        List<CookieBase> cookies = listCaptor.getValue().stream()
                .map(command -> (FlowCommand) command)
                .map(FlowCommand::getData)
                .map(FlowSpeakerData::getCookie)
                .collect(Collectors.toList());

        assertAll(
                () -> assertThat(commandsCaptor.getValue(), equalTo(OfCommandAction.DELETE)),
                () -> assertThat(cookies.size(), equalTo(expectedServiceRulesCount)),
                () -> assertThat(cookies, everyItem(hasProperty("serviceFlag", equalTo(true))))
        );
    }

    /**
     * Generating test data.
     */
    @Parameters(name = "ServiceRules count = {1}")
    public static Collection<Object[]> data() {
        FlowSpeakerDataBuilder<?, ?> speakerDataBuilder = FlowSpeakerData.builder();

        FlowSpeakerData defaultRule = speakerDataBuilder
                .cookie(new Cookie(SERVICE_BIT_FIELD.getMask()))
                .build();
        FlowSpeakerData flowRule1 = speakerDataBuilder
                .cookie(new Cookie(FIRST_FLOW_BIT_FIELD.getMask()))
                .build();
        FlowSpeakerData flowRule2 = speakerDataBuilder
                .cookie(new Cookie(SECOND_FLOW_BIT_FIELD.getMask()))
                .build();

        return Arrays.asList(new Object[][]{
                {new ArrayList<>(Arrays.asList(defaultRule, defaultRule, flowRule1, flowRule2)), 2},
                {new ArrayList<>(Arrays.asList(defaultRule, defaultRule, flowRule1, defaultRule)), 3},
                {new ArrayList<>(Arrays.asList(defaultRule, flowRule1, flowRule2)), 1},
                {new ArrayList<>(Arrays.asList(defaultRule, defaultRule)), 2},
                {new ArrayList<>(Arrays.asList(flowRule1, flowRule2)), 0}
        });
    }
}
