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

package org.openkilda.floodlight.service.batch;

import org.openkilda.floodlight.SwitchUtils;
import org.openkilda.floodlight.command.Command;
import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.model.OfInput;
import org.openkilda.floodlight.model.OfRequestResponse;
import org.openkilda.floodlight.service.of.IInputTranslator;
import org.openkilda.floodlight.service.of.InputService;

import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class OfBatchService implements IFloodlightService, IInputTranslator {
    private static final Logger log = LoggerFactory.getLogger(OfBatchService.class);

    private final HashMap<DatapathId, OfBatchSwitchQueue> pendingMap = new HashMap<>();

    private SwitchUtils switchUtils;

    /**
     * Receive notification about lost connection to switch.
     */
    public void switchDisconnect(DatapathId dpId) {
        synchronized (pendingMap) {
            OfBatchSwitchQueue queue = pendingMap.get(dpId);
            if (queue == null) {
                return;
            }

            queue.lostConnection();
            queue.cleanup();
            if (queue.isGarbage()) {
                pendingMap.remove(dpId);
            }
        }
    }

    /**
     * Service init(late) method.
     */
    public void init(FloodlightModuleContext moduleContext) {
        switchUtils = new SwitchUtils(moduleContext.getServiceImpl(IOFSwitchService.class));

        InputService inputService = moduleContext.getServiceImpl(InputService.class);
        inputService.addTranslator(OFType.ERROR, this);
        inputService.addTranslator(OFType.BARRIER_REPLY, this);
    }

    /**
     * Write prepared OFMessages to switches.
     */
    public CompletableFuture<List<OfRequestResponse>> write(List<OfRequestResponse> payload) {
        log.debug("New OF batch request with {} message(s)", payload.size());

        OfBatch batch = new OfBatch(switchUtils, payload);
        this.write(batch);

        return batch.getFuture();
    }

    void write(OfBatch batch) {
        synchronized (pendingMap) {
            for (DatapathId dpId : batch.getAffectedSwitches()) {
                OfBatchSwitchQueue queue = pendingMap.computeIfAbsent(dpId, OfBatchSwitchQueue::new);
                queue.add(batch);
            }
        }
        batch.write();
    }

    @Override
    public Command makeCommand(CommandContext context, OfInput input) {
        return new Command(context) {
            @Override
            public Command call() throws Exception {
                input(input);
                return null;
            }
        };
    }

    void input(OfInput input) {
        DatapathId dpId = input.getDpId();
        synchronized (pendingMap) {
            OfBatchSwitchQueue queue = pendingMap.get(dpId);
            if (queue == null) {
                return;
            }

            OfBatch match = queue.receiveResponse(input.getMessage());
            if (queue.isGarbage()) {
                pendingMap.remove(dpId);
            }

            if (match == null) {
                return;
            }

            if (match.isGarbage()) {
                // clean up all affected queues
                for (DatapathId key : match.getAffectedSwitches()) {
                    // current queue is clean due to automatic removal garbage records into .receiveResponse() method
                    if (dpId.equals(key)) {
                        continue;
                    }

                    OfBatchSwitchQueue affectedQueue = pendingMap.get(key);
                    if (affectedQueue == null) {
                        continue;
                    }

                    affectedQueue.cleanup();
                    if (affectedQueue.isGarbage()) {
                        pendingMap.remove(key);
                    }
                }
            }
        }
    }

    HashMap<DatapathId, OfBatchSwitchQueue> getPendingMap() {
        return pendingMap;
    }
}
