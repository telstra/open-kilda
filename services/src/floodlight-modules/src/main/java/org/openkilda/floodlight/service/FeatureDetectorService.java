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

package org.openkilda.floodlight.service;

import org.openkilda.floodlight.feature.AbstractFeature;
import org.openkilda.floodlight.feature.BfdFeature;
import org.openkilda.floodlight.feature.BfdReviewFeature;
import org.openkilda.floodlight.feature.MeterFeature;
import org.openkilda.messaging.model.SpeakerSwitchView.Feature;

import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class FeatureDetectorService implements IService {
    private final List<AbstractFeature> features;

    public FeatureDetectorService() {
        features = ImmutableList.of(
                new MeterFeature(),
                new BfdFeature(),
                new BfdReviewFeature());
    }

    /**
     * Detect features supported by switch.
     *
     * @param sw target switch
     * @return supported features
     */
    public Set<Feature> detectSwitch(IOFSwitch sw) {
        return features.stream()
                .map(x -> x.discover(sw))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }

    @Override
    public void setup(FloodlightModuleContext moduleContext) throws FloodlightModuleException {
        // there is nothing to initialize here
    }
}
