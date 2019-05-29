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

package org.openkilda.wfm.topology.flowhs.service;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.TransitVlanRepository;

public class AbstractFlowCommandFactory {
    private final TransitVlanRepository transitVlanRepository;

    public AbstractFlowCommandFactory(PersistenceManager persistenceManager) {
        this.transitVlanRepository = persistenceManager.getRepositoryFactory().createTransitVlanRepository();
    }

    /**
     * Provides command factory depending on the encapsulation type.
     * @param encapsulationType flow encapsulation type.
     * @return command factory.
     */
    public FlowCommandFactory getFactory(FlowEncapsulationType encapsulationType) {
        switch (encapsulationType) {
            case TRANSIT_VLAN:
                return new TransitVlanCommandFactory(transitVlanRepository);
            default:
                throw new UnsupportedOperationException(
                        String.format("Encapsulation type %s is not supported", encapsulationType));
        }
    }

}
