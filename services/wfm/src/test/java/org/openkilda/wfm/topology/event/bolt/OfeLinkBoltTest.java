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

package org.openkilda.wfm.topology.event.bolt;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.model.DiscoveryLink;
import org.openkilda.messaging.model.DiscoveryLink.LinkState;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.AbstractStormTest;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.ConfigurationException;
import org.openkilda.wfm.protocol.KafkaMessage;
import org.openkilda.wfm.topology.OutputCollectorMock;
import org.openkilda.wfm.topology.event.OFEventWfmTopologyConfig;
import org.openkilda.wfm.topology.event.OfEventWfmTopology;
import org.openkilda.wfm.topology.event.model.Sync;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.TupleImpl;
import org.apache.storm.tuple.Values;
import org.apache.storm.utils.Utils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kohsuke.args4j.CmdLineException;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class OfeLinkBoltTest extends AbstractStormTest {

    private static final Integer TASK_SPEAKER_SPOUT_ID = 0;
    private static final Integer TASK_FL_MONITOR_ID = 1;
    private static final String STREAM_ID_INPUT = "input";

    private TopologyContext context;
    private OfeLinkBolt bolt;
    private OutputCollectorMock outputDelegate;
    private OFEventWfmTopologyConfig config;

    @BeforeClass
    public static void setupOnce() throws Exception {
        AbstractStormTest.startZooKafkaAndStorm();
    }

    @Before
    public void before() throws CmdLineException, ConfigurationException {
        OfEventWfmTopology manager = new OfEventWfmTopology(
                AbstractStormTest.makeLaunchEnvironment());
        config = manager.getConfig();
        bolt = new OfeLinkBolt(config);

        context = Mockito.mock(TopologyContext.class);

        Mockito.when(context.getComponentId(TASK_SPEAKER_SPOUT_ID))
                .thenReturn(ComponentId.SPEAKER_SPOUT.toString());
        Mockito.when(context.getComponentOutputFields(ComponentId.SPEAKER_SPOUT.toString(), STREAM_ID_INPUT))
                .thenReturn(KafkaMessage.FORMAT);

        Mockito.when(context.getComponentId(TASK_FL_MONITOR_ID))
                .thenReturn(FlMonitor.BOLT_ID);
        Mockito.when(context.getComponentOutputFields(FlMonitor.BOLT_ID, Utils.DEFAULT_STREAM_ID))
                .thenReturn(FlMonitor.STREAM_FIELDS);
        Mockito.when(context.getComponentOutputFields(FlMonitor.BOLT_ID, FlMonitor.STREAM_SYNC_ID))
                .thenReturn(FlMonitor.STREAM_SYNC_FIELDS);

        outputDelegate = Mockito.spy(new OutputCollectorMock());
        OutputCollector output = new OutputCollector(outputDelegate);

        bolt.prepare(stormConfig(), context, output);
    }

    @Test
    public void shouldNotResetDiscoveryStatusOnSync() throws Exception {
        // given
        DiscoveryLink testLink = new DiscoveryLink(new SwitchId("ff:01"), 2, new SwitchId("ff:02"), 2, 0, -1, true);
        Map<SwitchId, Set<DiscoveryLink>> links = Collections.singletonMap(
                testLink.getSource().getDatapath(), new HashSet<>(Collections.singletonList(testLink)));
        bolt.resetDiscoveryManager(links);

        // when
        Sync sync = new Sync();
        sync.addActivePort(new SwitchId("ff:01"), 2);
        Tuple tuple = new TupleImpl(context, new Values(sync, new CommandContext()),
                                    TASK_FL_MONITOR_ID, FlMonitor.STREAM_SYNC_ID);
        bolt.handleInput(tuple);

        // then
        List<DiscoveryLink> linksAfterSync = links.values()
                .stream()
                .flatMap(Set::stream)
                .collect(Collectors.toList());

        assertThat(linksAfterSync, contains(
                allOf(hasProperty("source", hasProperty("datapath", is(new SwitchId("ff:01")))),
                        hasProperty("destination", hasProperty("datapath", is(new SwitchId("ff:02")))),
                        hasProperty("state", is(LinkState.ACTIVE)))));
    }

    @Test
    public void shouldNotProcessLoopedIsl() throws Exception {
        final SwitchId switchId = new SwitchId("00:01");
        final int port = 1;
        DiscoveryLink discoveryLink = new DiscoveryLink(switchId, port, switchId, port, 0, -1, false);
        Map<SwitchId, Set<DiscoveryLink>> links = Collections.singletonMap(
                discoveryLink.getSource().getDatapath(), new HashSet<>(Collections.singletonList(discoveryLink)));
        bolt.resetDiscoveryManager(links);

        PathNode source = new PathNode(switchId, port, 0);
        PathNode destination = new PathNode(switchId, port, 1);
        IslInfoData isl = new IslInfoData(source, destination, IslChangeType.DISCOVERED);
        InfoMessage inputMessage = new InfoMessage(isl, 0, DEFAULT_CORRELATION_ID, Destination.WFM);
        Tuple tuple = new TupleImpl(context, new Values(inputMessage, new CommandContext()),
                                    TASK_FL_MONITOR_ID, Utils.DEFAULT_STREAM_ID);
        bolt.handleInput(tuple);

        assertFalse(discoveryLink.getState().isActive());
    }
}
