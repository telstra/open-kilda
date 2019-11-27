/* Copyright 2019 Telstra Open Source
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

package org.openkilda.pce;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.Isl;
import org.openkilda.model.IslConfig;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.impl.AvailableNetwork;
import org.openkilda.pce.model.Edge;
import org.openkilda.pce.model.Node;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

public class AvailableNetworkFactoryTest {

    @Mock
    private PathComputerConfig config;
    @Mock
    RepositoryFactory repositoryFactory;
    @Mock
    private IslRepository islRepository;
    @Mock
    private FlowPathRepository flowPathRepository;

    private AvailableNetworkFactory availableNetworkFactory;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        when(repositoryFactory.createIslRepository()).thenReturn(islRepository);
        when(repositoryFactory.createFlowPathRepository()).thenReturn(flowPathRepository);

        availableNetworkFactory = new AvailableNetworkFactory(config, repositoryFactory);
    }

    @Test
    public void shouldBuildAvailableNetworkUsingCostStrategy() throws RecoverableException {
        Flow flow = getFlow(false);
        Isl isl = getIsl(flow);

        when(config.getNetworkStrategy()).thenReturn("COST");

        when(islRepository.findActiveWithAvailableBandwidth(flow.getBandwidth(), flow.getEncapsulationType()))
                .thenReturn(Collections.singletonList(isl));

        AvailableNetwork availableNetwork = availableNetworkFactory.getAvailableNetwork(flow, Collections.emptyList());

        assertAvailableNetworkIsCorrect(isl, availableNetwork);
    }

    @Test
    public void shouldBuildAvailableNetworkUsingCostStrategyWithIgnoreBandwidth() throws RecoverableException {
        Flow flow = getFlow(true);
        Isl isl = getIsl(flow);

        when(config.getNetworkStrategy()).thenReturn("COST");

        when(islRepository.findAllActiveByEncapsulationType(flow.getEncapsulationType()))
                .thenReturn(Collections.singletonList(isl));

        AvailableNetwork availableNetwork = availableNetworkFactory.getAvailableNetwork(flow, Collections.emptyList());

        assertAvailableNetworkIsCorrect(isl, availableNetwork);
    }

    @Test
    public void shouldBuildAvailableNetworkUsingSymmetricCostStrategy() throws RecoverableException {
        Flow flow = getFlow(false);
        Isl isl = getIsl(flow);

        when(config.getNetworkStrategy()).thenReturn("SYMMETRIC_COST");

        when(islRepository.findSymmetricActiveWithAvailableBandwidth(flow.getBandwidth(), flow.getEncapsulationType()))
                .thenReturn(Collections.singletonList(isl));

        AvailableNetwork availableNetwork = availableNetworkFactory.getAvailableNetwork(flow, Collections.emptyList());

        assertAvailableNetworkIsCorrect(isl, availableNetwork);
    }

    @Test
    public void shouldBuildAvailableNetworkUsingSymmetricCostStrategyWithIgnoreBandwidth() throws RecoverableException {
        Flow flow = getFlow(true);
        Isl isl = getIsl(flow);

        when(config.getNetworkStrategy()).thenReturn("SYMMETRIC_COST");

        when(islRepository.findAllActiveByEncapsulationType(flow.getEncapsulationType()))
                .thenReturn(Collections.singletonList(isl));

        AvailableNetwork availableNetwork = availableNetworkFactory.getAvailableNetwork(flow, Collections.emptyList());

        assertAvailableNetworkIsCorrect(isl, availableNetwork);
    }

    private static Flow getFlow(boolean ignoreBandwidth) {
        return Flow.builder()
                .flowId("test-id")
                .srcSwitch(Switch.builder().switchId(new SwitchId("1")).build())
                .destSwitch(Switch.builder().switchId(new SwitchId("2")).build())
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .bandwidth(100)
                .ignoreBandwidth(ignoreBandwidth)
                .build();
    }

    private static Isl getIsl(Flow flow) {
        Isl isl = Isl.builder()
                .srcSwitch(flow.getSrcSwitch())
                .srcPort(1)
                .destSwitch(flow.getDestSwitch())
                .destPort(2)
                .cost(22)
                .latency(33L)
                .availableBandwidth(44)
                .build();
        isl.setIslConfig(IslConfig.builder().build());
        return isl;
    }

    private static void assertAvailableNetworkIsCorrect(Isl isl, AvailableNetwork availableNetwork) {
        Node src = availableNetwork.getSwitch(isl.getSrcSwitch().getSwitchId());
        assertNotNull(src);
        assertEquals(1, src.getOutgoingLinks().size());
        Edge edge = src.getOutgoingLinks().iterator().next();
        assertEquals(isl.getSrcSwitch().getSwitchId(), edge.getSrcSwitch().getSwitchId());
        assertEquals(isl.getSrcPort(), edge.getSrcPort());
        assertEquals(isl.getDestSwitch().getSwitchId(), edge.getDestSwitch().getSwitchId());
        assertEquals(isl.getDestPort(), edge.getDestPort());
        assertEquals(isl.getCost(), edge.getCost());
        assertEquals(isl.getLatency(), edge.getLatency());
        assertEquals(isl.getAvailableBandwidth(), edge.getAvailableBandwidth());
        Node dst = availableNetwork.getSwitch(isl.getDestSwitch().getSwitchId());
        assertNotNull(dst);
        assertEquals(1, dst.getIncomingLinks().size());
    }
}
