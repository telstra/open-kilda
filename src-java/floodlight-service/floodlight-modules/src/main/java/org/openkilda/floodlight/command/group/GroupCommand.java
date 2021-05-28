/* Copyright 2021 Telstra Open Source
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

package org.openkilda.floodlight.command.group;

import org.openkilda.floodlight.command.SpeakerCommand;
import org.openkilda.floodlight.command.SpeakerCommandReport;
import org.openkilda.floodlight.config.provider.FloodlightModuleConfigurationProvider;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.floodlight.switchmanager.SwitchManagerConfig;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;

import lombok.AccessLevel;
import lombok.Getter;
import net.floodlightcontroller.core.module.FloodlightModuleContext;

import java.util.Set;

abstract class GroupCommand<T extends SpeakerCommandReport> extends SpeakerCommand<T> {
    // operation data
    @Getter(AccessLevel.PROTECTED)
    private SwitchManagerConfig switchManagerConfig;
    @Getter(AccessLevel.PROTECTED)
    private Set<SwitchFeature> switchFeatures;

    GroupCommand(MessageContext messageContext, SwitchId switchId) {
        super(messageContext, switchId);
    }

    @Override
    protected void setup(FloodlightModuleContext moduleContext) throws Exception {
        super.setup(moduleContext);

        FloodlightModuleConfigurationProvider provider =
                FloodlightModuleConfigurationProvider.of(moduleContext, SwitchManager.class);
        switchManagerConfig = provider.getConfiguration(SwitchManagerConfig.class);

        FeatureDetectorService featuresDetector = moduleContext.getServiceImpl(FeatureDetectorService.class);
        switchFeatures = featuresDetector.detectSwitch(getSw());
    }

    void ensureSwitchSupportGroups() throws UnsupportedSwitchOperationException {
        if (!switchFeatures.contains(SwitchFeature.GROUPS)) {
            throw new UnsupportedSwitchOperationException(getSw().getId(), "Switch doesn't support groups");
        }
    }
}
