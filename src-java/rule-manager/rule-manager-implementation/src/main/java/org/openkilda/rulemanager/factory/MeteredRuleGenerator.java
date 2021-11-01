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

package org.openkilda.rulemanager.factory;

import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.action.MeterAction;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@AllArgsConstructor
public abstract class MeteredRuleGenerator implements RuleGenerator {

    protected void addMeterToInstructions(MeterId meterId, Switch sw, Instructions instructions) {
        if (meterId.getValue() != 0L) {
            OfVersion ofVersion = OfVersion.of(sw.getOfVersion());
            if (ofVersion == OfVersion.OF_15) {
                instructions.getApplyActions().add(new MeterAction(meterId));
            } else /* OF_14 and earlier */ {
                instructions.setGoToMeter(meterId);
            }
        }
    }
}
