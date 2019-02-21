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

package org.openkilda.floodlight.command;

import org.openkilda.floodlight.error.SwitchWriteException;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.floodlight.service.session.SessionService;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFMessage;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@AllArgsConstructor
@Getter
@Slf4j
public class MessageWriter {

    final OFMessage ofMessage;

    /**
     * Sends of ofMessage to the switch.
     * @param sw target switch.
     * @param sessionService session holder.
     * @return response.
     * @throws SwitchWriteException if error occurred.
     */
    public CompletableFuture<Optional<OFMessage>> writeTo(IOFSwitch sw, SessionService sessionService)
            throws SwitchWriteException {
        try (Session session = sessionService.open(sw)) {
            return session.write(ofMessage)
                    .whenComplete((result, error) -> {
                        if (error == null) {
                            log.debug("OF command successfully executed {} on the switch {}", ofMessage, sw.getId());
                        } else {
                            log.error("Failed to execute OF command", error);
                        }
                    });
        }
    }
}
