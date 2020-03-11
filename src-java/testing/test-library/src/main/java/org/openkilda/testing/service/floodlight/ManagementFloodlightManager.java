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

package org.openkilda.testing.service.floodlight;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("mgmtFloodlight")
public class ManagementFloodlightManager implements MultiFloodlightManager {

    @Value("#{'${floodlight.regions}'.split(',')}")
    private List<String> regions;
    @Value("#{'${floodlight.controllers.management.containers}'.split(',')}")
    private List<String> mgmtContainers;
    @Value("#{'${floodlight.controllers.management.openflow}'.split(',')}")
    private List<String> managementControllers;
    @Autowired
    @Qualifier("managementFloodlights")
    private List<FloodlightService> floodlightServices;

    @Override
    public FloodlightService getFloodlightService(String region) {
        return floodlightServices.get(getRegionIndex(region));
    }

    @Override
    public String getContainerName(String region) {
        return mgmtContainers.get(getRegionIndex(region));
    }

    @Override
    public List<String> getRegions() {
        return regions;
    }

    @Override
    public List<String> getContainerNames() {
        return mgmtContainers;
    }

    @Override
    public List<String> getControllerAddresses() {
        return managementControllers;
    }

    private int getRegionIndex(String region) {
        int regionIndex = regions.indexOf(region);
        if (regionIndex == -1) {
            throw new RuntimeException(String.format("Specified region '%s' was not found in properties file", region));
        }
        return regionIndex;
    }
}
