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
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.discovery.controller.SwitchFsm;
import org.openkilda.wfm.topology.discovery.controller.SwitchFsmContext;
import org.openkilda.wfm.topology.discovery.controller.SwitchFsmEvent;
import org.openkilda.wfm.topology.discovery.controller.SwitchFsmState;
import org.openkilda.wfm.topology.discovery.model.OperationMode;
import org.openkilda.wfm.topology.discovery.model.SpeakerSharedSync;
import org.openkilda.wfm.topology.discovery.model.facts.HistoryFacts;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
public class DiscoverySwitchService {
    private final Map<SwitchId, SwitchFsm> controller = new HashMap<>();
    private final FsmExecutor<SwitchFsm, SwitchFsmState, SwitchFsmEvent, SwitchFsmContext> controllerExecutor
            = SwitchFsm.makeExecutor();

    private final PersistenceManager persistenceManager;

    private final int bfdLocalPortOffset;

    public DiscoverySwitchService(PersistenceManager persistenceManager, Integer bfdLocalPortOffset) {
        this.persistenceManager = persistenceManager;
        this.bfdLocalPortOffset = bfdLocalPortOffset;
    }

    /**
     * .
     */
    public void switchAddWithHistory(ISwitchCarrier carrier, HistoryFacts history) {
        log.debug("Switch ADD with history (sw: {})", history.getSwitchId());
        SwitchFsm switchFsm = SwitchFsm.create(persistenceManager, history.getSwitchId(), bfdLocalPortOffset);

        SwitchFsmContext fsmContext = SwitchFsmContext.builder(carrier)
                .history(history)
                .build();
        controller.put(history.getSwitchId(), switchFsm);
        controllerExecutor.fire(switchFsm, SwitchFsmEvent.HISTORY, fsmContext);
    }

    /**
     * .
     */
    public void switchRestoreManagement(ISwitchCarrier carrier, SpeakerSwitchView switchView) {
        SwitchFsmContext fsmContext = SwitchFsmContext.builder(carrier)
                .speakerData(switchView)
                .build();
        SwitchFsm fsm = locateControllerCreateIfAbsent(switchView.getDatapath());
        controllerExecutor.fire(fsm, SwitchFsmEvent.ONLINE, fsmContext);
    }

    /**
     * .
     */
    public void switchSharedSync(ISwitchCarrier carrier, SpeakerSharedSync sharedSync) {
        // FIXME(surabujin): invalid in multi-FL environment
        switch (sharedSync.getMode()) {
            case MANAGED_MODE:
                // Still connected switches will be handled by {@link switchRestoreManagement}, disconnected switches
                // must be handled here.
                detectOfflineSwitches(sharedSync.getKnownSwitches());
                break;
            case UNMANAGED_MODE:
                setAllSwitchesUnmanaged(carrier);
                break;
            default:
                throw new IllegalArgumentException(
                        String.format("Unsupported %s value %s", OperationMode.class.getName(), sharedSync.getMode()));
        }
    }

    /**
     * .
     */
    public void switchEvent(ISwitchCarrier carrier, SwitchInfoData payload) {
        log.debug("Switch event (sw: {}, become: {})", payload.getSwitchId(), payload.getState());
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
                log.info("Ignore switch event {} (no need to handle it)", payload.getSwitchId());
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
    public void switchEvent(ISwitchCarrier carrier, UnmanagedSwitchNotification payload) {
        log.debug("Switch become unmanaged (sw: {})", payload.getSwitchId());
        SwitchFsmContext.SwitchFsmContextBuilder fsmContextBuilder = SwitchFsmContext.builder(carrier);
        SwitchFsm fsm = locateControllerCreateIfAbsent(payload.getSwitchId());
        controllerExecutor.fire(fsm, SwitchFsmEvent.OFFLINE, fsmContextBuilder.build());
    }

    /**
     * .
     */
    public void switchPortEvent(ISwitchCarrier carrier, PortInfoData payload) {
        log.debug("Port event (sw: {}, port: {})", payload.getSwitchId(), payload.getPortNo());
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

    // -- private --

    private void detectOfflineSwitches(Set<SwitchId> knownSwitches) {
        Set<SwitchId> extraSwitches = new HashSet<>(controller.keySet());
        extraSwitches.removeAll(knownSwitches);

        for (SwitchId entryId : extraSwitches) {
            controller.remove(entryId);
        }
    }

    private void setAllSwitchesUnmanaged(ISwitchCarrier carrier) {
        SwitchFsmContext fsmContext = SwitchFsmContext.builder(carrier).build();
        for (SwitchFsm switchFsm : controller.values()) {
            controllerExecutor.fire(switchFsm, SwitchFsmEvent.OFFLINE, fsmContext);
        }
    }

    private SwitchFsm locateController(SwitchId datapath) {
        SwitchFsm switchFsm = controller.get(datapath);
        if (switchFsm == null) {
            throw new IllegalStateException(String.format("Switch FSM not found (%s).", datapath));
        }
        return switchFsm;
    }

    private SwitchFsm locateControllerCreateIfAbsent(SwitchId datapath) {
        return controller.computeIfAbsent(
                datapath, key -> SwitchFsm.create(persistenceManager, datapath, bfdLocalPortOffset));
    }
}
