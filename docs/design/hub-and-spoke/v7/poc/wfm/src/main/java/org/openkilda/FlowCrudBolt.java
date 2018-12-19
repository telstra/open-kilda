package org.openkilda;

import org.openkilda.PathService.Path;
import org.openkilda.hubandspoke.HubBolt;
import org.openkilda.model.CheckRule;
import org.openkilda.model.FlowCreate;
import org.openkilda.model.FlowCreateError;
import org.openkilda.model.InstallRule;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class FlowCrudBolt extends HubBolt {

    private Map<String, Integer> rulesToSetUp = new HashMap<>();
    private Map<String, String> keyToFlowId = new HashMap<>();
    private Map<String, Integer> rulesToCheck = new HashMap<>();

    public FlowCrudBolt(Config config) {
        super(config);
    }

    @Override
    protected void processTimeoutTuple(Tuple input) {
        String key = mapper.getKeyFromTuple(input);
        if (rulesToSetUp.containsKey(key)) {
            log.info("timeout callback received. going to start cleanup procedure");
            String flowid = keyToFlowId.remove(key);
            clear(flowid);
            log.info("pass message to nb");
            collector.emit(streamHubBoltToRequester, new Values(
                    mapper.getKeyFromTuple(input),
                    "timeout error in hub"));
        }
        saveStateToDb();
    }

    @Override
    protected void processIncomeTuple(Tuple input) {
        String key = mapper.getKeyFromTuple(input);
        registerCallback(key);
        try {
            FlowCreate flow = Utils.MAPPER.readValue(mapper.getMessageFromTuple(input), FlowCreate.class);

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
                    collector.emit(streamHubBoltToWorkerBolt, new Values(
                            String.format("%s-%d", mapper.getKeyFromTuple(input), i),
                            InstallRule.builder().flowid(flowid).ruleId(i).build()
                    ));
                }
                HistoryService.pushStatus(flowid);
            } else if (error == FlowCreateError.IN_WORKER || error == FlowCreateError.IN_FL) {
                HistoryService.pushStatus(flowid);
                log.info("pass message with error to worker");
                collector.emit(streamHubBoltToWorkerBolt, new Values(
                        String.format("%s-%d", mapper.getKeyFromTuple(input), 0),
                        InstallRule.builder().flowid(flowid).error(error).build()));
            } else if (error == FlowCreateError.IN_HUB) {
                log.info("error in hub. do nothing and waiting for timeout callback");
            }
        } catch (IOException e) {
            log.error("can't read message", e);
        }
        saveStateToDb();
    }

    private void saveStateToDb() {
        // Save state of bolt to db for recover purpose
        log.info("backup bolt state");
    }

    @Override
    protected void processWorkerResponseTuple(Tuple input) {
        String key = mapper.getKeyFromTuple(input);
        if (rulesToSetUp.containsKey(key) || rulesToCheck.containsKey(key)) {
            String message = mapper.getMessageFromTuple(input);
            String flowid = keyToFlowId.remove(key);
            if (message.contains("error")) {
                log.info("some error from worker, going to start cleanup procedure");
                log.info("clear callback");

                clear(flowid);

                cancelCallback(key);
                log.info("pass message to nb");
                collector.emit(streamHubBoltToRequester, new Values(
                        mapper.getKeyFromTuple(input),
                        "error in worker"));
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
                            collector.emit(streamHubBoltToWorkerBolt, new Values(
                                    String.format("%s-%d", mapper.getKeyFromTuple(input), i),
                                    CheckRule.builder().flowid(flowid).ruleId(i).build()
                            ));
                        }
                        rulesToSetUp.remove(key);
                    } else {
                        rulesToSetUp.put(key, setupRulesCounter);
                    }
                } else if (CheckRulesCounter != null) {
                    CheckRulesCounter -= 1;
                    if (CheckRulesCounter == 0) {
                        log.info("clear callback");
                        cancelCallback(key);
                        FlowService.updateFlowStatusWithSuccess(flowid);
                        HistoryService.pushStatus(flowid);
                        log.info("pass message to nb");
                        collector.emit(streamHubBoltToRequester, new Values(
                                mapper.getKeyFromTuple(input),
                                "all operation is successful"));
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

    private void clear(String flowid) {
        PathService.deallocatePath(flowid);
        FlowService.updateFlowStatusWithError(flowid);
        HistoryService.pushStatus(flowid);
    }
}
