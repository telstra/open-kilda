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

package org.openkilda.wfm.topology.flowhs.bolts;

import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_KEY;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.yflow.SubFlowsReadRequest;
import org.openkilda.messaging.command.yflow.SubFlowsResponse;
import org.openkilda.messaging.command.yflow.YFlowPathsReadRequest;
import org.openkilda.messaging.command.yflow.YFlowPathsResponse;
import org.openkilda.messaging.command.yflow.YFlowReadRequest;
import org.openkilda.messaging.command.yflow.YFlowResponse;
import org.openkilda.messaging.command.yflow.YFlowsDumpRequest;
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
import org.openkilda.wfm.topology.flowhs.service.YFlowReadService;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;

public class YFlowReadBolt extends AbstractBolt {
    public static final Fields OUTPUT_STREAM_FIELDS = new Fields(FIELD_ID_KEY, FIELD_ID_PAYLOAD);

    private final YFlowReadConfig config;

    private transient YFlowReadService yFlowReadService;

    public YFlowReadBolt(YFlowReadConfig config, PersistenceManager persistenceManager) {
        super(persistenceManager, config.getLifeCycleEventComponent());

        this.config = config;
    }

    @Override
    public void init() {
        yFlowReadService = new YFlowReadService(persistenceManager,
                config.getReadOperationRetriesLimit(), config.getReadOperationRetryDelay());
    }

    protected void handleInput(Tuple input) throws Exception {
        String requestId = getCommandContext().getCorrelationId();
        CommandData request = pullValue(input, FIELD_ID_PAYLOAD, CommandData.class);

        try {
            if (request instanceof YFlowsDumpRequest) {
                List<YFlowResponse> result = processYFlowDumpRequest();
                emitMessages(input, requestId, result);
            } else if (request instanceof YFlowReadRequest) {
                YFlowResponse result = processYFlowReadRequest((YFlowReadRequest) request);
                emitMessage(input, requestId, result);
            } else if (request instanceof YFlowPathsReadRequest) {
                YFlowPathsResponse result = processYFlowPathsReadRequest((YFlowPathsReadRequest) request);
                emitMessage(input, requestId, result);
            } else if (request instanceof SubFlowsReadRequest) {
                SubFlowsResponse result = processSubFlowsReadRequest((SubFlowsReadRequest) request);
                emitMessage(input, requestId, result);
            } else {
                unhandledInput(input);
            }
        } catch (MessageException e) {
            ErrorData errorData = new ErrorData(e.getErrorType(), e.getMessage(), e.getErrorDescription());
            Message message = new ErrorMessage(errorData, System.currentTimeMillis(), requestId);
            emit(input, new Values(requestId, message));
        }
    }

    private List<YFlowResponse> processYFlowDumpRequest() {
        try {
            return yFlowReadService.getAllYFlows();
        } catch (Exception e) {
            log.warn("Can not dump y-flows", e);
            throw new MessageException(ErrorType.INTERNAL_ERROR, e.getMessage(), "Can not dump y-flows");
        }
    }

    private YFlowResponse processYFlowReadRequest(YFlowReadRequest request) {
        try {
            return yFlowReadService.getYFlow(request.getYFlowId());
        } catch (FlowNotFoundException e) {
            throw new MessageException(ErrorType.NOT_FOUND, e.getMessage(), "Y-flow not found");
        } catch (Exception e) {
            log.warn("Can not get y-flow", e);
            throw new MessageException(ErrorType.INTERNAL_ERROR, e.getMessage(), "Can not get y-flow");
        }
    }

    private YFlowPathsResponse processYFlowPathsReadRequest(YFlowPathsReadRequest request) {
        try {
            return yFlowReadService.getYFlowPaths(request.getYFlowId());
        } catch (FlowNotFoundException e) {
            throw new MessageException(ErrorType.NOT_FOUND, e.getMessage(), "Y-flow not found");
        } catch (Exception e) {
            log.warn("Can not get y-flow paths", e);
            throw new MessageException(ErrorType.INTERNAL_ERROR, e.getMessage(), "Can not get y-flow paths");
        }
    }

    private SubFlowsResponse processSubFlowsReadRequest(SubFlowsReadRequest request) {
        try {
            return yFlowReadService.getYFlowSubFlows(request.getYFlowId());
        } catch (FlowNotFoundException e) {
            throw new MessageException(ErrorType.NOT_FOUND, e.getMessage(), "Y-flow not found");
        } catch (Exception e) {
            log.warn("Can not get y-flow sub-flows", e);
            throw new MessageException(ErrorType.INTERNAL_ERROR, e.getMessage(), "Can not get y-flow sub-flows");
        }
    }

    private void emitMessages(Tuple input, String requestId, List<? extends InfoData> messageData) {
        if (messageData.isEmpty()) {
            Message message = new ChunkedInfoMessage(null, System.currentTimeMillis(), requestId, requestId, 0);
            emit(input, new Values(requestId, message));
        } else {
            int idx = 0;
            for (InfoData data : messageData) {
                Message message = new ChunkedInfoMessage(data, System.currentTimeMillis(), requestId,
                        idx++, messageData.size());
                emit(input, new Values(requestId, message));
            }
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

    @Getter
    @AllArgsConstructor
    @Builder
    public static class YFlowReadConfig implements Serializable {
        private final int readOperationRetriesLimit;
        private final Duration readOperationRetryDelay;
        private final String lifeCycleEventComponent;
    }
}
