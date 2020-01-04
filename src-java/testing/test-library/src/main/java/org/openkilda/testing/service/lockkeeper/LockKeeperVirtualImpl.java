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
import org.openkilda.testing.service.lockkeeper.model.InetAddress;
import org.openkilda.testing.service.lockkeeper.model.SwitchModify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Provide functionality of {@link LockKeeperService} for virtual network.
 */
@Slf4j
@Service
@Profile("virtual")
public class LockKeeperVirtualImpl extends LockKeeperServiceImpl {

    public static final String DUMMY_CONTROLLER = "tcp:192.0.2.0:6666";
    @Value("#{'${floodlight.controllers.management}'.split(',')}")
    private List<String> managementControllers;
    @Value("#{'${floodlight.controllers.stat}'.split(',')}")
    private List<String> statControllers;

    @Override
    public void knockoutSwitch(Switch sw) {
        log.debug("Knock out switch: {}", sw.getName());
        setController(sw, DUMMY_CONTROLLER);
    }

    @Override
    public void reviveSwitch(Switch sw) {
        log.debug("Revive switch: {}", sw.getName());
        setController(sw, managementControllers.get(0) + " " + statControllers.get(0));
    }

    @Override
    public void setController(Switch sw, String controller) {
        log.debug("Set '{}' controller on the '{}' switch", controller, sw.getName());
        restTemplate.exchange(labService.getLab().getLabId() + "/lock-keeper/set-controller", HttpMethod.POST,
                new HttpEntity<>(new SwitchModify(sw.getName(), controller), buildJsonHeaders()), String.class);
    }

    @Override
    public void blockFloodlightAccessToPort(Integer port) {
        log.debug("Block floodlight access to {} by adding iptables rules", port);
        restTemplate.exchange(labService.getLab().getLabId() + "/lock-keeper/block-floodlight-access", HttpMethod.POST,
                new HttpEntity<>(new InetAddress(port), buildJsonHeaders()), String.class);
    }

    @Override
    public void unblockFloodlightAccessToPort(Integer port) {
        log.debug("Unblock floodlight access to {} by removing iptables rules", port);
        restTemplate.exchange(labService.getLab().getLabId() + "/lock-keeper/unblock-floodlight-access",
                HttpMethod.POST, new HttpEntity<>(new InetAddress(port), buildJsonHeaders()), String.class);
    }

    @Override
    public void removeFloodlightAccessRestrictions() {
        log.debug("Allow floodlight access to everything by flushing iptables rules(INPUT/OUTPUT chains)");
        restTemplate.exchange(labService.getLab().getLabId() + "/lock-keeper/remove-floodlight-access-restrictions",
                HttpMethod.POST, new HttpEntity(buildJsonHeaders()), String.class);
    }
}
