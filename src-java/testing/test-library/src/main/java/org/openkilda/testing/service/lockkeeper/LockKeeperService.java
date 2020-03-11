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
import org.openkilda.testing.service.floodlight.MultiFloodlightManager;
import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow;
import org.openkilda.testing.service.lockkeeper.model.BlockRequest;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This service is meant to give control over some software or hardware parts of the system that are out of Kilda's
 * direct control. E.g. switches that are not connected to controller or lifecycle of system components.
 */
public interface LockKeeperService {
    void addFlows(List<ASwitchFlow> flows);

    void removeFlows(List<ASwitchFlow> flows);

    List<ASwitchFlow> getAllFlows();

    void portsUp(List<Integer> ports);

    void portsDown(List<Integer> ports);

    void stopFloodlight(String region);

    void startFloodlight(String region);

    void restartFloodlight(String region);

    BlockRequest knockoutSwitch(Switch sw, MultiFloodlightManager factory);

    void reviveSwitch(Switch sw, BlockRequest blockRequest);

    void setController(Switch sw, String controller);

    void blockFloodlightAccess(String region, BlockRequest address);

    void unblockFloodlightAccess(String region, BlockRequest address);

    void removeFloodlightAccessRestrictions(String region);

    void knockoutFloodlight(String region);

    void reviveFloodlight(String region);

    /**
     * Extract switch address and port from the 'inetAddress' string of Floodlight 'get switches' response.
     */
    static Pair<String, Integer> parseAddressPort(String flFormatInetAddress) {
        Matcher matcher = Pattern.compile(".*?/(.*?):(\\d+)").matcher(flFormatInetAddress);
        if (matcher.matches()) {
            return ImmutablePair.of(matcher.group(1), Integer.parseInt(matcher.group(2)));
        } else {
            throw new RuntimeException(
                    String.format("Unable to parse inetaddress returned flow Floodlight: %s", flFormatInetAddress));
        }
    }
}
