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

package org.openkilda.wfm.share.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode
public class SpeakerRequestBuildContext {
    private boolean removeCustomerPortSharedCatchRule;
    private boolean removeOppositeCustomerPortSharedCatchRule;

    /**
     * Swap main and opposite data..
     */
    public void swapMainAndOppositeData() {
        boolean tmp = removeCustomerPortSharedCatchRule;
        removeCustomerPortSharedCatchRule = removeOppositeCustomerPortSharedCatchRule;
        removeOppositeCustomerPortSharedCatchRule = tmp;
    }
}
