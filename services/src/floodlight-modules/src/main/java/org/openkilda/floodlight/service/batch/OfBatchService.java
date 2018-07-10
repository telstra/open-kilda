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
import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.model.OfBatchResult;
import org.openkilda.floodlight.model.OfRequestResponse;
import org.openkilda.floodlight.service.AbstractOfHandler;
import org.openkilda.floodlight.utils.CommandContextFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Future;

public class OfBatchService extends AbstractOfHandler implements IFloodlightService {
    private static final Logger log = LoggerFactory.getLogger(OfBatchService.class);

    private final HashMap<DatapathId, OfBatchSwitchQueue> pendingMap = new HashMap<>();

    private SwitchUtils switchUtils;

    public OfBatchService(CommandContextFactory commandContextFactory) {
        super(commandContextFactory);
    }

    public void init(FloodlightModuleContext moduleContext) {
        switchUtils = new SwitchUtils(moduleContext.getServiceImpl(IOFSwitchService.class));
        activateSubscription(moduleContext, OFType.ERROR, OFType.BARRIER_REPLY);
    }

    /**
     * Write prepared OFMessages to switches.
     */
    public Future<OfBatchResult> write(List<OfRequestResponse> payload) {
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
    public boolean handle(CommandContext commandContext, IOFSwitch sw, OFMessage message, FloodlightContext context) {
        DatapathId dpId = sw.getId();
        synchronized (pendingMap) {
            OfBatchSwitchQueue queue = pendingMap.get(dpId);
            if (queue == null) {
                return false;
            }

            OfBatch match = queue.receiveResponse(message);
            if (queue.isGarbage()) {
                pendingMap.remove(dpId);
            }

            if (match == null) {
                return false;
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

        return true;
    }

    HashMap<DatapathId, OfBatchSwitchQueue> getPendingMap() {
        return pendingMap;
    }
}
