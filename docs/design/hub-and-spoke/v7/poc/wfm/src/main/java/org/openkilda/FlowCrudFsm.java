package org.openkilda;

import static org.openkilda.FlowCrudFsm.FlowCrudEvent.Next;
import static org.openkilda.FlowCrudFsm.FlowCrudState.AllRulesInstalled;
import static org.openkilda.FlowCrudFsm.FlowCrudState.AllRulesRemoveValidated;
import static org.openkilda.FlowCrudFsm.FlowCrudState.AllRulesRemoved;
import static org.openkilda.FlowCrudFsm.FlowCrudState.DeallocateResources;
import static org.openkilda.FlowCrudFsm.FlowCrudState.Finished;
import static org.openkilda.FlowCrudFsm.FlowCrudState.FinishedWithError;
import static org.openkilda.FlowCrudFsm.FlowCrudState.Initilized;
import static org.openkilda.FlowCrudFsm.FlowCrudState.InstallingRules;
import static org.openkilda.FlowCrudFsm.FlowCrudState.RemovingRules;
import static org.openkilda.FlowCrudFsm.FlowCrudState.ResourseAllocated;
import static org.openkilda.FlowCrudFsm.FlowCrudState.ValidatetingRemovedRules;
import static org.openkilda.FlowCrudFsm.FlowCrudState.ValidatetingRules;

