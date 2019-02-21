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
import org.openkilda.floodlight.error.NoFeatureException;
import org.openkilda.floodlight.error.SwitchWriteException;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.messaging.model.NoviBfdSession;

import net.floodlightcontroller.core.IOFSwitch;

public class SetupBfdSessionCommand extends BfdSessionCommand {
    private static final int CONSTRAINT_INTERVAL_MIN = 1;

    public SetupBfdSessionCommand(CommandContext context, NoviBfdSession bfdSession) {
        super(context, bfdSession);
    }

    @Override
    protected void validate(IOFSwitch sw) throws NoFeatureException {
        super.validate(sw);

        NoviBfdSession bfdSession = getBfdSession();
        if (bfdSession.getIntervalMs() < CONSTRAINT_INTERVAL_MIN) {
            throw new IllegalArgumentException(String.format(
                    "Invalid bfd session interval value: %d < %d",
                    bfdSession.getIntervalMs(), CONSTRAINT_INTERVAL_MIN));
        }
    }

    @Override
    public void handle(Session session) throws SwitchWriteException {
        log.info("Setup BFD session - {}", getBfdSession());

        scheduleResultHandling(session.write(makeSessionConfigMessage(session.getSw())));
    }
}
