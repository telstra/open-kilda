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

package org.openkilda.wfm.topology.isllatency.bolts;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslOneWayLatency;
import org.openkilda.messaging.info.event.IslRoundTripLatency;
import org.openkilda.model.Isl;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.IllegalIslStateException;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.error.JsonDecodeException;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.isllatency.LatencyAction;
import org.openkilda.wfm.topology.isllatency.service.DecisionMakerService;
import org.openkilda.wfm.topology.isllatency.service.IslLatencyService;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;

import java.io.IOException;

@Slf4j
public class IslLatencyBolt extends AbstractBolt {
    private final PersistenceManager persistenceManager;
    private transient IslLatencyService islLatencyService;
    private transient DecisionMakerService decisionMakerService;

    public IslLatencyBolt(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    protected void init() {
        TransactionManager transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        islLatencyService = new IslLatencyService(transactionManager, repositoryFactory);
        decisionMakerService = new DecisionMakerService(repositoryFactory);
    }

    private String getJson(Tuple tuple) {
        return tuple.getString(0);
    }

    private Message getMessage(String json) throws JsonDecodeException {
        try {
            return Utils.MAPPER.readValue(json, Message.class);
        } catch (IOException e) {
            log.error("Could not deserialize message={}", json, e);
            throw new JsonDecodeException(Message.class, json, e);
        }
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        String json = getJson(input);
        Message message = getMessage(json);

        if (message instanceof InfoMessage) {
            InfoData data = ((InfoMessage) message).getData();
            if (data instanceof IslRoundTripLatency) {
                handleRoundTripIslLatency((IslRoundTripLatency) data);
            } else if (data instanceof IslOneWayLatency) {
                handleOneWayIslLatency((IslOneWayLatency) data);
            } else {
                unhandledInput(input);
            }
        } else {
            unhandledInput(input);
        }
    }

    private void handleRoundTripIslLatency(IslRoundTripLatency data) {
        try {
            Isl isl = islLatencyService.setIslLatencyBySourceEndpoint(
                    data.getSrcSwitchId(),
                    data.getSrcPortNo(),
                    data.getLatency());
            log.info("Set latency for ISL {}_{} ===( {} ms )===> {}_{}. Packet id:{}",
                    isl.getSrcSwitch().getSwitchId(), isl.getSrcPort(), isl.getLatency(),
                    isl.getDestSwitch().getSwitchId(), isl.getDestPort(), data.getPacketId());
        } catch (IslNotFoundException e) {
            log.warn("Couldn't set latency {} for ISL with source {}_{}. Packet id:{}. There is no such ISL",
                    data.getLatency(), data.getSrcSwitchId(), data.getSrcPortNo(), data.getPacketId());
        } catch (IllegalIslStateException e) {
            log.warn("Couldn't set latency {} for ISL with source {}_{}. Packet id:{}. ISL is illegal state",
                    data.getLatency(), data.getSrcSwitchId(), data.getSrcPortNo(), data.getPacketId());
        }
    }

    private void handleOneWayIslLatency(IslOneWayLatency data) {
        LatencyAction decision = decisionMakerService.handleOneWayIslLatency(data);
        switch (decision) {
            case USE_ONE_WAY_LATENCY:
                setOneWayLatency(data);
                break;
            case COPY_REVERSE_ROUND_TRIP_LATENCY:
                copyRoundTripLatencyFromReverseIsl(data);
                break;
            case DO_NOTHING:
                // do nothing
                break;
            default:
                break;
        }
    }

    private void copyRoundTripLatencyFromReverseIsl(IslOneWayLatency data) {
        try {
            Isl isl = islLatencyService.copyLatencyFromReverseIsl(
                    data.getSrcSwitchId(), data.getSrcPortNo(), data.getDstSwitchId(), data.getDstPortNo());
            log.info("Copy latency from ISL {}_{} ===( {} ms )===> {}_{} to the reverse ISL. Packet id:{}",
                    isl.getSrcSwitch().getSwitchId(), isl.getSrcPort(), isl.getLatency(),
                    isl.getDestSwitch().getSwitchId(), isl.getDestPort(), data.getPacketId());
        } catch (IslNotFoundException e) {
            log.warn("Couldn't set latency {} for ISL {}_{} ===> {}_{}. Packet id:{}. There is no such ISL",
                    data.getLatency(), data.getSrcSwitchId(), data.getSrcPortNo(),
                    data.getDstSwitchId(), data.getDstPortNo(), data.getPacketId());
        }
    }

    private void setOneWayLatency(IslOneWayLatency data) {
        boolean updated = islLatencyService.setIslLatencyBySourceAndDestinationEndpoint(
                    data.getSrcSwitchId(),
                    data.getSrcPortNo(),
                    data.getDstSwitchId(),
                    data.getDstPortNo(),
                    data.getLatency());
        if (updated) {
            log.info("Set one way latency for ISL {}_{} ===( {} ms )===> {}_{}. Packet id:{}",
                    data.getSrcSwitchId(), data.getSrcPortNo(), data.getLatency(),
                    data.getDstSwitchId(), data.getDstPortNo(), data.getPacketId());
        } else {
            log.warn("One way latency {} for ISL {}_{} ===> {}_{} was NOT set. Packet id:{}.",
                    data.getLatency(), data.getSrcSwitchId(), data.getSrcPortNo(),
                    data.getDstSwitchId(), data.getDstPortNo(), data.getPacketId());
        }
    }


    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(AbstractTopology.fieldMessage);
    }
}
