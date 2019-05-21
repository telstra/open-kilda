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

package org.openkilda.wfm.topology.flow.bolts;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.BatchCommandsRequest;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandGroup;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.SwapFlowEndpointRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.SwapFlowResponse;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowPair;
import org.openkilda.model.UnidirectionalFlow;
import org.openkilda.pce.AvailableNetworkFactory;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.pce.PathComputerFactory;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.error.FeatureTogglesNotEnabledException;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.mappers.FlowMapper;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.flow.StreamType;
import org.openkilda.wfm.topology.flow.service.FeatureToggle;
import org.openkilda.wfm.topology.flow.service.FeatureTogglesService;
import org.openkilda.wfm.topology.flow.service.FlowCommandFactory;
import org.openkilda.wfm.topology.flow.service.FlowCommandSender;
import org.openkilda.wfm.topology.flow.service.FlowService;
import org.openkilda.wfm.topology.flow.validation.FlowValidator;

import lombok.NonNull;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class FlowOperationsBolt extends BaseRichBolt {


    private transient FlowService flowService;

    private transient FeatureTogglesService featureTogglesService;

    private transient OutputCollector outputCollector;

    private final PersistenceManager persistenceManager;

    private final PathComputerConfig pathComputerConfig;

    private final FlowResourcesConfig flowResourcesConfig;



    private transient RepositoryFactory repositoryFactory;
    private transient PathComputerFactory pathComputerFactory;

    private transient FlowResourcesManager flowResourcesManager;

    private transient FlowValidator flowValidator;
    private transient FlowCommandFactory commandFactory;

    private static final Logger logger = LoggerFactory.getLogger(FlowOperationsBolt.class);

    public FlowOperationsBolt(PersistenceManager persistenceManager, PathComputerConfig pathComputerConfig,
                              FlowResourcesConfig flowResourcesConfig) {
        this.persistenceManager = persistenceManager;
        this.pathComputerConfig = pathComputerConfig;
        this.flowResourcesConfig = flowResourcesConfig;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.outputCollector = outputCollector;
        flowValidator = new FlowValidator(repositoryFactory);
        commandFactory = new FlowCommandFactory();
        repositoryFactory = persistenceManager.getRepositoryFactory();
        flowService = new FlowService(persistenceManager, pathComputerFactory, flowResourcesManager,
                flowValidator, commandFactory);
        featureTogglesService = new FeatureTogglesService(persistenceManager.getRepositoryFactory());

        AvailableNetworkFactory availableNetworkFactory =
                new AvailableNetworkFactory(pathComputerConfig, repositoryFactory);
        pathComputerFactory = new PathComputerFactory(pathComputerConfig, availableNetworkFactory);
    }

    @Override
    public void execute(Tuple tuple) {
        StreamType streamId = StreamType.valueOf(tuple.getSourceStreamId());
        String flowId = tuple.getStringByField(Utils.FLOW_ID);
        String correlationId = Utils.DEFAULT_CORRELATION_ID;
        Message msg = (Message) tuple.getValueByField(AbstractTopology.MESSAGE_FIELD);
        correlationId = msg.getCorrelationId();

        CommandMessage cmsg = (msg instanceof CommandMessage) ? (CommandMessage) msg : null;
        InfoMessage imsg = (msg instanceof InfoMessage) ? (InfoMessage) msg : null;
        logger.info("Flow request: {}={}, {}={}, stream={}",
                Utils.CORRELATION_ID, correlationId, Utils.FLOW_ID, flowId, streamId);
        switch (streamId) {
            case SWAP_ENDPOINT:
                handleSwapFlowEndpointRequest(cmsg, tuple);
                break;
            default:
                logger.error("Unexpected stream: {} in {}", streamId, tuple);
                break;
        }
    }

    private void handleSwapFlowEndpointRequest(CommandMessage message, Tuple tuple) {
        final String errorType = "Can not swap endpoints for flows";

        try {
            featureTogglesService.checkFeatureToggleEnabled(FeatureToggle.UPDATE_FLOW);

            SwapFlowEndpointRequest request = (SwapFlowEndpointRequest) message.getData();

            List<FlowPair> flowPairs =
                    flowService.swapFlowEnpoints(request.getFirstFlow(), request.getSecondFlow(),
                            new FlowCommandSenderImpl(message.getCorrelationId(), tuple, StreamType.UPDATE));

            logger.info("Swap endpoint");
            Values values = new Values(new InfoMessage(buildSwapFlowResponse(flowPairs), message.getTimestamp(),
                    message.getCorrelationId(), Destination.NORTHBOUND, null));
            outputCollector.emit(StreamType.RESPONSE.toString(), tuple, values);
        } catch (FeatureTogglesNotEnabledException e) {
            // TODO (vborisovskii): Change INTERNAL_ERROR to ErrorType.NOT_PERMITTED after merge #2250
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.NOT_PERMITTED, errorType, "Feature toggles not enabled for UPDATE_FLOW operation.");
        } catch (FlowNotFoundException e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.NOT_FOUND, errorType, e.getMessage());
        } catch (Exception e) {
            logger.error("Unhandled exception on SWAP operation");
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.UPDATE_FAILURE, "Could not swap endpoints", e.getMessage());
        }
    }

    private SwapFlowResponse buildSwapFlowResponse(List<FlowPair> flows) {
        return new SwapFlowResponse(buildFlowResponse(flows.get(0).getForward()),
                buildFlowResponse(flows.get(1).getForward()));
    }

    private FlowResponse buildFlowResponse(UnidirectionalFlow flow) {
        FlowDto flowDto = FlowMapper.INSTANCE.map(flow);
        flowDto.setCookie(flow.getCookie() & Cookie.FLOW_COOKIE_VALUE_MASK);
        return new FlowResponse(flowDto);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(StreamType.RESPONSE.toString(), AbstractTopology.fieldMessage);
    }

    class FlowCommandSenderImpl implements FlowCommandSender {
        private final String correlationId;
        private final Tuple tuple;
        private final StreamType stream;

        FlowCommandSenderImpl(String correlationId, Tuple tuple, StreamType stream) {
            this.correlationId = correlationId;
            this.tuple = tuple;
            this.stream = stream;
        }

        @Override
        public void sendFlowCommands(@NonNull String flowId, @NonNull List<CommandGroup> commandGroups,
                                     @NonNull List<? extends CommandData> onSuccessCommands,
                                     @NonNull List<? extends CommandData> onFailureCommands) {
            CommandMessage message = new CommandMessage(
                    new BatchCommandsRequest(commandGroups, onSuccessCommands, onFailureCommands),
                    System.currentTimeMillis(), correlationId);
            outputCollector.emit(stream.toString(), tuple, new Values(message, flowId));
        }
    }
}
