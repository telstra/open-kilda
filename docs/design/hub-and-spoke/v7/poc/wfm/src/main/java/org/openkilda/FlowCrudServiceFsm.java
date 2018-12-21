package org.openkilda;

import org.openkilda.FlowCrudFsm.FlowCrudEvent;
import org.openkilda.FlowCrudFsm.FlowCrudState;
import org.openkilda.model.FlowCreate;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineLogger;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class FlowCrudServiceFsm implements IFlowCrudService {

    private Map<String, FlowCrudFsm> fsms = new HashMap<>();

    IFlowCrudCarrier carrier;
    private final StateMachineBuilder<FlowCrudFsm, FlowCrudState, FlowCrudEvent, Object> builder;

    public FlowCrudServiceFsm(IFlowCrudCarrier carrier) {
        this.carrier = carrier;
        this.builder = FlowCrudFsm.builder();
    }

    @Override
    public void handleFlowCreate(String key, FlowCreate flow) {
        log.info("handleFlowCreate");
        FlowCrudFsm fsm = builder.newStateMachine(FlowCrudState.Initilized, carrier, key, flow);


        StateMachineLogger fsmLogger = new StateMachineLogger(fsm);
        fsmLogger.startLogging();

        saveStateToDb(fsm);
        process(fsm);
    }

    @Override
    public void handleAsyncResponseFromWorker(String key, String message) {

        FlowCrudFsm fsm = getFsm(key);
        if (fsm == null) {
            log.error("missed field grouping");
            return;
        }

        if (message.contains("installed")) {
            fsm.fire(FlowCrudEvent.RuleInstalled, message);
        } else if (message.contains("validated")) {
            fsm.fire(FlowCrudEvent.RuleValidated, message);
        } else if (message.contains("error")) {
            fsm.fire(FlowCrudEvent.WorkerError, message);
        }
        process(fsm);
    }

    @Override
    public void handleTaskTimeout(String key) {
        FlowCrudFsm fsm = getFsm(key);
        if (fsm == null) {
            log.error("missed field grouping");
            return;
        }
        fsm.fire(FlowCrudEvent.TimeoutRised);
        process(fsm);
    }

    void process(FlowCrudFsm fsm) {
        final List<FlowCrudState> stopStates = Arrays.asList(
                FlowCrudState.InstallingRules,
                FlowCrudState.RemovingRules,
                FlowCrudState.ValidatetingRules,
                FlowCrudState.ValidatetingRemovedRules,
                FlowCrudState.Finished,
                FlowCrudState.FinishedWithError
                );

        while (!stopStates.contains(fsm.getCurrentState()))
        {
            saveStateToDb(fsm);
            fsm.fire(FlowCrudEvent.Next);
        }
    }

    void saveStateToDb(FlowCrudFsm fsm) {
        // in real kilda we will send fsm to database
        fsms.put(fsm.getKey(), fsm);
        log.info("save state to db");
    }

    FlowCrudFsm getFsm(String key) {
        return fsms.get(key);
    }
}
