package org.bitbucket.openkilda.topology.service.impl;

import static org.bitbucket.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.bitbucket.openkilda.topology.TestUtils.dstSwitchId;
import static org.bitbucket.openkilda.topology.TestUtils.firstTransitSwitchId;
import static org.bitbucket.openkilda.topology.TestUtils.secondTransitSwitchId;
import static org.bitbucket.openkilda.topology.TestUtils.srcSwitchId;
import static org.junit.Assert.*;

import org.bitbucket.openkilda.topology.TestConfig;
import org.bitbucket.openkilda.topology.TestUtils;
import org.bitbucket.openkilda.topology.domain.repository.TopologyRepository;
import org.bitbucket.openkilda.topology.model.Node;
import org.bitbucket.openkilda.topology.model.Topology;
import org.bitbucket.openkilda.topology.service.IslService;
import org.bitbucket.openkilda.topology.service.SwitchService;
import org.bitbucket.openkilda.topology.service.TopologyService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@ActiveProfiles("test")
@ContextConfiguration(classes = TestConfig.class)
public class TopologyServiceImplTest {
    @Autowired
    SwitchService switchService;

    @Autowired
    IslService islService;

    @Autowired
    TopologyService topologyService;

    @Autowired
    TopologyRepository topologyRepository;

    @Before
    public void setUp() {
        TestUtils.createTopology(switchService, islService);
    }

    @After
    public void tearDown() {
        topologyRepository.clear();
    }

    @Test
    public void clear() throws Exception {
        Topology topology = topologyService.clear(DEFAULT_CORRELATION_ID);
        assertEquals(new Topology(new ArrayList<>()), topology);
        System.out.println(topology.toString());
    }

    @Test
    public void network() throws Exception {
        List<Node> nodes = Arrays.asList(
                new Node(srcSwitchId, Collections.singletonList(firstTransitSwitchId)),
                new Node(firstTransitSwitchId, Arrays.asList(srcSwitchId, secondTransitSwitchId)),
                new Node(secondTransitSwitchId, Arrays.asList(firstTransitSwitchId, dstSwitchId)),
                new Node(dstSwitchId, Collections.singletonList(secondTransitSwitchId)));

        Topology topology = topologyService.network(DEFAULT_CORRELATION_ID);

        assertEquals(new Topology(nodes), topology);
        System.out.println(topology.toString());
    }
}
