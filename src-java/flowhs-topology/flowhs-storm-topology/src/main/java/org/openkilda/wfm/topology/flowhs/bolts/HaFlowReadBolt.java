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

import static org.openkilda.wfm.topology.flowhs.bolts.YFlowReadBolt.OUTPUT_STREAM_FIELDS;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
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
import org.openkilda.model.HaFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.topology.flowhs.mapper.HaFlowMapper;

import lombok.NonNull;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * This implementation of the class is temporary.
 * It only works with DB. Switch rules wouldn't be modified.
 * Class is just a stub to give an API for users. It will be modified later.
 */
public class HaFlowReadBolt extends AbstractBolt {
    private transient HaFlowRepository haFlowRepository;

    public HaFlowReadBolt(@NonNull PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    public void init() {
        haFlowRepository = persistenceManager.getRepositoryFactory().createHaFlowRepository();
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
        return haFlowRepository.findAll().stream()
                .map(HaFlowMapper.INSTANCE::toHaFlowDto)
                .map(HaFlowResponse::new)
                .collect(Collectors.toList());
    }

    private HaFlowResponse processHaFlowReadRequest(HaFlowReadRequest request) {
        Optional<HaFlow> haFlow = haFlowRepository.findById(request.getHaFlowId());
        if (!haFlow.isPresent()) {
            throw new MessageException(ErrorType.NOT_FOUND, "Couldn't get HA-flow",
                    String.format("HA-flow %s not found.", request.getHaFlowId()));
        }
        return new HaFlowResponse(HaFlowMapper.INSTANCE.toHaFlowDto(haFlow.get()));
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
    }

    private void emitErrorMessage(ErrorType type, String message, String description) {
        String requestId = getCommandContext().getCorrelationId();
        ErrorData errorData = new ErrorData(type, message, description);
        emit(getCurrentTuple(), new Values(requestId,
                new ErrorMessage(errorData, System.currentTimeMillis(), requestId)));
    }
}
