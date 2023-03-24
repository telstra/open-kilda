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

import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.command.haflow.HaFlowResponse;
import org.openkilda.messaging.command.haflow.HaSubFlowDto;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.repositories.HaSubFlowRepository;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.mapper.HaFlowMapper;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import com.fasterxml.uuid.impl.UUIDUtil;
import com.google.common.io.BaseEncoding;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.HashSet;
import java.util.Set;

/**
 * This implementation of the class is temporary.
 * It only works with DB. Switch rules wouldn't be modified.
 * Class is just a stub to give an API for users. It will be modified later.
 */
public class HaFlowCreateHubBolt extends AbstractBolt {
    private static final String HA_FLOW_PREFIX = "haf-";
    private static final char SUB_FLOW_INITIAL_POSTFIX = 'a';
    public static final String COMMON_ERROR_MESSAGE = "Couldn't create HA-flow";
    private transient HaFlowRepository haFlowRepository;
    private transient HaSubFlowRepository haSubFlowRepository;
    private transient NoArgGenerator flowIdGenerator;

    public HaFlowCreateHubBolt(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void init() {
        haFlowRepository = persistenceManager.getRepositoryFactory().createHaFlowRepository();
        haSubFlowRepository = persistenceManager.getRepositoryFactory().createHaSubFlowRepository();
        flowIdGenerator = Generators.timeBasedGenerator();
    }

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        String key = pullValue(input, MessageKafkaTranslator.FIELD_ID_KEY, String.class);
        HaFlowRequest payload = pullValue(input, FIELD_ID_PAYLOAD, HaFlowRequest.class);
        try {
            handleHaFlowCreate(key, payload);
        } catch (FlowProcessingException e) {
            emitErrorMessage(e.getErrorType(), COMMON_ERROR_MESSAGE, e.getMessage());
        } catch (Exception e) {
            emitErrorMessage(ErrorType.INTERNAL_ERROR, COMMON_ERROR_MESSAGE, e.getMessage());
        }
    }

    private void handleHaFlowCreate(String key, HaFlowRequest payload) {
        if (payload.getHaFlowId() == null) {
            payload.setHaFlowId(generateFlowId());
        }
        HaFlow haFlow = HaFlowMapper.INSTANCE.toHaFlow(payload);

        Set<HaSubFlow> subflows = new HashSet<>();
        char postfix = SUB_FLOW_INITIAL_POSTFIX;
        for (HaSubFlowDto subFlow : payload.getSubFlows()) {
            String subFlowId = String.format("%s-%c", payload.getHaFlowId(), postfix++);
            subflows.add(HaFlowMapper.INSTANCE.toSubFlow(subFlowId, subFlow));
        }
        persistenceManager.getTransactionManager().doInTransaction(() -> {
            if (haFlowRepository.exists(payload.getHaFlowId())) {
                throw new FlowProcessingException(ErrorType.ALREADY_EXISTS,
                        String.format("Couldn't create ha-flow %s. This ha-flow already exist.",
                                payload.getHaFlowId()));
            }
            for (HaSubFlow subflow : subflows) {
                subflow.setStatus(FlowStatus.UP);
                haSubFlowRepository.add(subflow);
            }
            haFlowRepository.add(haFlow);
            haFlow.setSubFlows(new HashSet<>(subflows));
        });
        HaFlowResponse response = new HaFlowResponse(HaFlowMapper.INSTANCE.toHaFlowDto(haFlow));
        InfoMessage message = new InfoMessage(
                response, System.currentTimeMillis(), getCommandContext().getCorrelationId());
        emitWithContext(Stream.HUB_TO_NB_RESPONSE_SENDER.name(), getCurrentTuple(), new Values(key, message));
    }

    private void emitErrorMessage(ErrorType type, String message, String description) {
        String requestId = getCommandContext().getCorrelationId();
        ErrorData errorData = new ErrorData(type, message, description);
        emitWithContext(Stream.HUB_TO_NB_RESPONSE_SENDER.name(), getCurrentTuple(), new Values(requestId,
                new ErrorMessage(errorData, System.currentTimeMillis(), requestId)));
    }

    protected String generateFlowId() {
        byte[] uuidAsBytes = UUIDUtil.asByteArray(flowIdGenerator.generate());
        String uuidAsBase32 = BaseEncoding.base32().omitPadding().lowerCase().encode(uuidAsBytes);
        return HA_FLOW_PREFIX + uuidAsBase32;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declareStream(HUB_TO_NB_RESPONSE_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
    }
}
