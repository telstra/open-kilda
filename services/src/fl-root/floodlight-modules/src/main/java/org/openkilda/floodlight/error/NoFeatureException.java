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

package org.openkilda.floodlight.error;

import org.openkilda.messaging.model.Switch;

import org.projectfloodlight.openflow.types.DatapathId;

import java.util.Set;
import java.util.stream.Collectors;

public class NoFeatureException extends AbstractException {
    public NoFeatureException(DatapathId dpId, Switch.Feature missingFeature, Set<Switch.Feature> switchFeatures) {
        super(formatMessage(dpId, missingFeature, switchFeatures));
    }

    private static String formatMessage(
            DatapathId dpId, Switch.Feature missingFeature, Set<Switch.Feature> switchFeatures) {
        return String.format("Switch %s does not support feature %s (supported features: %s)",
                             dpId, missingFeature,
                             switchFeatures.stream()
                                     .map(Switch.Feature::toString)
                                     .collect(Collectors.joining(", ")));
    }
}
