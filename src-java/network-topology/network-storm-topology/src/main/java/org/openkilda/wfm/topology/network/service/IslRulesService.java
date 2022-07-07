/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.network.service;

import static java.lang.String.format;

import org.openkilda.floodlight.api.request.rulemanager.FlowCommand;
import org.openkilda.floodlight.api.request.rulemanager.GroupCommand;
import org.openkilda.floodlight.api.request.rulemanager.MeterCommand;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.DataAdapter;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.model.IslReference;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public class IslRulesService {
    private final IIslCarrier carrier;
    private final PersistenceManager persistenceManager;
    private final RuleManager ruleManager;

    private final Map<UUID, Request> requests = new HashMap<>();

    public IslRulesService(IIslCarrier carrier, PersistenceManager persistenceManager, RuleManager ruleManager) {
        this.carrier = carrier;
        this.persistenceManager = persistenceManager;
        this.ruleManager = ruleManager;
    }

    /**
     * Build and send required ISL service rules install commands.
     */
    public void installIslRules(IslReference reference, Endpoint endpoint) {
        List<OfCommand> commands = buildIslRules(endpoint.getDatapath(), endpoint.getPortNumber());
        UUID commandId = UUID.randomUUID();
        requests.put(commandId, new Request(reference, endpoint,
                (carrier) -> carrier.islRulesInstalled(reference, endpoint)));
        carrier.sendIslRulesInstallCommand(endpoint.getDatapath(), commandId, commands);
    }

    /**
     * Build and send required ISL service rules delete commands.
     */
    public void deleteIslRules(IslReference reference, Endpoint endpoint) {
        List<OfCommand> commands = buildIslRules(endpoint.getDatapath(), endpoint.getPortNumber());
        UUID commandId = UUID.randomUUID();
        requests.put(commandId, new Request(reference, endpoint,
                (carrier) -> carrier.islRulesDeleted(reference, endpoint)));
        carrier.sendIslRulesDeleteCommand(endpoint.getDatapath(), commandId, commands);
    }

    /**
     * Process ISL service rules installed/deleted response.
     */
    public void handleResponse(SpeakerCommandResponse response) {
        Request request = requests.remove(response.getCommandId());
        if (request != null) {
            if (response.isSuccess()) {
                request.successAction.accept(carrier);
            } else {
                carrier.islRulesFailed(request.getReference(), request.getEndpoint());
            }
        } else {
            log.warn("Skipping unknown speaker command response: {}", response);
        }
    }

    /**
     * Process ISL service rules command timeout.
     */
    public void handleTimeout(UUID commandId) {
        Request request = requests.remove(commandId);
        if (request != null) {
            carrier.islRulesFailed(request.getReference(), request.getEndpoint());
        } else {
            log.warn("Skipping timeout for unknown command {}", commandId);
        }
    }

    private List<OfCommand> buildIslRules(SwitchId switchId, int port) {
        DataAdapter adapter = PersistenceDataAdapter.builder()
                .persistenceManager(persistenceManager)
                .pathIds(Collections.emptySet())
                .switchIds(Collections.singleton(switchId))
                .build();
        return ruleManager.buildIslServiceRules(switchId, port, adapter).stream()
                .map(this::buildOfCommand)
                .collect(Collectors.toList());
    }

    private OfCommand buildOfCommand(SpeakerData data) {
        if (data instanceof FlowSpeakerData) {
            return new FlowCommand((FlowSpeakerData) data);
        } else if (data instanceof MeterSpeakerData) {
            return new MeterCommand((MeterSpeakerData) data);
        } else if (data instanceof GroupSpeakerData) {
            return new GroupCommand((GroupSpeakerData) data);
        }
        throw new IllegalStateException(format("Unknown speaker data %s", data));
    }

    @Value
    private static class Request {
        IslReference reference;
        Endpoint endpoint;
        Consumer<IIslCarrier> successAction;
    }
}
