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

package org.openkilda.wfm.topology.discovery.service;

import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.switches.UnmanagedSwitchNotification;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.discovery.controller.SwitchFsm;
import org.openkilda.wfm.topology.discovery.controller.SwitchFsm.SwitchFsmContext;
import org.openkilda.wfm.topology.discovery.controller.SwitchFsm.SwitchFsmEvent;
import org.openkilda.wfm.topology.discovery.controller.SwitchFsm.SwitchFsmState;
import org.openkilda.wfm.topology.discovery.model.facts.HistoryFacts;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DiscoverySwitchService {
    private final Map<SwitchId, SwitchFsm> controller = new HashMap<>();
    private final FsmExecutor<SwitchFsm, SwitchFsmState, SwitchFsmEvent, SwitchFsmContext> controllerExecutor
            = SwitchFsm.makeExecutor();

    private final PersistenceManager persistenceManager;

    private final int bfdLogicalPortOffset;

    ISwitchCarrier carrier;

    public DiscoverySwitchService(ISwitchCarrier carrier, PersistenceManager persistenceManager,
                                  Integer bfdLogicalPortOffset) {
        this.carrier = carrier;
        this.persistenceManager = persistenceManager;
        this.bfdLogicalPortOffset = bfdLogicalPortOffset;
    }

    /**
     * .
     */
    public void switchAddWithHistory(HistoryFacts history) {
        log.debug("Switch service receive switch ADD from history request for {}", history.getSwitchId());
        SwitchFsm switchFsm = SwitchFsm.create(persistenceManager, history.getSwitchId(), bfdLogicalPortOffset);

        SwitchFsmContext fsmContext = SwitchFsmContext.builder(carrier)
                .history(history)
                .build();
        controller.put(history.getSwitchId(), switchFsm);
        controllerExecutor.fire(switchFsm, SwitchFsmEvent.HISTORY, fsmContext);
    }

    /**
     * .
     */
    public void switchEvent(SwitchInfoData payload) {
        log.debug("Switch service receive SWITCH event for {} status:{}", payload.getSwitchId(), payload.getState());
        SwitchFsmContext.SwitchFsmContextBuilder fsmContextBuilder = SwitchFsmContext.builder(carrier);
        SwitchFsmEvent event = null;

        switch (payload.getState()) {
            case ACTIVATED:
                event = SwitchFsmEvent.ONLINE;
                fsmContextBuilder.speakerData(payload.getSwitchView());
                break;
            case DEACTIVATED:
                event = SwitchFsmEvent.OFFLINE;
                break;

            default:
                log.info("Ignore switch event {} on {} (no need to handle it)", payload.getState(),
                        payload.getSwitchId());
                break;
        }

        if (event != null) {
            SwitchFsm fsm = locateControllerCreateIfAbsent(payload.getSwitchId());
            controllerExecutor.fire(fsm, event, fsmContextBuilder.build());
        }
    }

    /**
     * .
     */
    public void switchEvent(UnmanagedSwitchNotification payload) {
        log.debug("Switch service receive unmanaged notification for {}", payload.getSwitchId());
        SwitchFsmContext.SwitchFsmContextBuilder fsmContextBuilder = SwitchFsmContext.builder(carrier);
        SwitchFsm fsm = locateControllerCreateIfAbsent(payload.getSwitchId());
        controllerExecutor.fire(fsm, SwitchFsmEvent.OFFLINE, fsmContextBuilder.build());
    }

    /**
     * .
     */
    public void switchPortEvent(PortInfoData payload) {
        log.debug("Switch service receive PORT event for {} port:{}, status:{}",
                  payload.getSwitchId(), payload.getPortNo(), payload.getState());
        SwitchFsmContext fsmContext = SwitchFsmContext.builder(carrier)
                .portNumber(payload.getPortNo())
                .build();
        SwitchFsmEvent event = null;
        switch (payload.getState()) {
            case ADD:
                event = SwitchFsmEvent.PORT_ADD;
                break;
            case DELETE:
                event = SwitchFsmEvent.PORT_DEL;
                break;
            case UP:
                event = SwitchFsmEvent.PORT_UP;
                break;
            case DOWN:
                event = SwitchFsmEvent.PORT_DOWN;
                break;

            case OTHER_UPDATE:
            case CACHED:
                log.error("Invalid port event {}_{} - incomplete or deprecated",
                          payload.getSwitchId(), payload.getPortNo());
                break;

            default:
                log.info("Ignore port event {}_{} (no need to handle it)", payload.getSwitchId(), payload.getPortNo());
        }

        if (event != null) {
            SwitchFsm switchFsm = locateController(payload.getSwitchId());
            controllerExecutor.fire(switchFsm, event, fsmContext);
        }
    }

    /**
     * Remove isl by request.
     */
    public void remove(SwitchId datapath) {
        log.debug("Switch service receive remove request for {}", datapath);
        SwitchFsm fsm = controller.get(datapath);
        if (fsm != null) {
            SwitchFsmContext context = SwitchFsmContext.builder(carrier).build();
            controllerExecutor.fire(fsm, SwitchFsmEvent.SWITCH_REMOVE, context);
            if (fsm.getCurrentState() == SwitchFsmState.DELETED) {
                controller.remove(datapath);
                log.debug("Switch service removed FSM {}", datapath);
            } else {
                log.error("Switch service remove failed for FSM {}, state: {}", datapath, fsm.getCurrentState());
            }
        }
    }

    // -- private --

    private SwitchFsm locateController(SwitchId datapath) {
        SwitchFsm switchFsm = controller.get(datapath);
        if (switchFsm == null) {
            throw new IllegalStateException(String.format("Switch FSM not found (%s).", datapath));
        }
        return switchFsm;
    }

    private SwitchFsm locateControllerCreateIfAbsent(SwitchId datapath) {
        return controller.computeIfAbsent(
                datapath, key -> SwitchFsm.create(persistenceManager, datapath, bfdLogicalPortOffset));
    }
}
