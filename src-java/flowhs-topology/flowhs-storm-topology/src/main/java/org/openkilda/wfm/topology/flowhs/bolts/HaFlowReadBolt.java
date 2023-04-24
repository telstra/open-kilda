/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.bolts;

import static org.openkilda.wfm.topology.flowhs.bolts.YFlowReadBolt.OUTPUT_STREAM_FIELDS;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.haflow.HaFlowPathsReadRequest;
import org.openkilda.messaging.command.haflow.HaFlowPathsResponse;
import org.openkilda.messaging.command.haflow.HaFlowReadRequest;
import org.openkilda.messaging.command.haflow.HaFlowResponse;
import org.openkilda.messaging.command.haflow.HaFlowsDumpRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.ChunkedInfoMessage;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.topology.flowhs.service.haflow.HaFlowReadService;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;

public class HaFlowReadBolt extends AbstractBolt {
    private final HaFlowReadConfig config;
    private transient HaFlowReadService service;

    public HaFlowReadBolt(@NonNull HaFlowReadConfig config, @NonNull PersistenceManager persistenceManager) {
        super(persistenceManager, config.getLifeCycleEventComponent());
        this.config = config;
    }

    @Override
    public void init() {
        service = new HaFlowReadService(persistenceManager, config.getReadOperationRetriesLimit(),
                config.getReadOperationRetryDelay());
    }

    protected void handleInput(Tuple input) throws Exception {
        String requestId = getCommandContext().getCorrelationId();
        CommandData request = pullValue(input, FIELD_ID_PAYLOAD, CommandData.class);

        try {
            if (request instanceof HaFlowsDumpRequest) {
                List<HaFlowResponse> result = processHaFlowDumpRequest();
                emitMessages(input, requestId, result);
            } else if (request instanceof HaFlowReadRequest) {
                HaFlowResponse result = processHaFlowReadRequest((HaFlowReadRequest) request);
                emitMessage(input, requestId, result);
            } else if (request instanceof HaFlowPathsReadRequest) {
                HaFlowPathsResponse result = processHaFlowPathsReadRequest((HaFlowPathsReadRequest) request);
                emitMessage(input, requestId, result);
            } else {
                unhandledInput(input);
            }
        } catch (MessageException e) {
            emitErrorMessage(e.getErrorType(), e.getMessage(), e.getErrorDescription());
        } catch (Exception e) {
            emitErrorMessage(ErrorType.INTERNAL_ERROR, e.getMessage(), "Couldn't read HA-flow");
        }
    }

    private List<HaFlowResponse> processHaFlowDumpRequest() {
        try {
            return service.getAllHaFlows();
        } catch (Exception e) {
            log.warn("Couldn't dump HA-flows", e);
            throw new MessageException(ErrorType.INTERNAL_ERROR, e.getMessage(), "Couldn't dump HA-flows");
        }
    }

    private HaFlowResponse processHaFlowReadRequest(HaFlowReadRequest request) {
        try {
            return service.getHaFlow(request.getHaFlowId());
        } catch (FlowNotFoundException e) {
            throw new MessageException(ErrorType.NOT_FOUND, "Couldn't get HA-flow",
                    String.format("HA-flow %s not found.", request.getHaFlowId()));
        } catch (Exception e) {
            log.warn("Couldn't get HA-flow", e);
            throw new MessageException(ErrorType.INTERNAL_ERROR, e.getMessage(), "Couldn't get HA-flow");
        }
    }

    private HaFlowPathsResponse processHaFlowPathsReadRequest(HaFlowPathsReadRequest request) {
        try {
            return service.getHaFlowPaths(request.getHaFlowId());
        } catch (FlowNotFoundException e) {
            throw new MessageException(ErrorType.NOT_FOUND, "Couldn't find HA-flow",
                    String.format("HA-flow %s not found.", request.getHaFlowId()));
        } catch (Exception e) {
            log.warn("Couldn't get HA-flow paths", e);
            throw new MessageException(ErrorType.INTERNAL_ERROR, e.getMessage(), "Couldn't get HA-flow paths");
        }
    }

    private void emitMessages(Tuple input, String requestId, List<? extends InfoData> messageData) {
        for (ChunkedInfoMessage message : ChunkedInfoMessage.createChunkedList(messageData, requestId)) {
            emit(input, new Values(requestId, message));
        }
    }

    private void emitMessage(Tuple input, String requestId, InfoData messageData) {
        Message message = new InfoMessage(messageData, System.currentTimeMillis(), requestId);
        emit(input, new Values(requestId, message));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);

        declarer.declare(OUTPUT_STREAM_FIELDS);
        declarer.declareStream(ZkStreams.ZK.toString(),
                new Fields(ZooKeeperBolt.FIELD_ID_STATE, ZooKeeperBolt.FIELD_ID_CONTEXT));
    }

    private void emitErrorMessage(ErrorType type, String message, String description) {
        String requestId = getCommandContext().getCorrelationId();
        ErrorData errorData = new ErrorData(type, message, description);
        emit(getCurrentTuple(), new Values(requestId,
                new ErrorMessage(errorData, System.currentTimeMillis(), requestId)));
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class HaFlowReadConfig implements Serializable {
        private final int readOperationRetriesLimit;
        private final Duration readOperationRetryDelay;
        private final String lifeCycleEventComponent;
    }
}
