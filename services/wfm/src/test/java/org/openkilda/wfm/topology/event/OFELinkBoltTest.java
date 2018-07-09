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

package org.openkilda.wfm.topology.event;

import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.openkilda.wfm.topology.event.OFELinkBolt.STATE_ID_DISCOVERY;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.model.DiscoveryLink;
import org.openkilda.wfm.AbstractStormTest;
import org.openkilda.wfm.error.ConfigurationException;
import org.openkilda.wfm.protocol.KafkaMessage;
import org.openkilda.wfm.topology.OutputCollectorMock;
import org.openkilda.wfm.topology.event.OFELinkBolt.State;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.state.KeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.TupleImpl;
import org.apache.storm.tuple.Values;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.kohsuke.args4j.CmdLineException;
import org.mockito.Mockito;

import java.util.LinkedList;

public class OFELinkBoltTest extends AbstractStormTest {

    private static final Integer TASK_ID_BOLT = 0;
    private static final String STREAM_ID_INPUT = "input";

    private ObjectMapper objectMapper = new ObjectMapper();

    private TopologyContext context;
    private OFELinkBolt bolt;
    private OutputCollectorMock outputDelegate;
    private OFEventWfmTopologyConfig config;

    @Before
    public void before() throws CmdLineException, ConfigurationException {
        OFEventWFMTopology manager = new OFEventWFMTopology(
                AbstractStormTest.makeLaunchEnvironment());
        config = manager.getConfig();
        bolt = new OFELinkBolt(config);

        context = Mockito.mock(TopologyContext.class);

        Mockito.when(context.getComponentId(TASK_ID_BOLT))
                .thenReturn(OFEventWFMTopology.DISCO_SPOUT_ID);
        Mockito.when(context.getComponentOutputFields(OFEventWFMTopology.DISCO_SPOUT_ID, STREAM_ID_INPUT))
                .thenReturn(KafkaMessage.FORMAT);

        outputDelegate = Mockito.spy(new OutputCollectorMock());
        OutputCollector output = new OutputCollector(outputDelegate);

        bolt.prepare(stormConfig(), context, output);
        bolt.initState(new InMemoryKeyValueState<>());
    }

    @Test
    public void invalidJsonForDiscoveryFilter() throws JsonProcessingException {
        Tuple tuple = new TupleImpl(context, new Values("{\"corrupted-json"), TASK_ID_BOLT,
                STREAM_ID_INPUT);
        bolt.doWork(tuple);

        Mockito.verify(outputDelegate).ack(tuple);
    }

    @Test
    public void shouldNotResetDiscoveryStatusOnSync() throws JsonProcessingException {
        // given
        DiscoveryLink testLink = new DiscoveryLink("sw1", 2, "sw2", 2, 0, -1, true);

        KeyValueState<String, Object> boltState = new InMemoryKeyValueState<>();
        boltState.put(STATE_ID_DISCOVERY, new LinkedList<DiscoveryLink>(singletonList(testLink)));
        bolt.initState(boltState);

        // set the state to WAIT_SYNC
        bolt.state = State.SYNC_IN_PROGRESS;

        // when
        PortInfoData dumpPortData = new PortInfoData("sw1", 2, PortChangeType.UP);
        InfoMessage dumpBeginMessage = new InfoMessage(dumpPortData, 0, DEFAULT_CORRELATION_ID, Destination.WFM);
        Tuple tuple = new TupleImpl(context, new Values(objectMapper.writeValueAsString(dumpBeginMessage)),
                TASK_ID_BOLT, STREAM_ID_INPUT);
        bolt.doWork(tuple);

        // then
        @SuppressWarnings("unchecked")
        LinkedList<DiscoveryLink> stateAfterSync = (LinkedList<DiscoveryLink>) boltState.get(STATE_ID_DISCOVERY);
        assertThat(stateAfterSync, Matchers.contains(
                allOf(hasProperty("source", hasProperty("datapath", is("sw1"))),
                        hasProperty("destination", hasProperty("datapath", is("sw2"))),
                        hasProperty("active", is(true)))));
    }
}