import org.openkilda.FlowCrudFsm.FlowCrudEvent;
import org.openkilda.FlowCrudFsm.FlowCrudState;
import org.openkilda.PathService.Path;
import org.openkilda.model.FlowCreate;
import org.openkilda.model.FlowCreateError;
import org.openkilda.model.InstallRule;
import org.openkilda.model.ValidateRule;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class FlowCrudFsm extends AbstractStateMachine<FlowCrudFsm, FlowCrudState, FlowCrudEvent, Object> {

    private final String key;
    private final FlowCreate flow;
    private final IFlowCrudCarrier carrier;
    private String flowid;
    private int rulesToSetUp;
    private int rulesToSetValidate;
    private List<String> errors = new ArrayList<>();

    public FlowCrudFsm(IFlowCrudCarrier carrier, String key, FlowCreate flow) {
        this.carrier = carrier;
        this.key = key;
        this.flow = flow;
    }

    public static StateMachineBuilder<FlowCrudFsm, FlowCrudState, FlowCrudEvent, Object> builder() {
        StateMachineBuilder<FlowCrudFsm, FlowCrudState, FlowCrudEvent, Object> builder = StateMachineBuilderFactory.create(
                FlowCrudFsm.class,
                FlowCrudState.class,
                FlowCrudEvent.class,
                Object.class,
                IFlowCrudCarrier.class,
                String.class,
                FlowCreate.class);

        builder.onEntry(Initilized).callMethod("init");
        builder.externalTransition().from(Initilized).to(ResourseAllocated).on(Next).callMethod("allocateResource");

        builder.externalTransition().from(ResourseAllocated).to(InstallingRules).on(Next).callMethod("installRules");
        builder.externalTransition().from(ResourseAllocated).to(DeallocateResources).on(FlowCrudEvent.TimeoutRised).callMethod("deallocateResources");
        builder.externalTransition().from(ResourseAllocated).to(FinishedWithError).on(FlowCrudEvent.ErrorOnResourseAllocation).callMethod("finishedWithError");

        builder.internalTransition().within(InstallingRules).on(FlowCrudEvent.RuleInstalled).callMethod("ruleInstalled");
        builder.externalTransition().from(InstallingRules).to(RemovingRules).on(FlowCrudEvent.TimeoutRised).callMethod("installingRulesFailedByTimeout");
        builder.externalTransition().from(InstallingRules).to(RemovingRules).on(FlowCrudEvent.WorkerError).callMethod("errorInWorkerOnRuleInstall");
        builder.externalTransition().from(InstallingRules).to(AllRulesInstalled).on(Next).callMethod("validateRules");

        builder.externalTransition().from(AllRulesInstalled).to(ValidatetingRules).on(Next);

        builder.internalTransition().within(ValidatetingRules).on(FlowCrudEvent.RuleValidated).callMethod("ruleValidated");
        builder.externalTransition().from(ValidatetingRules).to(Finished).on(Next).callMethod("finish");
        builder.externalTransition().from(ValidatetingRules).to(RemovingRules).on(FlowCrudEvent.TimeoutRised);

        builder.onEntry(RemovingRules).callMethod("removeRules");
        builder.internalTransition().within(RemovingRules).on(FlowCrudEvent.RuleRemoved).callMethod("ruleRemoved");
        builder.externalTransition().from(RemovingRules).to(AllRulesRemoved).on(Next);
        builder.externalTransition().from(RemovingRules).to(AllRulesRemoved).on(FlowCrudEvent.TimeoutRised).callMethod("removingRulesFailedByTimeout");

        builder.externalTransition().from(AllRulesRemoved).to(ValidatetingRemovedRules).on(Next).callMethod("validateRemovedRules");

        builder.internalTransition().within(ValidatetingRemovedRules).on(FlowCrudEvent.RuleRemoveValidated).callMethod("ruleRemoveValidated");
        builder.externalTransition().from(ValidatetingRemovedRules).to(AllRulesRemoveValidated).on(Next);
        builder.externalTransition().from(ValidatetingRemovedRules).to(AllRulesRemoveValidated).on(FlowCrudEvent.TimeoutRised).callMethod("createZombieRules");

        builder.externalTransition().from(AllRulesRemoveValidated).to(DeallocateResources).on(Next);

        builder.onEntry(DeallocateResources).callMethod("deallocateResources");
        builder.externalTransition().from(DeallocateResources).to(FinishedWithError).on(Next).callMethod("finishedWithError");

        return builder;
    }

    public String getKey() {
        return key;
    }

    void postConstruct() {

    }

    protected void init(FlowCrudState from, FlowCrudState to, FlowCrudEvent event, Object context) {
        log.info("init");
        carrier.registerCallback(key);
        flowid = flow.getFlowid();
        FlowService.checkFlowId(flowid);
        FlowService.createFlowInDb(flowid);
        HistoryService.pushStatus(flowid);
    }

    protected void removeRules(FlowCrudState from, FlowCrudState to, FlowCrudEvent event, Object context) {
        log.info("removeRules");
        // the same as install rules
        fire(Next);
    }

    protected void installingRulesFailedByTimeout(FlowCrudState from, FlowCrudState to, FlowCrudEvent event, Object context) {
        log.info("installingRulesFailedByTimeout");
        errors.add("error in hub by timeout");
    }

    protected void ruleRemoved(FlowCrudState from, FlowCrudState to, FlowCrudEvent event, Object context) {
        log.info("ruleRemoved");
    }

    protected void errorInWorkerOnRuleInstall(FlowCrudState from, FlowCrudState to, FlowCrudEvent event, Object context) {
        log.info("errorInWorkerOnRuleInstall");
        errors.add("error in worker on rule install");
    }

    protected void removingRulesFailedByTimeout(FlowCrudState from, FlowCrudState to, FlowCrudEvent event, Object context) {
        log.info("removingRulesFailedByTimeout");
    }

    protected void validateRemovedRules(FlowCrudState from, FlowCrudState to, FlowCrudEvent event, Object context) {
        log.info("validateRemovedRules");
        // the same as install rules
        fire(Next);
    }

    protected void ruleRemoveValidated(FlowCrudState from, FlowCrudState to, FlowCrudEvent event, Object context) {
        log.info("ruleRemoveValidated");
    }

    protected void createZombieRules(FlowCrudState from, FlowCrudState to, FlowCrudEvent event, Object context) {
        log.info("createZombieRules");
    }

    protected void deallocateResources(FlowCrudState from, FlowCrudState to, FlowCrudEvent event, Object context) {
        log.info("deallocateResources");
    }

    protected void allocateResource(FlowCrudState from, FlowCrudState to, FlowCrudEvent event, Object context) {
        log.info("allocateResource");
        Path path = PathService.allocatePath();
        FlowService.updateFlowWithPath(flowid, path);
        HistoryService.pushStatus(flowid);
    }

    protected void installRules(FlowCrudState from, FlowCrudState to, FlowCrudEvent event, Object context) {

        log.info("installRules");
        FlowCreateError error = flow.getError();
        if (error == FlowCreateError.IN_HUB) {
            log.info("simulate error in hub, do not install rule and wait for timeout");
        } else if (error == FlowCreateError.IN_WORKER || error == FlowCreateError.IN_FL) {
            HistoryService.pushStatus(flowid);
            log.info("pass message with error to worker");
            carrier.installRule(String.format("%s-%d", key, 0),
                    InstallRule.builder().flowid(flowid).error(error).build());
        } else {
            rulesToSetUp = flow.getLength();
            for (int i = 0; i < flow.getLength(); ++i) {
                carrier.installRule(String.format("%s-%d", key, i),
                        InstallRule.builder().flowid(flowid).ruleid(i).build());
            }
            HistoryService.pushStatus(flowid);
        }
    }

    protected void ruleInstalled(FlowCrudState from, FlowCrudState to, FlowCrudEvent event, Object context) {
        log.info("ruleInstalled");
        rulesToSetUp -= 1;
        if (rulesToSetUp == 0) {
            fire(Next);
        }
    }

    protected void validateRules(FlowCrudState from, FlowCrudState to, FlowCrudEvent event, Object context) {
        log.info("validateRules");
        rulesToSetValidate = flow.getLength();
        for (int i = 0; i < flow.getLength(); ++i) {
            carrier.checkRule(String.format("%s-%d", key, i),
                    ValidateRule.builder().flowid(flowid).ruleid(i).build());
        }
        HistoryService.pushStatus(flowid);
    }

    protected void ruleValidated(FlowCrudState from, FlowCrudState to, FlowCrudEvent event, Object context) {
        log.info("ruleValidated");
        rulesToSetValidate -= 1;
        if (rulesToSetValidate == 0) {
            fire(Next);
        }
    }

    protected void finish(FlowCrudState from, FlowCrudState to, FlowCrudEvent event, Object context) {
        log.info("finish");
        carrier.cancelCallback(key);
        FlowService.updateFlowStatusWithSuccess(flowid);
        HistoryService.pushStatus(flowid);
        log.info("pass message to nb");
        carrier.response(key, "all operation is successful");
    }

    protected void finishedWithError(FlowCrudState from, FlowCrudState to, FlowCrudEvent event, Object context) {
        log.info("finishedWithError");
        carrier.cancelCallback(key);
        FlowService.updateFlowStatusWithError(flowid);
        HistoryService.pushStatus(flowid);
        log.info("pass message to nb");
        carrier.response(key, String.join(";\n", errors));
    }

    public enum FlowCrudState {
        Initilized,
        ResourseAllocated,
        InstallingRules,
        ValidatetingRules,
        RemovingRules,
        FinishedWithError,
        ValidatetingRemovedRules,
        Finished,
        DeallocateResources,
        AllRulesRemoved,
        AllRulesInstalled,
        AllRulesRemoveValidated
    }

    public enum FlowCrudEvent {
        Next,
        RuleInstalled,
        RuleValidated,
        TimeoutRised,
        RuleRemoved,
        RuleRemoveValidated,
        ErrorOnResourseAllocation, WorkerError,
    }
}

