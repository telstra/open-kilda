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

package org.openkilda.wfm.share.flow.resources;

import org.openkilda.model.Flow;
import org.openkilda.model.PathId;

import java.util.Optional;

public interface EncapsulationResourcesProvider<T extends EncapsulationResources> {
    /**
     * Allocates flow encapsulation resources for the flow path.
     *
     * @return allocated resources.
     */
    T allocate(Flow flow, PathId pathId) throws ResourceNotAvailableException;

    /**
     * Deallocates flow encapsulation resources of the path.
     */
    void deallocate(PathId pathId);

    /**
     * Get allocated encapsulation resources of the flow path.
     */
    Optional<T> get(PathId pathId);
}
