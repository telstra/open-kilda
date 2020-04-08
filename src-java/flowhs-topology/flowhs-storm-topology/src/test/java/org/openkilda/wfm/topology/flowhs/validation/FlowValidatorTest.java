/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.validation;

import static org.mockito.Mockito.mock;

import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import org.junit.BeforeClass;
import org.junit.Test;

public class FlowValidatorTest {
    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);

    public static FlowValidator flowValidator;

    @BeforeClass
    public static void setup() {
        FlowRepository flowRepository = mock(FlowRepository.class);
        SwitchRepository switchRepository = mock(SwitchRepository.class);
        IslRepository islRepository = mock(IslRepository.class);
        SwitchPropertiesRepository switchPropertiesRepository = mock(SwitchPropertiesRepository.class);
        flowValidator = new FlowValidator(flowRepository, switchRepository, islRepository, switchPropertiesRepository);
    }

    @Test(expected = InvalidFlowException.class)
    public void shouldFailOnSwapWhenEqualsEndpointsOnFirstFlow() throws InvalidFlowException {
        RequestedFlow firstFlow = RequestedFlow.builder()
                .flowId("firstFlow")
                .srcSwitch(SWITCH_ID_1)
                .srcPort(10)
                .srcVlan(11)
                .destSwitch(SWITCH_ID_2)
                .destPort(10)
                .destVlan(11)
                .build();

        RequestedFlow secondFlow = RequestedFlow.builder()
                .flowId("secondFlow")
                .srcSwitch(SWITCH_ID_2)
                .destSwitch(SWITCH_ID_2)
                .build();

        flowValidator.checkForEqualsEndpoints(firstFlow, secondFlow);
    }

    @Test(expected = InvalidFlowException.class)
    public void shouldFailOnSwapWhenEqualsEndpointsOnSecondFlow() throws InvalidFlowException {
        RequestedFlow firstFlow = RequestedFlow.builder()
                .flowId("firstFlow")
                .srcSwitch(SWITCH_ID_2)
                .srcPort(10)
                .srcVlan(11)
                .destSwitch(SWITCH_ID_2)
                .destPort(12)
                .build();

        RequestedFlow secondFlow = RequestedFlow.builder()
                .flowId("secondFlow")
                .srcSwitch(SWITCH_ID_1)
                .srcPort(10)
                .srcVlan(11)
                .destSwitch(SWITCH_ID_1)
                .destPort(10)
                .destVlan(11)
                .build();

        flowValidator.checkForEqualsEndpoints(firstFlow, secondFlow);
    }

    @Test(expected = InvalidFlowException.class)
    public void shouldFailOnSwapWhenEqualsEndpointsOnFirstAndSecondFlow() throws InvalidFlowException {
        RequestedFlow firstFlow = RequestedFlow.builder()
                .flowId("firstFlow")
                .srcSwitch(SWITCH_ID_1)
                .srcPort(10)
                .srcVlan(11)
                .destSwitch(SWITCH_ID_2)
                .destPort(12)
                .destVlan(13)
                .build();

        RequestedFlow secondFlow = RequestedFlow.builder()
                .flowId("secondFlow")
                .srcSwitch(SWITCH_ID_1)
                .srcPort(10)
                .srcVlan(11)
                .destSwitch(SWITCH_ID_2)
                .destPort(12)
                .destVlan(13)
                .build();

        flowValidator.checkForEqualsEndpoints(firstFlow, secondFlow);
    }
}
