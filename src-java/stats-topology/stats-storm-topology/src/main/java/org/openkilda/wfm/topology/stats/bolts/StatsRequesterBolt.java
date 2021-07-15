/* Copyright 2021 Telstra Open Source
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

import static org.openkilda.wfm.topology.stats.StatsStreamType.GRPC_REQUEST;
import static org.openkilda.wfm.topology.stats.StatsStreamType.STATS_REQUEST;

import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.grpc.GetPacketInOutStatsRequest;
import org.openkilda.messaging.command.stats.StatsRequest;
import org.openkilda.model.Switch;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.stats.StatsComponentType;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Optional;

public class StatsRequesterBolt extends AbstractBolt {
    public static final String ZOOKEEPER_STREAM = ZkStreams.ZK.toString();

    private final PersistenceManager persistenceManager;
    private transient SwitchRepository switchRepository;
    private transient KildaFeatureTogglesRepository featureTogglesRepository;

    public StatsRequesterBolt(PersistenceManager persistenceManager, String lifeCycleEventSourceComponent) {
        super(lifeCycleEventSourceComponent);
        this.persistenceManager = persistenceManager;
    }

    @Override
    protected void init() {
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        featureTogglesRepository = persistenceManager.getRepositoryFactory().createFeatureTogglesRepository();
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(STATS_REQUEST.name(), new Fields(AbstractTopology.MESSAGE_FIELD));
        declarer.declareStream(GRPC_REQUEST.name(), new Fields(AbstractTopology.MESSAGE_FIELD));
        declarer.declareStream(ZOOKEEPER_STREAM, new Fields(ZooKeeperBolt.FIELD_ID_STATE,
                ZooKeeperBolt.FIELD_ID_CONTEXT));
    }

    @Override
    protected void handleInput(Tuple input) {
        if (active) {
            if (StatsComponentType.TICK_BOLT.name().equals(input.getSourceComponent())) {
                sendStatsRequest(input);
            } else {
                unhandledInput(input);
            }
        }
    }

    private void sendStatsRequest(Tuple input) {
        CommandMessage statsRequest = new CommandMessage(
                new StatsRequest(), System.currentTimeMillis(), getCommandContext().getCorrelationId());
        Values values = new Values(statsRequest);
        emit(STATS_REQUEST.name(), input, values);

        if (featureTogglesRepository.getOrDefault().getCollectGrpcStats()) {
            Collection<Switch> switches = switchRepository.findActive();
            for (Switch sw : switches) {
                if (sw.getOfDescriptionSoftware() != null && Switch.isNoviflowSwitch(sw.getOfDescriptionSoftware())) {
                    emitGrpcStatsRequest(input, sw);
                }
            }
        }
    }

    private void emitGrpcStatsRequest(Tuple input, Switch sw) {
        Optional<String> address = Optional.ofNullable(sw.getSocketAddress())
                .map(InetSocketAddress::getAddress)
                .map(InetAddress::getHostAddress);

        if (!address.isPresent()) {
            return;
        }

        GetPacketInOutStatsRequest packetInOutStatsRequest = new GetPacketInOutStatsRequest(
                address.get(), sw.getSwitchId());
        String correlationId = getCommandContext().getCorrelationId().concat(" : ").concat(address.get());
        CommandMessage grpcRequest = new CommandMessage(
                packetInOutStatsRequest, System.currentTimeMillis(), correlationId);

        emit(GRPC_REQUEST.name(), input, new Values(grpcRequest));
    }
}
