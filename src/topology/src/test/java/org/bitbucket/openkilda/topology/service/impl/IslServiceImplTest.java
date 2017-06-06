package org.bitbucket.openkilda.topology.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathNode;
import org.bitbucket.openkilda.messaging.info.event.SwitchEventType;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.bitbucket.openkilda.topology.TestConfig;
import org.bitbucket.openkilda.topology.domain.Isl;
import org.bitbucket.openkilda.topology.domain.repository.IslRepository;
import org.bitbucket.openkilda.topology.domain.repository.SwitchRepository;
import org.bitbucket.openkilda.topology.service.IslService;
import org.bitbucket.openkilda.topology.service.SwitchService;

import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class IslServiceImplTest {
    private static final String srcSwitchId = "00:00:00:00:00:01";
    private static final String dstSwitchId = "00:00:00:00:00:02";
    private static final String address = "127.0.0.1";
    private static final String name = "localhost";
    private static final String description = "Unknown";
    private static final SwitchInfoData srcData = new SwitchInfoData(srcSwitchId,
            SwitchEventType.ADDED, address, name, description);
    private static final SwitchInfoData dstData = new SwitchInfoData(dstSwitchId,
            SwitchEventType.ADDED, address, name, description);

    @Autowired
    SwitchRepository switchRepository;

    @Autowired
    IslRepository islRepository;

    @Autowired
    SwitchService switchService;

    @Autowired
    IslService islService;

    @Before
    public void setUp() throws Exception {
        switchService.add(srcData);
        switchService.add(dstData);
    }

    @After
    public void tearDown() throws Exception {
        islRepository.deleteAll();
        switchRepository.deleteAll();
    }

    @Test
    public void discoverIsl() throws Exception {
        PathNode srcNode = new PathNode(srcSwitchId, 1, 0);
        PathNode dstNode = new PathNode(dstSwitchId, 1, 1);
        List<PathNode> list = new ArrayList<>();
        list.add(srcNode);
        list.add(dstNode);

        IslInfoData forwardIsl = new IslInfoData(100L, list, 10000000L);
        islService.discoverLink(forwardIsl);

        Isl isl = islService.getLink(forwardIsl);
        assertNotNull(isl);
        assertEquals(100L, isl.getLatency());
    }

    @Test
    public void updateLinkLatency() throws Exception {
        PathNode srcNode = new PathNode(srcSwitchId, 1, 0);
        PathNode dstNode = new PathNode(dstSwitchId, 1, 1);
        List<PathNode> list = new ArrayList<>();
        list.add(srcNode);
        list.add(dstNode);

        IslInfoData forwardIsl = new IslInfoData(100L, list, 10000000L);
        islService.discoverLink(forwardIsl);

        Isl isl = islService.getLink(forwardIsl);
        assertNotNull(isl);
        assertEquals(100L, isl.getLatency());

        forwardIsl = new IslInfoData(200L, list, 10000000L);
        islService.discoverLink(forwardIsl);

        isl = islService.getLink(forwardIsl);
        assertNotNull(isl);
        assertEquals(200L, isl.getLatency());
    }

    @Test
    public void discoverBidirectionalIsl() throws Exception {
        PathNode srcNode = new PathNode(srcSwitchId, 1, 0);
        PathNode dstNode = new PathNode(dstSwitchId, 1, 1);
        List<PathNode> list = new ArrayList<>();
        list.add(srcNode);
        list.add(dstNode);

        IslInfoData forwardIsl = new IslInfoData(10L, list, 10000000L);
        IslInfoData reverseIsl = new IslInfoData(20L, Lists.reverse(list), 10000000L);

        islService.discoverLink(forwardIsl);
        islService.discoverLink(reverseIsl);

        Isl isl = islService.getLink(forwardIsl);
        assertNotNull(isl);
        assertEquals(10L, isl.getLatency());

        isl = islService.getLink(reverseIsl);
        assertNotNull(isl);
        assertEquals(20L, isl.getLatency());
    }

    @Test
    public void dropIsl() throws Exception {
        PathNode srcNode = new PathNode(srcSwitchId, 1, 0);
        PathNode dstNode = new PathNode(dstSwitchId, 1, 1);
        List<PathNode> list = new ArrayList<>();
        list.add(srcNode);
        list.add(dstNode);

        IslInfoData forwardIsl = new IslInfoData(100L, list, 10000000L);
        islService.discoverLink(forwardIsl);

        Isl isl = islService.getLink(forwardIsl);
        assertNotNull(isl);
        assertEquals(100L, isl.getLatency());

        islService.dropLink(forwardIsl);
        isl = islService.getLink(forwardIsl);
        assertNull(isl);
    }
}