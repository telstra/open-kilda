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

package org.openkilda.wfm.topology.network.model.facts;

import org.openkilda.wfm.topology.network.model.BiIslDataHolder;
import org.openkilda.wfm.topology.network.model.IslDataHolder;
import org.openkilda.wfm.topology.network.model.IslReference;

public class DiscoveryFacts extends BiIslDataHolder<IslDataHolder> {
    public DiscoveryFacts(IslReference reference) {
        super(reference);
    }

    /**
     * Aggregate and return ISL data for both direction.
     */
    public IslDataHolder makeAggregatedData() {
        IslDataHolder aggData = null;
        for (IslDataHolder link : islData) {
            if (link == null) {
                continue;
            }
            if (aggData == null) {
                aggData = link;
            } else {
                aggData = aggData.merge(link);
            }
        }

        return aggData;
    }
}
