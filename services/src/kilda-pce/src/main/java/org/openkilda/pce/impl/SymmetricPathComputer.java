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

package org.openkilda.pce.impl;

import org.openkilda.model.Flow;
import org.openkilda.model.Isl;
import org.openkilda.pce.finder.PathFinder;
import org.openkilda.persistence.repositories.IslRepository;

import java.util.Collection;

public class SymmetricPathComputer extends InMemoryPathComputer {

    public SymmetricPathComputer(IslRepository islRepository, PathFinder pathFinder) {
        super(islRepository, pathFinder);
    }

    @Override
    protected Collection<Isl> getAvailableIsls(Flow flow) {
        return flow.isIgnoreBandwidth()
                ? islRepository.findAllActive() : islRepository.findActiveWithAvailableBandwidth(flow.getBandwidth());
    }
}
