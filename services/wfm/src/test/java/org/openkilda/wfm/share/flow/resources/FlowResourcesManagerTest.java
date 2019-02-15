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

package org.openkilda.wfm.share.flow.resources;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.openkilda.config.provider.PropertiesBasedConfigurationProvider;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.UnidirectionalFlow;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.Neo4jBasedTest;
import org.openkilda.wfm.share.flow.resources.transitvlan.TransitVlanResources;
import org.openkilda.wfm.share.mappers.FlowMapper;

import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

public class FlowResourcesManagerTest extends Neo4jBasedTest {

    private final FlowDto firstFlow = new FlowDto("first-flow", 1, false, "first-flow",
            new SwitchId("ff:01"), 11, 100,
            new SwitchId("ff:03"), 11, 200);
    private final FlowDto secondFlow = new FlowDto("second-flow", 1, false, "second-flow",
            new SwitchId("ff:05"), 12, 100,
            new SwitchId("ff:03"), 12, 200);
    private final FlowDto thirdFlow = new FlowDto("third-flow", 0, true, "third-flow",
            new SwitchId("ff:03"), 21, 100,
            new SwitchId("ff:03"), 22, 200);
    private final FlowDto fourthFlow = new FlowDto("fourth-flow", 0, true, "fourth-flow",
            new SwitchId("ff:04"), 21, 100,
            new SwitchId("ff:05"), 22, 200);

    private FlowResourcesManager resourcesManager;
    private FlowResourcesConfig flowResourcesConfig;

    private Switch switch1 = Switch.builder().switchId(new SwitchId("ff:01")).build();
    private Switch switch3 = Switch.builder().switchId(new SwitchId("ff:03")).build();

    @Before
    public void setUp() {
        Properties configProps = new Properties();
        configProps.setProperty("flow.meter-id.max", "40");
        configProps.setProperty("flow.vlan.max", "50");

        PropertiesBasedConfigurationProvider configurationProvider =
                new PropertiesBasedConfigurationProvider(configProps);
        flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        resourcesManager = new FlowResourcesManager(persistenceManager, flowResourcesConfig);

        SwitchRepository switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        switchRepository.findAll().forEach(switchRepository::delete);
        switchRepository.createOrUpdate(switch1);
        switchRepository.createOrUpdate(switch3);
    }

    @Test
    public void shouldAllocateForFlow() throws ResourceAllocationException {
        UnidirectionalFlow flow = FlowMapper.INSTANCE.map(firstFlow);

        FlowResources flowResources = resourcesManager.allocateFlowResources(flow.getFlowEntity());

        assertEquals(1, flowResources.getUnmaskedCookie());
        assertEquals(2, ((TransitVlanResources) flowResources.getForward().getEncapsulationResources())
                .getTransitVlan().getVlan());
        assertEquals(32, flowResources.getForward().getMeterId().getValue());

        assertEquals(3, ((TransitVlanResources) flowResources.getReverse().getEncapsulationResources())
                .getTransitVlan().getVlan());
        assertEquals(32, flowResources.getReverse().getMeterId().getValue());
    }

    @Test
    public void shouldNotImmediatelyReuseResources() throws ResourceAllocationException {
        UnidirectionalFlow flow = FlowMapper.INSTANCE.map(firstFlow);

        FlowResources flowResources = resourcesManager.allocateFlowResources(flow.getFlowEntity());
        resourcesManager.deallocateFlowResources(flow.getFlowEntity(), flowResources);

        flowResources = resourcesManager.allocateFlowResources(flow.getFlowEntity());

        assertEquals(1, flowResources.getUnmaskedCookie());
        assertEquals(2, ((TransitVlanResources) flowResources.getForward().getEncapsulationResources())
                .getTransitVlan().getVlan());
        assertEquals(32, flowResources.getForward().getMeterId().getValue());

        assertEquals(3, ((TransitVlanResources) flowResources.getReverse().getEncapsulationResources())
                .getTransitVlan().getVlan());
        assertEquals(32, flowResources.getReverse().getMeterId().getValue());
    }

    @Test
    public void shouldAllocateForNoBandwidthFlow() throws ResourceAllocationException {
        UnidirectionalFlow flow = FlowMapper.INSTANCE.map(fourthFlow);

        FlowResources flowResources = resourcesManager.allocateFlowResources(flow.getFlowEntity());


        assertEquals(1, flowResources.getUnmaskedCookie());
        assertEquals(2, ((TransitVlanResources) flowResources.getForward().getEncapsulationResources())
                .getTransitVlan().getVlan());
        assertNull(flowResources.getForward().getMeterId());

        assertEquals(3, ((TransitVlanResources) flowResources.getReverse().getEncapsulationResources())
                .getTransitVlan().getVlan());
        assertNull(flowResources.getReverse().getMeterId());
    }

    @Test
    public void shouldNotConsumeVlansForSingleSwitchFlows() throws ResourceAllocationException {
        /*
         * This is to validate that single switch flows don't consume transit vlans.
         */

        // for forward and reverse flows 2 t-vlans are allocated, so just try max / 2 + 1 attempts
        final int attemps =
                (flowResourcesConfig.getMaxFlowTransitVlan() - flowResourcesConfig.getMinFlowTransitVlan()) / 2 + 1;

        for (int i = 0; i < attemps; i++) {
            thirdFlow.setFlowId(format("third-flow-%d", i));

            UnidirectionalFlow flow3 = FlowMapper.INSTANCE.map(thirdFlow);
            resourcesManager.allocateFlowResources(flow3.getFlowEntity());
        }
    }

    @Test
    public void shouldNotConsumeMetersForUnmeteredFlows() throws ResourceAllocationException {
        // for forward and reverse flows 2 meters are allocated, so just try max / 2 + 1 attempts
        final int attemps = (flowResourcesConfig.getMaxFlowMeterId() - flowResourcesConfig.getMinFlowMeterId()) / 2 + 1;

        for (int i = 0; i < attemps; i++) {
            fourthFlow.setFlowId(format("fourth-flow-%d", i));

            UnidirectionalFlow flow4 = FlowMapper.INSTANCE.map(fourthFlow);
            resourcesManager.allocateFlowResources(flow4.getFlowEntity());
        }
    }
}
