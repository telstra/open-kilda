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

package org.openkilda.persistence.repositories;

import org.openkilda.persistence.PersistenceArea;
import org.openkilda.persistence.repositories.history.FlowEventActionRepository;
import org.openkilda.persistence.repositories.history.FlowEventDumpRepository;
import org.openkilda.persistence.repositories.history.FlowEventRepository;
import org.openkilda.persistence.repositories.history.PortEventRepository;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public final class RepositoryAreaBinding {
    public static final RepositoryAreaBinding INSTANCE = new RepositoryAreaBinding();

    private final Map<Class<?>, PersistenceArea> binding = new HashMap<>();

    private RepositoryAreaBinding() {
        binding.put(ApplicationRepository.class, PersistenceArea.COMMON);
        binding.put(BfdSessionRepository.class, PersistenceArea.COMMON);
        binding.put(ExclusionIdRepository.class, PersistenceArea.COMMON);
        binding.put(FlowCookieRepository.class, PersistenceArea.COMMON);
        binding.put(FlowMeterRepository.class, PersistenceArea.COMMON);
        binding.put(FlowMirrorPathRepository.class, PersistenceArea.COMMON);
        binding.put(FlowMirrorPointsRepository.class, PersistenceArea.COMMON);
        binding.put(FlowPathRepository.class, PersistenceArea.COMMON);
        binding.put(FlowRepository.class, PersistenceArea.COMMON);
        binding.put(IslRepository.class, PersistenceArea.COMMON);
        binding.put(KildaConfigurationRepository.class, PersistenceArea.COMMON);
        binding.put(KildaFeatureTogglesRepository.class, PersistenceArea.COMMON);
        binding.put(LagLogicalPortRepository.class, PersistenceArea.COMMON);
        binding.put(LinkPropsRepository.class, PersistenceArea.COMMON);
        binding.put(MirrorGroupRepository.class, PersistenceArea.COMMON);
        binding.put(PathSegmentRepository.class, PersistenceArea.COMMON);
        binding.put(PhysicalPortRepository.class, PersistenceArea.COMMON);
        binding.put(PortPropertiesRepository.class, PersistenceArea.COMMON);
        binding.put(SpeakerRepository.class, PersistenceArea.COMMON);
        binding.put(SwitchConnectedDeviceRepository.class, PersistenceArea.COMMON);
        binding.put(SwitchConnectRepository.class, PersistenceArea.COMMON);
        binding.put(SwitchPropertiesRepository.class, PersistenceArea.COMMON);
        binding.put(SwitchRepository.class, PersistenceArea.COMMON);
        binding.put(TransitVlanRepository.class, PersistenceArea.COMMON);
        binding.put(VxlanRepository.class, PersistenceArea.COMMON);
        binding.put(FlowStatsRepository.class, PersistenceArea.COMMON);
        binding.put(YFlowRepository.class, PersistenceArea.COMMON);

        // history
        binding.put(FlowEventActionRepository.class, PersistenceArea.HISTORY);
        binding.put(FlowEventDumpRepository.class, PersistenceArea.HISTORY);
        binding.put(FlowEventRepository.class, PersistenceArea.HISTORY);
        binding.put(PortEventRepository.class, PersistenceArea.HISTORY);
    }

    public PersistenceArea lookup(Repository<?> repository) {
        return lookup(repository.getClass());
    }

    /**
     * Map repository class into {@link PersistenceArea}.
     */
    public PersistenceArea lookup(Class<?> klass) {
        LinkedList<Class<?>> queue = new LinkedList<>();
        queue.addFirst(klass);

        PersistenceArea result = null;
        while (! queue.isEmpty()) {
            Class<?> entry = queue.pollFirst();
            if (entry == null) {
                continue;
            }
            if (! Repository.class.isAssignableFrom(entry)) {
                continue;
            }

            result = binding.get(entry);
            if (result != null) {
                break;
            }

            queue.addAll(Arrays.asList(entry.getInterfaces()));
            queue.addLast(entry.getSuperclass());
        }
        if (result == null) {
            throw new IllegalArgumentException(String.format(
                    "Unable to lookup persistence area for class %s", klass.getName()));
        }
        return result;
    }
}
