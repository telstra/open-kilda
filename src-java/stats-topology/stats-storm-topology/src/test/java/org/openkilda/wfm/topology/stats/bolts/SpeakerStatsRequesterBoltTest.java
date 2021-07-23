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

package org.openkilda.wfm.topology.stats.bolts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.openkilda.wfm.AbstractBolt.FIELD_ID_CONTEXT;
import static org.openkilda.wfm.share.zk.ZooKeeperSpout.FIELD_ID_LIFECYCLE_EVENT;
import static org.openkilda.wfm.topology.stats.StatsTopology.ComponentId.TICK_BOLT;
import static org.openkilda.wfm.topology.stats.bolts.StatsRequesterBolt.GRPC_REQUEST_STREAM;
import static org.openkilda.wfm.topology.stats.bolts.StatsRequesterBolt.STATS_REQUEST_STREAM;

import org.openkilda.bluegreen.LifecycleEvent;
import org.openkilda.bluegreen.Signal;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.grpc.GetPacketInOutStatsRequest;
import org.openkilda.model.IpSocketAddress;
import org.openkilda.model.KildaFeatureToggles;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.context.PersistenceContextManager;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperSpout;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

@RunWith(MockitoJUnitRunner.class)
public class SpeakerStatsRequesterBoltTest {

    @Mock
    private PersistenceManager persistenceManager;
    @Mock
    private PersistenceContextManager persistenceContextManager;
    @Mock
    private RepositoryFactory repositoryFactory;
    @Mock
    private KildaFeatureTogglesRepository featureTogglesRepository;
    @Mock
    private SwitchRepository switchRepository;
    @Mock
    private TopologyContext topologyContext;
    @Mock
    private OutputCollector output;
    @Mock
    private Tuple input;
    @Mock
    private Tuple startTuple;

    @Before
    public void setup() {
        when(topologyContext.getThisTaskId()).thenReturn(1);
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
        when(repositoryFactory.createFeatureTogglesRepository()).thenReturn(featureTogglesRepository);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
        when(persistenceManager.getPersistenceContextManager()).thenReturn(persistenceContextManager);
        when(input.getSourceComponent()).thenReturn(TICK_BOLT.name());
        when(input.getFields()).thenReturn(new Fields());
        when(input.getValueByField(FIELD_ID_CONTEXT)).thenReturn("123");
        when(startTuple.getSourceComponent()).thenReturn(ZooKeeperSpout.SPOUT_ID);
        when(startTuple.getValueByField(FIELD_ID_LIFECYCLE_EVENT))
                .thenReturn(LifecycleEvent.builder().signal(Signal.START).build());
        when(startTuple.getFields()).thenReturn(new Fields());

        PersistenceProvider.makeDefault(persistenceManager);
    }

    @Test
    public void doNotRequestGrpcStatsIfToggleIsFalseTest() {
        KildaFeatureToggles featureToggles = new KildaFeatureToggles(KildaFeatureToggles.DEFAULTS);
        featureToggles.setCollectGrpcStats(false);

        when(featureTogglesRepository.getOrDefault()).thenReturn(featureToggles);

        runDoNotRequestGrpcStatsTest();
        verify(switchRepository, never()).findActive();
    }

    @Test
    public void doNotRequestGrpcStatsIfNoActiveSwitchesTest() {
        KildaFeatureToggles featureToggles = new KildaFeatureToggles(KildaFeatureToggles.DEFAULTS);
        featureToggles.setCollectGrpcStats(true);

        when(switchRepository.findActive()).thenReturn(Collections.emptyList());
        when(featureTogglesRepository.getOrDefault()).thenReturn(featureToggles);

        runDoNotRequestGrpcStatsTest();
        verify(switchRepository, times(1)).findActive();
    }

    @Test
    public void doNotRequestGrpcStatsIfNoNoviflowSwitchesTest() {
        KildaFeatureToggles featureToggles = new KildaFeatureToggles(KildaFeatureToggles.DEFAULTS);
        featureToggles.setCollectGrpcStats(true);
        Switch sw = Switch.builder()
                .switchId(new SwitchId(1))
                .build();
        sw.setOfDescriptionSoftware("some");

        when(switchRepository.findActive()).thenReturn(Collections.emptyList());
        when(featureTogglesRepository.getOrDefault()).thenReturn(featureToggles);

        runDoNotRequestGrpcStatsTest();
        verify(switchRepository, times(1)).findActive();
    }

    private void runDoNotRequestGrpcStatsTest() {
        StatsRequesterBolt statsRequesterBolt = new StatsRequesterBolt(persistenceManager, ZooKeeperSpout.SPOUT_ID);
        statsRequesterBolt.prepare(Collections.emptyMap(), topologyContext, output);
        statsRequesterBolt.execute(startTuple);
        verify(output).emit(eq(ZkStreams.ZK.toString()), any(Tuple.class), anyList());

        statsRequesterBolt.execute(input);

        verify(output, times(1)).emit(eq(STATS_REQUEST_STREAM), any(Tuple.class), anyList());
        verify(output, never()).emit(eq(GRPC_REQUEST_STREAM), any(Tuple.class), anyList());
    }

    @Test
    public void requestGrpcStatsForNoviflowSwitchesTest() {
        KildaFeatureToggles featureToggles = new KildaFeatureToggles(KildaFeatureToggles.DEFAULTS);
        featureToggles.setCollectGrpcStats(true);
        String address = "192.168.1.1";
        Switch sw = Switch.builder()
                .switchId(new SwitchId(1))
                .socketAddress(new IpSocketAddress(address, 20))
                .build();
        sw.setOfDescriptionSoftware("NW500.1.1");

        when(switchRepository.findActive()).thenReturn(Collections.singleton(sw));
        when(featureTogglesRepository.getOrDefault()).thenReturn(featureToggles);

        StatsRequesterBolt statsRequesterBolt = new StatsRequesterBolt(persistenceManager, ZooKeeperSpout.SPOUT_ID);
        statsRequesterBolt.prepare(Collections.emptyMap(), topologyContext, output);
        statsRequesterBolt.execute(startTuple);
        verify(output).emit(eq(ZkStreams.ZK.toString()), any(Tuple.class), anyList());

        statsRequesterBolt.execute(input);

        ArgumentCaptor<Values> values = ArgumentCaptor.forClass(Values.class);

        verify(output, times(1)).emit(eq(STATS_REQUEST_STREAM), any(Tuple.class), anyList());
        verify(output, times(1)).emit(eq(GRPC_REQUEST_STREAM), any(Tuple.class), values.capture());

        Values capturedValues = values.getValue();
        assertTrue(capturedValues.get(0) instanceof CommandMessage);
        CommandMessage commandMessage = (CommandMessage) capturedValues.get(0);
        assertTrue(commandMessage.getData() instanceof GetPacketInOutStatsRequest);
        GetPacketInOutStatsRequest request = (GetPacketInOutStatsRequest) commandMessage.getData();
        assertEquals(address, request.getAddress());
    }
}
