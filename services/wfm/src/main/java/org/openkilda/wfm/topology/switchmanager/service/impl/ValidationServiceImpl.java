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

package org.openkilda.wfm.topology.switchmanager.service.impl;

import org.openkilda.model.Cookie;
import org.openkilda.model.FlowPath;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.service.ValidationService;

import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class ValidationServiceImpl implements ValidationService {
    private FlowPathRepository flowPathRepository;

    public ValidationServiceImpl(PersistenceManager persistenceManager) {
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
    }

    @Override
    public ValidateRulesResult validate(SwitchId switchId, Set<Long> presentCookies) {
        log.debug("Validating rules on switch {}", switchId);

        Set<Long> expectedCookies = flowPathRepository.findBySegmentDestSwitchId(switchId).stream()
                .map(FlowPath::getCookie)
                .map(Cookie::getValue)
                .collect(Collectors.toSet());

        flowPathRepository.findBySrcSwitchId(switchId).stream()
                .map(FlowPath::getCookie)
                .map(Cookie::getValue)
                .forEach(expectedCookies::add);

        presentCookies.removeIf(Cookie::isDefaultRule);

        return makeRulesResponse(expectedCookies, presentCookies, switchId);
    }

    private ValidateRulesResult makeRulesResponse(Set<Long> expectedCookies,
                                                  Set<Long> presentCookies,
                                                  SwitchId switchId) {
        Set<Long> missingRules = new HashSet<>(expectedCookies);
        missingRules.removeAll(presentCookies);
        if (!missingRules.isEmpty() && log.isErrorEnabled()) {
            log.error("On switch {} the following rules are missed: {}", switchId,
                    cookiesIntoLogRepresentation(missingRules));
        }

        Set<Long> properRules = new HashSet<>(expectedCookies);
        properRules.retainAll(presentCookies);

        Set<Long> excessRules = new HashSet<>(presentCookies);
        excessRules.removeAll(expectedCookies);
        if (!excessRules.isEmpty() && log.isWarnEnabled()) {
            log.warn("On switch {} the following rules are excessive: {}", switchId,
                    cookiesIntoLogRepresentation(excessRules));
        }

        return new ValidateRulesResult(
                ImmutableList.copyOf(missingRules),
                ImmutableList.copyOf(properRules),
                ImmutableList.copyOf(excessRules)
        );
    }

    private static String cookiesIntoLogRepresentation(Collection<Long> rules) {
        return rules.stream().map(Cookie::toString).collect(Collectors.joining(", ", "[", "]"));
    }
}
