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

import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class BatchWriter implements SessionProxy {
    private final List<OFMessage> batch = new ArrayList<>();

    public BatchWriter(OFMessage... batch) {
        this.batch.addAll(Arrays.asList(batch));
    }

    public BatchWriter(Collection<OFMessage> batch) {
        this.batch.addAll(batch);
    }

    @Override
    public CompletableFuture<Optional<OFMessage>> writeTo(IOFSwitch sw, SessionService sessionService)
            throws SwitchWriteException {
        List<CompletableFuture<Optional<OFMessage>>> requests = new ArrayList<>(batch.size());
        try (Session session = sessionService.open(sw)) {
            for (OFMessage payload : batch) {
                requests.add(session.write(payload));
            }
        }

        final CompletableFuture<Optional<OFMessage>> lastRequest = requests.isEmpty()
                ? CompletableFuture.completedFuture(Optional.empty())
                : requests.get(requests.size() - 1);
        return CompletableFuture.allOf(requests.toArray(new CompletableFuture[0]))
                .thenCompose(unneeded -> lastRequest);
    }
}
