/* Copyright 2022 Telstra Open Source
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

import org.openkilda.persistence.repositories.FlowCookieRepository;
import org.openkilda.wfm.share.utils.PoolEntityAdapter;
import org.openkilda.wfm.share.utils.PoolManager;
import org.openkilda.wfm.share.utils.PoolManager.PoolConfig;

import java.util.Optional;

public class FlowCookieEntityAdapter implements PoolEntityAdapter {
    private final FlowCookieRepository flowCookieRepository;

    private final PoolManager.PoolConfig config;

    public FlowCookieEntityAdapter(FlowCookieRepository flowCookieRepository, PoolConfig config) {
        this.flowCookieRepository = flowCookieRepository;
        this.config = config;
    }

    @Override
    public boolean allocateSpecificId(long entityId) {
        return ! flowCookieRepository.exists(entityId);
    }

    @Override
    public Optional<Long> allocateFirstInRange(long idMinimum, long idMaximum) {
        return flowCookieRepository.findFirstUnassignedCookie(idMinimum, idMaximum);
    }

    @Override
    public String formatResourceNotAvailableMessage() {
        return String.format(
                "Unable to find any unassigned flow cookie (effective ID) in range from %d to %d",
                config.getIdMinimum(), config.getIdMaximum());
    }
}
