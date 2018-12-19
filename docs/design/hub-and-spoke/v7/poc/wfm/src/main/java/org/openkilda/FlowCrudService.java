package org.openkilda;

import org.openkilda.PathService.Path;
import org.openkilda.model.CheckRule;
import org.openkilda.model.FlowCreate;
import org.openkilda.model.FlowCreateError;
import org.openkilda.model.InstallRule;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class FlowCrudService {

    private Map<String, Integer> rulesToSetUp = new HashMap<>();
    private Map<String, String> keyToFlowId = new HashMap<>();
    private Map<String, Integer> rulesToCheck = new HashMap<>();

    IFlowCrudCarrier carrier;

    public FlowCrudService(IFlowCrudCarrier carrier) {
        this.carrier = carrier;
    }


    private void saveStateToDb() {
        // Save state of bolt to db for recover purpose
        log.info("backup bolt state");
    }


    private void clear(String flowid) {
        PathService.deallocatePath(flowid);
        FlowService.updateFlowStatusWithError(flowid);
        HistoryService.pushStatus(flowid);
    }

    public void handleFlowCreate(String key, FlowCreate flow) {
        carrier.registerCallback(key);
        String flowid = flow.getFlowid();
        FlowService.checkFlowId(flowid);
        FlowService.createFlowInDb(flowid);
        HistoryService.pushStatus(flowid);

        int hops = flow.getLength();
        FlowCreateError error = flow.getError();

        rulesToSetUp.put(key, hops);
        keyToFlowId.put(key, flowid);
        rulesToCheck.put(key, hops);

        if (error == null) {
            Path path = PathService.allocatePath();
            FlowService.updateFlowWithPath(flowid, path);
            HistoryService.pushStatus(flowid);

            log.info("pass messages to worker");
            for (int i = 0; i < hops; ++i) {
                carrier.installRule(String.format("%s-%d", key, i),
                        InstallRule.builder().flowid(flowid).ruleId(i).build());
            }
            HistoryService.pushStatus(flowid);
        } else if (error == FlowCreateError.IN_WORKER || error == FlowCreateError.IN_FL) {
            HistoryService.pushStatus(flowid);
            log.info("pass message with error to worker");
            carrier.installRule(String.format("%s-%d", key, 0),
                    InstallRule.builder().flowid(flowid).error(error).build());
        } else if (error == FlowCreateError.IN_HUB) {
            log.info("error in hub. do nothing and waiting for timeout callback");
        }
    }

    public void handleAsyncResponseFromWorker(String key, String message) {
        if (rulesToSetUp.containsKey(key) || rulesToCheck.containsKey(key)) {

            String flowid = keyToFlowId.remove(key);
            if (message.contains("error")) {
                log.info("some error from worker, going to start cleanup procedure");
                log.info("clear callback");

                clear(flowid);

                carrier.cancelCallback(key);
                log.info("pass message to nb");
                carrier.response(key, "error in worker");
                rulesToSetUp.remove(key);
            } else {
                Integer setupRulesCounter = rulesToSetUp.get(key);
                Integer CheckRulesCounter = rulesToCheck.get(key);
                if (setupRulesCounter != null) {
                    setupRulesCounter -= 1;
                    if (setupRulesCounter == 0) {

                        FlowService.updateFlowStatusWithFinishInstall(flowid);
                        HistoryService.pushStatus(flowid);
                        Integer hops = rulesToCheck.get(key);
                        log.info("validate");
                        for (int i = 0; i < hops; ++i) {
                            carrier.checkRule(String.format("%s-%d", key, i),
                                    CheckRule.builder().flowid(flowid).ruleId(i).build());

                        }
                        rulesToSetUp.remove(key);
                    } else {
                        rulesToSetUp.put(key, setupRulesCounter);
                    }
                } else if (CheckRulesCounter != null) {
                    CheckRulesCounter -= 1;
                    if (CheckRulesCounter == 0) {
                        log.info("clear callback");
                        carrier.cancelCallback(key);
                        FlowService.updateFlowStatusWithSuccess(flowid);
                        HistoryService.pushStatus(flowid);
                        log.info("pass message to nb");
                        carrier.response(key, "all operation is successful");
                        rulesToCheck.remove(key);
                    } else {
                        rulesToCheck.put(key, CheckRulesCounter);
                    }
                }
            }
        } else {
            log.error("missed field grouping");
        }
        saveStateToDb();
    }

    public void handleTaskTimeout(String key) {
        if (rulesToSetUp.containsKey(key)) {
            log.info("timeout callback received. going to start cleanup procedure");
            String flowid = keyToFlowId.remove(key);
            clear(flowid);
            log.info("pass message to nb");
            carrier.response(key, "timeout error in hub");
        }
        saveStateToDb();
    }
}
