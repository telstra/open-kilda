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

package org.openkilda.wfm.topology.switchmanager.model.v2;

import org.openkilda.messaging.info.switches.v2.MeterInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.MisconfiguredInfo;

import lombok.Value;

import java.util.List;

@Value
public class ValidateMetersResultV2 {
    boolean asExpected;
    List<MeterInfoEntryV2> missingMeters;
    List<MeterInfoEntryV2> properMeters;
    List<MeterInfoEntryV2> excessMeters;
    List<MisconfiguredInfo<MeterInfoEntryV2>> misconfiguredMeters;
}
