/* Copyright 2018 Telstra Open Source
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

import static java.lang.String.format;
import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.BatchFlowCommandsRequest;
import org.openkilda.messaging.command.flow.FlowCommandGroup;
import org.openkilda.messaging.command.flow.FlowCommandGroup.FailureReaction;
import org.openkilda.messaging.command.flow.FlowCreateRequest;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.openkilda.messaging.command.flow.InstallTransitFlow;
import org.openkilda.messaging.command.flow.MeterModifyCommandRequest;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.state.CrudBoltState;
import org.openkilda.messaging.ctrl.state.ResorceCacheBoltState;
import org.openkilda.messaging.error.CacheException;
import org.openkilda.messaging.error.ClientErrorMessage;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.ChunkedInfoMessage;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.flow.FlowCacheSyncResponse;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.info.flow.FlowOperation;
import org.openkilda.messaging.info.flow.FlowReadResponse;
import org.openkilda.messaging.info.flow.FlowRerouteResponse;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.FlowStatusResponse;
import org.openkilda.messaging.model.BidirectionalFlowDto;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPair;
import org.openkilda.model.FlowSegment;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.AvailableNetworkFactory;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.pce.PathComputerFactory;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.ctrl.CtrlAction;
import org.openkilda.wfm.ctrl.ICtrlBolt;
import org.openkilda.wfm.error.ClientException;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.share.cache.ResourceCache;
import org.openkilda.wfm.share.mappers.FlowMapper;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.flow.ComponentType;
import org.openkilda.wfm.topology.flow.FlowTopology;
import org.openkilda.wfm.topology.flow.StreamType;
import org.openkilda.wfm.topology.flow.model.FlowPairWithSegments;
import org.openkilda.wfm.topology.flow.model.UpdatedFlowPairWithSegments;
import org.openkilda.wfm.topology.flow.service.FeatureToggle;
import org.openkilda.wfm.topology.flow.service.FeatureTogglesService;
import org.openkilda.wfm.topology.flow.service.FlowAlreadyExistException;
import org.openkilda.wfm.topology.flow.service.FlowCommandFactory;
import org.openkilda.wfm.topology.flow.service.FlowCommandSender;
import org.openkilda.wfm.topology.flow.service.FlowResourcesManager;
import org.openkilda.wfm.topology.flow.service.FlowService;
import org.openkilda.wfm.topology.flow.service.FlowService.ReroutedFlow;
import org.openkilda.wfm.topology.flow.validation.FlowValidationException;
import org.openkilda.wfm.topology.flow.validation.FlowValidator;
import org.openkilda.wfm.topology.flow.validation.SwitchValidationException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class CrudBolt
        extends BaseStatefulBolt<InMemoryKeyValueState<String, ResourceCache>>
        implements ICtrlBolt {

    private static final String STREAM_ID_CTRL = "ctrl";

    private static final Logger logger = LoggerFactory.getLogger(CrudBolt.class);

    /**
     * Flow cache key.
     */
    private static final String FLOW_CACHE = "flow";

    private final PersistenceManager persistenceManager;

    private final PathComputerConfig pathComputerConfig;

    private transient RepositoryFactory repositoryFactory;

    private transient FlowService flowService;

    private transient FeatureTogglesService featureTogglesService;

    private transient FlowCommandFactory commandFactory;

    private transient PathComputerFactory pathComputerFactory;

    private transient FlowResourcesManager flowResourcesManager;

    private transient FlowValidator flowValidator;

    private transient TopologyContext context;
    private transient OutputCollector outputCollector;

    public CrudBolt(PersistenceManager persistenceManager, PathComputerConfig pathComputerConfig) {
        this.persistenceManager = persistenceManager;
        this.pathComputerConfig = pathComputerConfig;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initState(InMemoryKeyValueState<String, ResourceCache> state) {
        ResourceCache resourceCache = state.get(FLOW_CACHE);
        if (resourceCache == null) {
            resourceCache = new ResourceCache();
            state.put(FLOW_CACHE, resourceCache);
        }

        flowResourcesManager = new FlowResourcesManager(resourceCache);
        flowService = new FlowService(persistenceManager, pathComputerFactory, flowResourcesManager, flowValidator);
        featureTogglesService = new FeatureTogglesService(persistenceManager.getRepositoryFactory());

        initFlowResourcesManager();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(StreamType.CREATE.toString(), FlowTopology.fieldsMessageFlowId);
        outputFieldsDeclarer.declareStream(StreamType.UPDATE.toString(), FlowTopology.fieldsMessageFlowId);
        outputFieldsDeclarer.declareStream(StreamType.DELETE.toString(), FlowTopology.fieldsMessageFlowId);
        outputFieldsDeclarer.declareStream(StreamType.METER_MODE.toString(), AbstractTopology.fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.RESPONSE.toString(), AbstractTopology.fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.ERROR.toString(), FlowTopology.fieldsMessageErrorType);
        // FIXME(dbogun): use proper tuple format
        outputFieldsDeclarer.declareStream(STREAM_ID_CTRL, AbstractTopology.fieldMessage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.context = topologyContext;
        this.outputCollector = outputCollector;

        repositoryFactory = persistenceManager.getRepositoryFactory();
        flowValidator = new FlowValidator(repositoryFactory);
        AvailableNetworkFactory availableNetworkFactory =
                new AvailableNetworkFactory(pathComputerConfig, repositoryFactory);
        pathComputerFactory = new PathComputerFactory(pathComputerConfig, availableNetworkFactory);
        commandFactory = new FlowCommandFactory();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Tuple tuple) {
        if (CtrlAction.boltHandlerEntrance(this, tuple)) {
            return;
        }

        logger.debug("Request tuple={}", tuple);

        ComponentType componentId = ComponentType.valueOf(tuple.getSourceComponent());
        // FIXME(surabujin): do not use any "default" correlation id
        String correlationId = Utils.DEFAULT_CORRELATION_ID;
        StreamType streamId = StreamType.valueOf(tuple.getSourceStreamId());
        String flowId = tuple.getStringByField(Utils.FLOW_ID);

        boolean isRecoverable = false;
        try {
            switch (componentId) {
                case SPLITTER_BOLT:
                    Message msg = (Message) tuple.getValueByField(AbstractTopology.MESSAGE_FIELD);
                    correlationId = msg.getCorrelationId();

                    CommandMessage cmsg = (msg instanceof CommandMessage) ? (CommandMessage) msg : null;
                    InfoMessage imsg = (msg instanceof InfoMessage) ? (InfoMessage) msg : null;

                    logger.info("Flow request: {}={}, {}={}, component={}, stream={}",
                            Utils.CORRELATION_ID, correlationId, Utils.FLOW_ID, flowId, componentId, streamId);

                    switch (streamId) {
                        case CREATE:
                            handleCreateRequest(cmsg, tuple);
                            break;
                        case UPDATE:
                            handleUpdateRequest(cmsg, tuple);
                            break;
                        case DELETE:
                            handleDeleteRequest(flowId, cmsg, tuple);
                            break;
                        case PUSH:
                            handlePushRequest(flowId, imsg, tuple);
                            break;
                        case UNPUSH:
                            handleUnpushRequest(flowId, imsg, tuple);
                            break;
                        case REROUTE:
                            handleRerouteRequest(cmsg, tuple);
                            break;
                        case CACHE_SYNC:
                            handleCacheSyncRequest(cmsg, tuple);
                            break;
                        case READ:
                            handleReadRequest(flowId, cmsg, tuple);
                            break;
                        case DUMP:
                            handleDumpRequest(cmsg, tuple);
                            break;
                        case METER_MODE:
                            handleMeterModeRequest(cmsg, tuple, flowId);
                            break;
                        default:
                            logger.error("Unexpected stream: {} in {}", streamId, tuple);
                            break;
                    }
                    break;

                default:
                    logger.error("Unexpected component: {} in {}", componentId, tuple);
                    break;
            }
            //} catch (RecoverableException e) {
            // FIXME(surabujin): implement retry limit
            // logger.error(
            // "Recoverable error (do not try to recoverable it until retry limit will be implemented): {}", e);
            // isRecoverable = true;
        } catch (ClientException exception) {
            emitError(tuple, correlationId, exception, true);
        } catch (CacheException exception) {
            emitError(tuple, correlationId, exception, false);

        } catch (Exception e) {
            logger.error("Unhandled exception", e);
        } finally {
            logger.debug("Command message ack: {}", tuple);

            if (isRecoverable) {
                outputCollector.fail(tuple);
            } else {
                outputCollector.ack(tuple);
            }
        }
    }

    private void emitError(Tuple tuple, String correlationId, CacheException exception, boolean isWarning) {
        String logMessage = format("%s: %s", exception.getErrorMessage(), exception.getErrorDescription());
        ErrorData errorData = new ErrorData(exception.getErrorType(), logMessage, exception.getErrorDescription());
        ErrorMessage errorMessage;

        if (isWarning) {
            logger.warn(logMessage, exception);
            errorMessage = buildClientErrorMessage(correlationId, errorData);
        } else {
            logger.error(logMessage, exception);
            errorMessage = buildErrorMessage(correlationId, errorData);
        }

        Values error = new Values(errorMessage, exception.getErrorType());
        outputCollector.emit(StreamType.ERROR.toString(), tuple, error);
    }

    private void handleCacheSyncRequest(CommandMessage message, Tuple tuple) {
        logger.info("Synchronize FlowResourcesManager.");

        initFlowResourcesManager();

        Values values = new Values(new InfoMessage(new FlowCacheSyncResponse(),
                message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND, null));
        outputCollector.emit(StreamType.RESPONSE.toString(), tuple, values);
    }

    private void handlePushRequest(String flowId, InfoMessage message, Tuple tuple) {
        final String errorType = "Can not push flow";

        try {
            featureTogglesService.checkFeatureToggleEnabled(FeatureToggle.PUSH_FLOW);

            logger.info("PUSH flow: {} :: {}", flowId, message);
            FlowInfoData fid = (FlowInfoData) message.getData();
            FlowPair flow = FlowMapper.INSTANCE.map(fid.getPayload());

            FlowStatus flowStatus = (fid.getOperation() == FlowOperation.PUSH_PROPAGATE)
                    ? FlowStatus.IN_PROGRESS : FlowStatus.UP;
            flow.setStatus(flowStatus);

            flowService.saveFlow(flow,
                    new CrudFlowCommandSender(message.getCorrelationId(), tuple, StreamType.CREATE) {
                        @Override
                        public void sendInstallRulesCommand(FlowPairWithSegments flowWithSegments) {
                            if (fid.getOperation() == FlowOperation.PUSH_PROPAGATE) {
                                super.sendInstallRulesCommand(flowWithSegments);
                            }
                        }
                    });

            logger.info("PUSHed the flow: {}", flow);

            Values values = new Values(new InfoMessage(
                    new FlowStatusResponse(new FlowIdStatusPayload(flowId, FlowMapper.INSTANCE.map(flowStatus))),
                    message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND, null));
            outputCollector.emit(StreamType.RESPONSE.toString(), tuple, values);
        } catch (FlowAlreadyExistException e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.ALREADY_EXISTS, errorType, e.getMessage());
        } catch (Exception e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.CREATION_FAILURE, errorType, e.getMessage());
        }
    }

    private void handleUnpushRequest(String flowId, InfoMessage message, Tuple tuple) {
        final String errorType = "Can not unpush flow";

        try {
            featureTogglesService.checkFeatureToggleEnabled(FeatureToggle.UNPUSH_FLOW);

            logger.info("UNPUSH flow: {} :: {}", flowId, message);

            FlowInfoData fid = (FlowInfoData) message.getData();

            FlowPair deletedFlow = flowService.deleteFlow(flowId,
                    new CrudFlowCommandSender(message.getCorrelationId(), tuple, StreamType.DELETE) {
                        @Override
                        public void sendRemoveRulesCommand(FlowPairWithSegments flowWithSegments) {
                            if (fid.getOperation() == FlowOperation.UNPUSH_PROPAGATE) {
                                super.sendRemoveRulesCommand(flowWithSegments);
                            }
                        }
                    });

            logger.info("UNPUSHed the flow: {}", deletedFlow);

            Values values = new Values(new InfoMessage(
                    new FlowStatusResponse(new FlowIdStatusPayload(flowId, FlowState.DOWN)),
                    message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND, null));
            outputCollector.emit(StreamType.RESPONSE.toString(), tuple, values);
        } catch (FlowNotFoundException e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.NOT_FOUND, errorType, e.getMessage());
        } catch (Exception e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.DELETION_FAILURE, errorType, e.getMessage());
        }
    }

    private void handleDeleteRequest(String flowId, CommandMessage message, Tuple tuple) {
        final String errorType = "Can not delete flow";

        try {
            featureTogglesService.checkFeatureToggleEnabled(FeatureToggle.DELETE_FLOW);

            FlowPair deletedFlow = flowService.deleteFlow(flowId,
                    new CrudFlowCommandSender(message.getCorrelationId(), tuple, StreamType.DELETE));

            logger.info("Deleted the flow: {}", deletedFlow);

            Values values = new Values(new InfoMessage(buildFlowResponse(deletedFlow.getForward()),
                    message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND, null));
            outputCollector.emit(StreamType.RESPONSE.toString(), tuple, values);
        } catch (FlowNotFoundException e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.NOT_FOUND, errorType, e.getMessage());
        } catch (Exception e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.DELETION_FAILURE, errorType, e.getMessage());
        }
    }

    private void handleCreateRequest(CommandMessage message, Tuple tuple) {
        final String errorType = "Could not create flow";

        try {
            featureTogglesService.checkFeatureToggleEnabled(FeatureToggle.CREATE_FLOW);

            FlowCreateRequest request = (FlowCreateRequest) message.getData();
            Flow flow = FlowMapper.INSTANCE.map(request.getPayload());

            FlowPair createdFlow = flowService.createFlow(flow,
                    request.getDiverseFlowId(),
                    new CrudFlowCommandSender(message.getCorrelationId(), tuple, StreamType.CREATE));

            logger.info("Created the flow: {}", createdFlow);

            Values values = new Values(new InfoMessage(buildFlowResponse(createdFlow.getForward()),
                    message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND, null));
            outputCollector.emit(StreamType.RESPONSE.toString(), tuple, values);
        } catch (FlowValidationException e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    e.getType(), errorType, e.getMessage());
        } catch (SwitchValidationException e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.DATA_INVALID, errorType, e.getMessage());
        } catch (FlowAlreadyExistException e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.ALREADY_EXISTS, errorType, e.getMessage());
        } catch (UnroutableFlowException e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.NOT_FOUND, errorType, "Not enough bandwidth found or path not found : " + e.getMessage());
        } catch (FlowNotFoundException e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.NOT_FOUND, errorType, "The flow not found :  " + e.getMessage());
        } catch (Exception e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.CREATION_FAILURE, errorType, e.getMessage());
        }
    }

    private void handleRerouteRequest(CommandMessage message, Tuple tuple) {
        FlowRerouteRequest request = (FlowRerouteRequest) message.getData();
        final String flowId = request.getFlowId();
        final String errorType = "Could not reroute flow";

        try {
            ReroutedFlow reroutedFlow = flowService.rerouteFlow(flowId, request.isForce(),
                    new CrudFlowCommandSender(message.getCorrelationId(), tuple, StreamType.UPDATE));

            if (reroutedFlow.getNewFlow() != null) {
                logger.warn("Rerouted flow: {}", reroutedFlow);
            } else {
                // There's no new path found, but the current flow may still be active.
                logger.warn("Reroute {} is unsuccessful: can't find new path.", flowId);
            }

            PathInfoData currentPath = FlowPathMapper.INSTANCE.map(reroutedFlow.getOldFlow()
                    .getForward().getFlowPath());
            PathInfoData resultPath = Optional.ofNullable(reroutedFlow.getNewFlow())
                    .map(flow -> FlowPathMapper.INSTANCE.map(flow.getForward().getFlowPath()))
                    .orElse(currentPath);

            FlowRerouteResponse response = new FlowRerouteResponse(resultPath, !resultPath.equals(currentPath));
            Values values = new Values(new InfoMessage(response, message.getTimestamp(),
                    message.getCorrelationId(), Destination.NORTHBOUND, null));
            outputCollector.emit(StreamType.RESPONSE.toString(), tuple, values);
        } catch (FlowNotFoundException e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.NOT_FOUND, errorType, e.getMessage());
        } catch (UnroutableFlowException e) {
            logger.warn("There is no path available for the flow {}", flowId);
            flowService.updateFlowStatus(flowId, FlowStatus.DOWN);
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.NOT_FOUND, errorType, "Path was not found");
        } catch (Exception e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.UPDATE_FAILURE, errorType, e.getMessage());
        }
    }

    private void handleUpdateRequest(CommandMessage message, Tuple tuple) {
        final String errorType = "Could not update flow";

        try {
            featureTogglesService.checkFeatureToggleEnabled(FeatureToggle.UPDATE_FLOW);

            FlowUpdateRequest request = (FlowUpdateRequest) message.getData();
            Flow flow = FlowMapper.INSTANCE.map((request).getPayload());

            FlowPair updatedFlow = flowService.updateFlow(flow,
                    request.getDiverseFlowId(),
                    new CrudFlowCommandSender(message.getCorrelationId(), tuple, StreamType.UPDATE));

            logger.info("Updated the flow: {}", updatedFlow);

            Values values = new Values(new InfoMessage(buildFlowResponse(updatedFlow.getForward()),
                    message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND, null));
            outputCollector.emit(StreamType.RESPONSE.toString(), tuple, values);
        } catch (FlowValidationException e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    e.getType(), errorType, e.getMessage());
        } catch (SwitchValidationException e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.DATA_INVALID, errorType, e.getMessage());
        } catch (FlowNotFoundException e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.NOT_FOUND, errorType, e.getMessage());
        } catch (UnroutableFlowException e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.NOT_FOUND, errorType, "Not enough bandwidth found or path not found");
        } catch (Exception e) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    ErrorType.UPDATE_FAILURE, errorType, e.getMessage());
        }
    }

    private void handleDumpRequest(CommandMessage message, Tuple tuple) {
        List<BidirectionalFlowDto> flows = flowService.getFlows().stream()
                .map(x -> new BidirectionalFlowDto(FlowMapper.INSTANCE.map(x)))
                .collect(Collectors.toList());

        logger.debug("Dump flows: found {} items", flows.size());

        String requestId = message.getCorrelationId();
        if (flows.isEmpty()) {
            Message response = new ChunkedInfoMessage(null, System.currentTimeMillis(), requestId, requestId,
                    flows.size());
            outputCollector.emit(StreamType.RESPONSE.toString(), tuple, new Values(response));
        } else {
            int i = 0;
            for (BidirectionalFlowDto flow : flows) {
                Message response = new ChunkedInfoMessage(new FlowReadResponse(flow), System.currentTimeMillis(),
                        requestId, i++, flows.size());

                outputCollector.emit(StreamType.RESPONSE.toString(), tuple, new Values(response));
            }
        }
    }

    private void handleReadRequest(String flowId, CommandMessage message, Tuple tuple) {
        FlowPair flowPair = flowService.getFlowPair(flowId)
                .orElseThrow(() -> new ClientException(message.getCorrelationId(), System.currentTimeMillis(),
                        ErrorType.NOT_FOUND, "Can not get flow", String.format("Flow %s not found", flowId)));

        BidirectionalFlowDto flow =
                new BidirectionalFlowDto(FlowMapper.INSTANCE.map(flowPair));
        logger.debug("Got bidirectional flow: {}, correlationId {}", flow, message.getCorrelationId());

        Values values = new Values(new InfoMessage(new FlowReadResponse(flow),
                message.getTimestamp(), message.getCorrelationId(), Destination.NORTHBOUND, null));
        outputCollector.emit(StreamType.RESPONSE.toString(), tuple, values);
    }

    private void handleMeterModeRequest(CommandMessage inMessage, Tuple tuple, final String flowId) {
        FlowPair flowPair = flowService.getFlowPair(flowId)
                .orElseThrow(() -> new MessageException(inMessage.getCorrelationId(), System.currentTimeMillis(),
                        ErrorType.NOT_FOUND, "Can not get flow", String.format("Flow %s not found", flowId)));

        SwitchId fwdSwitchId = flowPair.getForward().getSrcSwitch().getSwitchId();
        SwitchId rvsSwitchId = flowPair.getReverse().getSrcSwitch().getSwitchId();
        long bandwidth = flowPair.getForward().getBandwidth();
        Integer fwdMeterId = flowPair.getForward().getMeterId();
        Integer rvsMeterId = flowPair.getReverse().getMeterId();

        MeterModifyCommandRequest request = new MeterModifyCommandRequest(fwdSwitchId, fwdMeterId,
                rvsSwitchId, rvsMeterId, bandwidth);
        CommandMessage message = new CommandMessage(request, System.currentTimeMillis(), inMessage.getCorrelationId());

        try {
            outputCollector.emit(StreamType.METER_MODE.toString(), tuple,
                    new Values(MAPPER.writeValueAsString(message)));
        } catch (JsonProcessingException e) {
            logger.error("Unable to serialize {}", message);
        }
    }

    /**
     * Builds flow response entity.
     *
     * @param flow a flow for payload
     * @return flow response entity
     */
    private FlowResponse buildFlowResponse(Flow flow) {
        FlowDto flowDto = FlowMapper.INSTANCE.map(flow);
        flowDto.setCookie(flow.getCookie() & ResourceCache.FLOW_COOKIE_VALUE_MASK);
        return new FlowResponse(flowDto);
    }

    private ErrorMessage buildErrorMessage(String correlationId, ErrorData errorData) {
        return new ErrorMessage(errorData, System.currentTimeMillis(), correlationId, Destination.NORTHBOUND);
    }

    private ErrorMessage buildClientErrorMessage(String correlationId, ErrorData errorData) {
        return new ClientErrorMessage(errorData, System.currentTimeMillis(), correlationId, Destination.NORTHBOUND);
    }

    private void initFlowResourcesManager() {
        flowResourcesManager.clear();

        repositoryFactory.createFlowRepository().findAllFlowPairs()
                .forEach(flowPair -> flowResourcesManager.registerUsedByFlow(flowPair));
    }

    @Override
    public AbstractDumpState dumpState() {
        // Not implemented
        return new CrudBoltState();
    }

    @VisibleForTesting
    @Override
    public void clearState() {
        logger.info("State clear request from test");
        initState(new InMemoryKeyValueState<>());
    }

    @Override
    public AbstractDumpState dumpStateBySwitchId(SwitchId switchId) {
        // Not implemented
        return new CrudBoltState();
    }

    @Override
    public String getCtrlStreamId() {
        return STREAM_ID_CTRL;
    }

    @Override
    public TopologyContext getContext() {
        return context;
    }

    @Override
    public OutputCollector getOutput() {
        return outputCollector;
    }

    @Override
    public Optional<AbstractDumpState> dumpResorceCacheState() {
        return Optional.of(new ResorceCacheBoltState(
                flowResourcesManager.getAllocatedMeters(),
                flowResourcesManager.getAllocatedVlans(),
                flowResourcesManager.getAllocatedCookies()));
    }

    class CrudFlowCommandSender implements FlowCommandSender {
        private final String correlationId;
        private final Tuple tuple;
        private final StreamType stream;

        CrudFlowCommandSender(String correlationId, Tuple tuple, StreamType stream) {
            this.correlationId = correlationId;
            this.tuple = tuple;
            this.stream = stream;
        }

        @Override
        public void sendInstallRulesCommand(FlowPairWithSegments flowWithSegments) {
            List<FlowCommandGroup> commandGroups = createInstallGroups(flowWithSegments.getFlowPair(),
                    flowWithSegments.getForwardSegments(), flowWithSegments.getReverseSegments());
            sendRulesCommand(flowWithSegments.getFlowPair().getForward().getFlowId(), commandGroups);
        }

        @Override
        public void sendUpdateRulesCommand(UpdatedFlowPairWithSegments flowWithSegments) {
            List<FlowCommandGroup> commandGroups = new ArrayList<>();

            commandGroups.addAll(createInstallGroups(flowWithSegments.getFlowPair(),
                    flowWithSegments.getForwardSegments(), flowWithSegments.getReverseSegments()));

            commandGroups.addAll(createRemoveGroups(flowWithSegments.getOldFlowPair(),
                    flowWithSegments.getOldForwardSegments(), flowWithSegments.getOldReverseSegments()));

            sendRulesCommand(flowWithSegments.getFlowPair().getForward().getFlowId(), commandGroups);
        }

        @Override
        public void sendRemoveRulesCommand(FlowPairWithSegments flowWithSegments) {
            List<FlowCommandGroup> commandGroups = createRemoveGroups(flowWithSegments.getFlowPair(),
                    flowWithSegments.getForwardSegments(), flowWithSegments.getReverseSegments());
            sendRulesCommand(flowWithSegments.getFlowPair().getForward().getFlowId(), commandGroups);
        }

        private List<FlowCommandGroup> createInstallGroups(FlowPair flow,
                                                           List<FlowSegment> forwardSegments,
                                                           List<FlowSegment> reverseSegments) {
            List<FlowCommandGroup> commandGroups = new ArrayList<>();

            createInstallTransitAndEgressRules(flow.getForward(), forwardSegments)
                    .ifPresent(commandGroups::add);
            createInstallTransitAndEgressRules(flow.getReverse(), reverseSegments)
                    .ifPresent(commandGroups::add);
            // The ingress rule must be installed after the egress and transit ones.
            commandGroups.add(createInstallIngressRules(flow.getForward(), forwardSegments));
            commandGroups.add(createInstallIngressRules(flow.getReverse(), reverseSegments));

            return commandGroups;
        }

        private Optional<FlowCommandGroup> createInstallTransitAndEgressRules(Flow flow, List<FlowSegment> segments) {
            List<InstallTransitFlow> rules = commandFactory.createInstallTransitAndEgressRulesForFlow(flow, segments);
            return !rules.isEmpty() ? Optional.of(new FlowCommandGroup(rules, FailureReaction.ABORT_FLOW))
                    : Optional.empty();
        }

        private FlowCommandGroup createInstallIngressRules(Flow flow, List<FlowSegment> segments) {
            return new FlowCommandGroup(Collections.singletonList(
                    commandFactory.createInstallIngressRulesForFlow(flow, segments)), FailureReaction.ABORT_FLOW);
        }

        private List<FlowCommandGroup> createRemoveGroups(FlowPair flow,
                                                          List<FlowSegment> forwardSegments,
                                                          List<FlowSegment> reverseSegments) {
            List<FlowCommandGroup> commandGroups = new ArrayList<>();

            commandGroups.add(createRemoveIngressRules(flow.getForward(), forwardSegments));
            commandGroups.add(createRemoveIngressRules(flow.getReverse(), reverseSegments));
            createRemoveTransitAndEgressRules(flow.getForward(), forwardSegments)
                    .ifPresent(commandGroups::add);
            createRemoveTransitAndEgressRules(flow.getReverse(), reverseSegments)
                    .ifPresent(commandGroups::add);

            return commandGroups;
        }

        private Optional<FlowCommandGroup> createRemoveTransitAndEgressRules(Flow flow, List<FlowSegment> segments) {
            List<RemoveFlow> rules = commandFactory.createRemoveTransitAndEgressRulesForFlow(flow, segments);
            return !rules.isEmpty() ? Optional.of(new FlowCommandGroup(rules, FailureReaction.IGNORE))
                    : Optional.empty();
        }

        private FlowCommandGroup createRemoveIngressRules(Flow flow, List<FlowSegment> segments) {
            return new FlowCommandGroup(Collections.singletonList(
                    commandFactory.createRemoveIngressRulesForFlow(flow, segments)), FailureReaction.IGNORE);
        }

        private void sendRulesCommand(String flowId, List<FlowCommandGroup> commandGroups) {
            CommandMessage message = new CommandMessage(new BatchFlowCommandsRequest(commandGroups),
                    System.currentTimeMillis(), correlationId, Destination.CONTROLLER);
            outputCollector.emit(stream.toString(), tuple, new Values(message, flowId));
        }
    }
}
