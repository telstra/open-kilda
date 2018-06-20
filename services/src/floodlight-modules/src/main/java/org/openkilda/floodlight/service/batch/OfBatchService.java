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
import org.openkilda.floodlight.service.AbstractOfHandler;
import org.openkilda.floodlight.switchmanager.OFInstallException;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public class OfBatchService extends AbstractOfHandler implements IFloodlightService {
    private static final Logger log = LoggerFactory.getLogger(OfBatchService.class);

    private final LinkedList<Task> operations = new LinkedList<>();

    private SwitchUtils switchUtils;

    /**
     * Write prepared OFMessages to switches.
     */
    public synchronized void push(org.openkilda.floodlight.command.Command initiator, List<OfPendingMessage> payload)
            throws OFInstallException {
        log.debug("Got io request with {} message(s) from {}", payload.size(), initiator);

        BatchRecord batch = new BatchRecord(switchUtils, payload);
        operations.addLast(new Task(initiator, batch));

        try {
            batch.write();
        } catch (OFInstallException e) {
            operations.removeLast();
            throw e;
        }
    }

    public void init(FloodlightModuleContext moduleContext) {
        switchUtils = new SwitchUtils(moduleContext.getServiceImpl(IOFSwitchService.class));
        activateSubscription(moduleContext, OFType.ERROR, OFType.BARRIER_REPLY);
    }

    @Override
    public boolean handle(IOFSwitch sw, OFMessage message, FloodlightContext context) {
        boolean isHandled = false;

        Task completed = null;
        synchronized (this) {
            for (ListIterator<Task> iterator = operations.listIterator(); iterator.hasNext(); ) {
                Task task = iterator.next();

                if (!task.batch.handleResponse(message)) {
                    continue;
                }

                log.debug("Message (xId:{}) have matched one of pending io batches", message.getXid());
                isHandled = true;
                if (task.batch.isComplete()) {
                    iterator.remove();
                    completed = task;
                }
                break;
            }
        }

        if (completed != null) {
            log.debug("Send complete signal to pending command: {}", completed.command);
            completed.command.ioComplete(completed.batch.getBatch(), completed.batch.isErrors());
        }

        return isHandled;
    }

    private class Task {
        final org.openkilda.floodlight.command.Command command;
        final BatchRecord batch;

        Task(org.openkilda.floodlight.command.Command command, BatchRecord batch) {
            this.command = command;
            this.batch = batch;
        }
    }
}
