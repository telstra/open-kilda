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

package org.openkilda.wfm.topology.nbworker.bolts;

import static java.lang.String.format;
import static org.openkilda.wfm.share.bolt.KafkaEncoder.FIELD_ID_KEY;
import static org.openkilda.wfm.share.bolt.KafkaEncoder.FIELD_ID_PAYLOAD;
import static org.openkilda.wfm.topology.nbworker.bolts.ResponseSplitterBolt.FIELD_ID_RESPONSE;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.messaging.command.flow.FlowRequest.Type;
import org.openkilda.messaging.command.flow.PeriodicPingCommand;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.model.FlowPatch;
import org.openkilda.messaging.nbtopology.request.FlowPatchRequest;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.topology.nbworker.StreamType;
import org.openkilda.wfm.topology.nbworker.services.FlowOperationsService;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Collections;
import java.util.Map;

//TODO(dpoltavets): It is necessary to transfer the logic of this bolt to another place.
public class FlowPatchBolt extends AbstractBolt implements FlowOperationsCarrier {
    private final PersistenceManager persistenceManager;
    private transient RepositoryFactory repositoryFactory;
    private transient TransactionManager transactionManager;
    private transient FlowOperationsService flowOperationsService;

    public FlowPatchBolt(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    protected String getCorrelationId() {
        return getCommandContext().getCorrelationId();
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        repositoryFactory = persistenceManager.getRepositoryFactory();
        transactionManager = persistenceManager.getTransactionManager();
        super.prepare(stormConf, context, collector);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init() {
        this.flowOperationsService = new FlowOperationsService(repositoryFactory, transactionManager);
    }

    protected void handleInput(Tuple input) throws Exception {
        CommandData request = pullValue(input, MessageKafkaTranslator.FIELD_ID_PAYLOAD, CommandData.class);
        try {
            if (request instanceof FlowPatchRequest) {
                processFlowPatchRequest((FlowPatchRequest) request);
            } else {
                unhandledInput(input);
            }

        } catch (MessageException e) {
            log.error(format("Failed to process request: %s", e.getMessage()), e);
            ErrorData errorData = new ErrorData(e.getErrorType(), e.getMessage(), e.getErrorDescription());
            getOutput().emit(StreamType.ERROR.toString(), input, new Values(errorData, getCommandContext()));
        }
    }

    private void processFlowPatchRequest(FlowPatchRequest request) {
        FlowPatch flowPatch = request.getFlow();

        try {
            flowOperationsService.updateFlow(this, flowPatch);
        } catch (FlowNotFoundException e) {
            throw new MessageException(ErrorType.NOT_FOUND, e.getMessage(), "Flow was not found.");
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(FIELD_ID_RESPONSE, FIELD_ID_CONTEXT));
        declarer.declareStream(StreamType.ERROR.toString(), new Fields(FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT));
        declarer.declareStream(StreamType.PING.toString(), new Fields(FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT));
        declarer.declareStream(StreamType.FLOWHS.toString(), new Fields(FIELD_ID_KEY, FIELD_ID_PAYLOAD));
    }

    @Override
    public void emitPeriodicPingUpdate(String flowId, boolean enabled) {
        CommandMessage command = new CommandMessage(new PeriodicPingCommand(flowId, enabled),
                System.currentTimeMillis(), getCorrelationId());
        getOutput().emit(StreamType.PING.toString(), getCurrentTuple(), new Values(command, getCommandContext()));
    }

    @Override
    public void sendUpdateRequest(FlowRequest request) {
        request.setType(Type.UPDATE);
        CommandMessage command = new CommandMessage(request, System.currentTimeMillis(), getCorrelationId());
        getOutput().emit(StreamType.FLOWHS.toString(), getCurrentTuple(),
                new Values(getCommandContext().getCorrelationId(), command));
    }

    @Override
    public void sendNorthboundResponse(InfoData data) {
        getOutput().emit(getCurrentTuple(), new Values(Collections.singletonList(data), getCommandContext()));
    }
}
