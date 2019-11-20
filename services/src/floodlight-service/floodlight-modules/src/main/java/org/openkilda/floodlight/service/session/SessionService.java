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

package org.openkilda.floodlight.service.session;

import org.openkilda.floodlight.command.Command;
import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.model.OfInput;
import org.openkilda.floodlight.service.IService;
import org.openkilda.floodlight.service.of.IInputTranslator;
import org.openkilda.floodlight.service.of.InputService;
import org.openkilda.messaging.MessageContext;

import com.google.common.annotations.VisibleForTesting;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionService implements IService, IInputTranslator {
    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final Map<DatapathId, SwitchSessions> sessionsByDatapath = new ConcurrentHashMap<>();

    public Session open(IOFSwitch sw) {
        return open(new MessageContext(), sw);
    }

    /**
     * Create new OF communication session and register it in service.
     */
    public Session open(MessageContext context, IOFSwitch sw) {
        SwitchSessions group = sessionsByDatapath.get(sw.getId());
        if (group == null) {
            throw new IllegalStateException(String.format(
                    "Switch %s is not registered into %s", sw.getId(), getClass().getName()));
        }

        return group.open(sw, context);
    }

    @Override
    public void setup(FloodlightModuleContext moduleContext) {
        InputService inputService = moduleContext.getServiceImpl(InputService.class);
        inputService.addTranslator(OFType.ERROR, this);
        inputService.addTranslator(OFType.BARRIER_REPLY, this);

        new SwitchEventsTranslator(this, moduleContext.getServiceImpl(IOFSwitchService.class));
    }

    @Override
    public Command makeCommand(CommandContext context, OfInput input) {
        return new Command(context) {
            @Override
            public Command call() throws Exception {
                handleResponse(input.getDpId(), input.getMessage());
                return null;
            }
        };
    }

    @VisibleForTesting
    void handleResponse(DatapathId dpId, OFMessage message) {
        SwitchSessions group;
        group = sessionsByDatapath.get(dpId);

        if (group == null) {
            log.error("Switch {} is not registered", dpId);
            return;
        }

        group.handleResponse(message);
    }

    /**
     * Register switch as available for OF sessions.
     *
     * <p>Called from main FL thread, that why we can avoid `synchronization` here.
     */
    void switchActivate(DatapathId dpId) {
        SwitchSessions group = new SwitchSessions();
        SwitchSessions previous;
        previous = sessionsByDatapath.put(dpId, group);

        if (previous != null) {
            log.error("Switch {} already registered (connect/disconnect race condition?)", dpId);
            previous.disconnect(); // To clean all possibly pending sessions.
        }
    }

    /**
     * Unregister switch, close all existing sessions.
     *
     * <p>Called from main FL thread, that why we can avoid `synchronization` here.
     */
    void switchDeactivate(DatapathId dpId) {
        SwitchSessions group;
        group = sessionsByDatapath.remove(dpId);

        if (group == null) {
            log.error("Switch {} is not registered (double removal?)", dpId);
            return;
        }

        group.disconnect();
    }
}
