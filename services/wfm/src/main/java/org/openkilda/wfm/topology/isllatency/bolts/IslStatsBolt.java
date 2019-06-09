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
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslOneWayLatency;
import org.openkilda.messaging.info.event.IslRoundTripLatency;
import org.openkilda.model.Isl;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.IllegalIslStateException;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.error.JsonEncodeException;
import org.openkilda.wfm.share.utils.MetricFormatter;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.isllatency.LatencyAction;
import org.openkilda.wfm.topology.isllatency.service.DecisionMakerService;
import org.openkilda.wfm.topology.isllatency.service.IslLatencyService;
import org.openkilda.wfm.topology.utils.KafkaRecordTranslator;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class IslStatsBolt extends AbstractBolt {
    private final PersistenceManager persistenceManager;
    private transient IslLatencyService islLatencyService;
    private transient DecisionMakerService decisionMakerService;
    private MetricFormatter metricFormatter;

    public IslStatsBolt(String metricPrefix, PersistenceManager persistenceManager) {
        this.metricFormatter = new MetricFormatter(metricPrefix);
        this.persistenceManager = persistenceManager;
    }

    @Override
    protected void init() {
        TransactionManager transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        islLatencyService = new IslLatencyService(transactionManager, repositoryFactory);
        decisionMakerService = new DecisionMakerService(repositoryFactory);
    }

    private static List<Object> tsdbTuple(String metric, long timestamp, Number value, Map<String, String> tag)
            throws JsonEncodeException {
        Datapoint datapoint = new Datapoint(metric, timestamp, tag, value);
        try {
            return Collections.singletonList(Utils.MAPPER.writeValueAsString(datapoint));
        } catch (JsonProcessingException e) {
            throw new JsonEncodeException(datapoint, e);
        }
    }

    List<Object> buildTsdbTuple(Isl isl, long latency, long timestamp) throws JsonEncodeException {
        return buildTsdbTuple(isl.getSrcSwitch().getSwitchId(), isl.getSrcPort(),
                isl.getDestSwitch().getSwitchId(), isl.getDestPort(), latency, timestamp);
    }

    List<Object> buildTsdbTuple(IslOneWayLatency data, long latency, long timestamp) throws JsonEncodeException {
        return buildTsdbTuple(data.getSrcSwitchId(), data.getSrcPortNo(), data.getDstSwitchId(), data.getDstPortNo(),
                latency, timestamp);
    }

    private List<Object> buildTsdbTuple(SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort,
                                        long latency, long timestamp) throws JsonEncodeException {
        Map<String, String> tags = new HashMap<>();
        tags.put("src_switch", srcSwitchId.toOtsdFormat());
        tags.put("src_port", String.valueOf(srcPort));
        tags.put("dst_switch", dstSwitchId.toOtsdFormat());
        tags.put("dst_port", String.valueOf(dstPort));

        return tsdbTuple(metricFormatter.format("isl.latency"), timestamp, latency, tags);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        Message message = pullValue(input, KafkaRecordTranslator.FIELD_ID_PAYLOAD, Message.class);

        if (message instanceof InfoMessage) {
            InfoData data = ((InfoMessage) message).getData();
            if (data instanceof IslRoundTripLatency) {
                handleRoundTripLatencyMetric(input, message, (IslRoundTripLatency) data);
            } else if (data instanceof IslOneWayLatency) {
                handleOneWayLatencyMetric(input, message, (IslOneWayLatency) data);
            } else {
                unhandledInput(input);
            }
        } else {
            unhandledInput(input);
        }
    }

    private void handleRoundTripLatencyMetric(Tuple input, Message message, IslRoundTripLatency data)
            throws JsonEncodeException {
        Isl isl;
        try {
            isl = islLatencyService.getIsl(data.getSrcSwitchId(), data.getSrcPortNo());
        } catch (IslNotFoundException e) {
            log.warn("Couldn't send latency metric for ISL with source {}_{}. Packet id:{}. There is no such ISL",
                    data.getSrcSwitchId(), data.getSrcPortNo(), data.getPacketId());
            return;
        } catch (IllegalIslStateException e) {
            log.warn("Couldn't send latency metric for ISL with source {}_{}. Packet id:{}. ISL is illegal state",
                    data.getSrcSwitchId(), data.getSrcPortNo(), data.getPacketId());
            return;
        }

        List<Object> results = buildTsdbTuple(isl, data.getLatency(), message.getTimestamp());
        emit(input, results);
    }

    private void handleOneWayLatencyMetric(Tuple input, Message message, IslOneWayLatency data)
            throws JsonEncodeException {
        LatencyAction decision = decisionMakerService.handleOneWayIslLatency(data);

        switch (decision) {
            case USE_ONE_WAY_LATENCY:
                emitLatency(data.getLatency(), input, message, data);
                break;
            case COPY_REVERSE_ROUND_TRIP_LATENCY:
                getAndEmitReverseLatency(input, message, data);
                break;
            case DO_NOTHING:
                break;
            default:
                break;
        }
    }

    private void getAndEmitReverseLatency(Tuple input, Message message, IslOneWayLatency data)
            throws JsonEncodeException {
        try {
            Isl reverseIsl = islLatencyService.getIsl(data.getDstSwitchId(), data.getDstPortNo(),
                    data.getSrcSwitchId(), data.getSrcPortNo());
            emitLatency(reverseIsl.getLatency(), input, message, data);
        } catch (IslNotFoundException e) {
            log.warn("Couldn't send latency metric for ISL {}_{} ===> {}_{}. Packet id:{}. "
                            + "There is no such ISL",
                    data.getSrcSwitchId(), data.getSrcPortNo(),
                    data.getDstSwitchId(), data.getDstPortNo(), data.getPacketId());
        }
    }

    private void emitLatency(long latency, Tuple input, Message message, IslOneWayLatency data)
            throws JsonEncodeException {
        List<Object> tsdbTuple = buildTsdbTuple(data, latency, message.getTimestamp());
        emit(input, tsdbTuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(AbstractTopology.fieldMessage);
    }
}
