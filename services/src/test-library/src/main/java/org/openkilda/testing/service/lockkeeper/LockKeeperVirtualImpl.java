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

package org.openkilda.testing.service.lockkeeper;

import org.openkilda.testing.model.topology.TopologyDefinition.Switch;
import org.openkilda.testing.service.lockkeeper.model.SwitchModify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

/**
 * Provide functionality of {@link LockKeeperService} for virtual network.
 */
@Slf4j
@Service
@Profile("virtual")
public class LockKeeperVirtualImpl extends LockKeeperServiceImpl implements LockKeeperService {

    @Value("${floodlight.controller.uri}")
    private String controllerHost;

    @Override
    public void knockoutSwitch(Switch switchDef) {
        restTemplate.exchange("/knockoutswitch", HttpMethod.POST,
                new HttpEntity<>(new SwitchModify(switchDef.getName(), null), buildJsonHeaders()), String.class);
        log.debug("Knocking out switch: {}", switchDef.getName());
    }

    @Override
    public void reviveSwitch(Switch switchDef) {
        restTemplate.exchange("/reviveswitch", HttpMethod.POST,
                new HttpEntity<>(new SwitchModify(switchDef.getName(), controllerHost),
                        buildJsonHeaders()), String.class);
        log.debug("Revive switch: {}", switchDef.getName());
    }
}
