/* Copyright 2020 Telstra Open Source
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

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

import org.openkilda.testing.service.floodlight.model.Floodlight;
import org.openkilda.testing.service.floodlight.model.FloodlightConnectMode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Scope(SCOPE_PROTOTYPE)
public class FloodlightsHelper {
    @Autowired
    List<Floodlight> floodlights;

    public Floodlight getFlByRegion(String region) {
        return floodlights.stream().filter(f -> f.getRegion().equalsIgnoreCase(region)).findFirst().orElseThrow(() ->
                new IllegalArgumentException(format("Unable to find floodlight for region %s", region)));
    }

    public List<Floodlight> getFlsByRegions(List<String> regions) {
        return floodlights.stream().filter(f -> regions.contains(f.getRegion())).collect(toList());
    }

    /**
     * Get Floodlight by container name.
     */
    public Floodlight getFlByContainer(String container) {
        return floodlights.stream().filter(f -> f.getContainer().equalsIgnoreCase(container)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(format("Unable to find floodlight with container %s",
                        container)));
    }

    public List<Floodlight> getFlsByMode(FloodlightConnectMode mode) {
        return floodlights.stream().filter(f ->
                f.getMode().equals(mode)).collect(toList());
    }

    public List<String> filterRegionsByMode(List<String> regions, FloodlightConnectMode mode) {
        return floodlights.stream().filter(f -> regions.contains(f.getRegion()) && f.getMode().equals(mode))
                .map(Floodlight::getRegion).collect(toList());
    }

    public List<Floodlight> getFls() {
        return floodlights;
    }
}
