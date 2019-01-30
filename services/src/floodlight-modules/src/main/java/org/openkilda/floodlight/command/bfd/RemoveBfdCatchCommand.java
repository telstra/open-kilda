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

package org.openkilda.floodlight.command.bfd;

import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.error.SwitchWriteException;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.messaging.model.NoviBfdCatch;

import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFMessage;

public class RemoveBfdCatchCommand extends BfdCatchCommand {
    public RemoveBfdCatchCommand(CommandContext context, NoviBfdCatch bfdCatch) {
        super(context, bfdCatch);
    }

    @Override
    protected void handle(Session session) throws SwitchWriteException {
        log.info("Remove BFD catch rule - {}", getBfdCatch());

        scheduleResultHandling(session.write(makeCatchRuleDelMessage(session.getSw())));
    }

    private OFMessage makeCatchRuleDelMessage(IOFSwitch sw) {
        return applyCommonFlowModSettings(sw, sw.getOFFactory().buildFlowDeleteStrict()).build();
    }
}
