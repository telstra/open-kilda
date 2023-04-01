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

import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_NB_RESPONSE_SENDER;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.haflow.HaFlowDeleteRequest;
import org.openkilda.messaging.command.haflow.HaFlowResponse;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.model.HaFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.mapper.HaFlowMapper;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Optional;

/**
 * This implementation of the class is temporary.
 * It only works with DB. Switch rules wouldn't be modified.
 * Class is just a stub to give an API for users. It will be modified later.
 */
public class HaFlowDeleteHubBolt extends AbstractBolt {
    public static final String COMMON_ERROR_MESSAGE = "Couldn't delete HA-flow";
    private transient HaFlowRepository haFlowRepository;
    private transient FlowRepository flowRepository;

    public HaFlowDeleteHubBolt(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void init() {
        haFlowRepository = persistenceManager.getRepositoryFactory().createHaFlowRepository();
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
    }

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        String key = pullValue(input, MessageKafkaTranslator.FIELD_ID_KEY, String.class);
        HaFlowDeleteRequest payload = pullValue(input, FIELD_ID_PAYLOAD, HaFlowDeleteRequest.class);

        try {
            handleHaFlowDelete(key, payload);
        } catch (FlowProcessingException e) {
            emitErrorMessage(e.getErrorType(), COMMON_ERROR_MESSAGE, e.getMessage());
        } catch (Exception e) {
            emitErrorMessage(ErrorType.INTERNAL_ERROR, COMMON_ERROR_MESSAGE, e.getMessage());
        }
    }

    private void handleHaFlowDelete(String key, HaFlowDeleteRequest payload) {
        CommandContext context = getCommandContext();
        Optional<HaFlow> haFlow = haFlowRepository.remove(payload.getHaFlowId());
        if (haFlow.isPresent()) {
            InfoData response = new HaFlowResponse(HaFlowMapper.INSTANCE.toHaFlowDto(
                    haFlow.get(), flowRepository, haFlowRepository));
            Message message = new InfoMessage(response, System.currentTimeMillis(), context.getCorrelationId());
            emitWithContext(Stream.HUB_TO_NB_RESPONSE_SENDER.name(), getCurrentTuple(), new Values(key, message));
        } else {
            throw new FlowProcessingException(ErrorType.DATA_INVALID,
                    String.format("Couldn't delete non existent HA-flow %s", payload.getHaFlowId()));
        }
    }

    private void emitErrorMessage(ErrorType type, String message, String description) {
        String requestId = getCommandContext().getCorrelationId();
        ErrorData errorData = new ErrorData(type, message, description);
        emitWithContext(Stream.HUB_TO_NB_RESPONSE_SENDER.name(), getCurrentTuple(), new Values(requestId,
                new ErrorMessage(errorData, System.currentTimeMillis(), requestId)));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declareStream(HUB_TO_NB_RESPONSE_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
    }
}
