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

package org.openkilda.wfm.topology.nbworker.services;

import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.switches.SyncRulesResponse;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowPath;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;

import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class ValidationService {
    private RepositoryFactory repositoryFactory;

    public ValidationService(RepositoryFactory repositoryFactory) {
        this.repositoryFactory = repositoryFactory;
    }

    /**
     * Runs validating process.
     *
     * @return validation result as RulesResponse
     */
    public SyncRulesResponse validate(SwitchFlowEntries request) {
        SwitchId switchId = request.getSwitchId();
        log.debug("Validating rules on switch {}", switchId);

        FlowPathRepository flowPathRepository = repositoryFactory.createFlowPathRepository();
        Set<Long> expectedCookies = flowPathRepository.findBySegmentDestSwitchId(switchId).stream()
                .map(FlowPath::getCookie)
                .map(Cookie::getValue)
                .collect(Collectors.toSet());
        flowPathRepository.findBySrcSwitchId(switchId).stream()
                .map(FlowPath::getCookie)
                .map(Cookie::getValue)
                .forEach(expectedCookies::add);
        if (log.isDebugEnabled()) {
            log.debug("Expected rules on switch {}: {}", switchId, cookiesIntoLogRepresentaion(expectedCookies));
        }

        Set<Long> presentCookies = request.getFlowEntries().stream()
                .map(FlowEntry::getCookie)
                .collect(Collectors.toSet());
        if (log.isDebugEnabled()) {
            log.debug("Presented rules on switch {}: {}", switchId, cookiesIntoLogRepresentaion(presentCookies));
        }
        presentCookies.removeIf(Cookie::isDefaultRule);

        return makeRulesResponse(expectedCookies, presentCookies, switchId);
    }

    private SyncRulesResponse makeRulesResponse(Set<Long> expectedCookies,
                                                Set<Long> presentCookies,
                                                SwitchId switchId) {
        Set<Long> missingRules = new HashSet<>(expectedCookies);
        missingRules.removeAll(presentCookies);
        if (!missingRules.isEmpty() && log.isErrorEnabled()) {
            log.error("On switch {} the following rules are missed: {}", switchId,
                    cookiesIntoLogRepresentaion(missingRules));
        }

        Set<Long> properRules = new HashSet<>(expectedCookies);
        properRules.retainAll(presentCookies);

        Set<Long> excessRules = new HashSet<>(presentCookies);
        excessRules.removeAll(expectedCookies);
        if (!excessRules.isEmpty() && log.isWarnEnabled()) {
            log.warn("On switch {} the following rules are excessive: {}", switchId,
                    cookiesIntoLogRepresentaion(excessRules));
        }

        return new SyncRulesResponse(
                ImmutableList.copyOf(missingRules),
                ImmutableList.copyOf(properRules),
                ImmutableList.copyOf(excessRules),
                ImmutableList.copyOf(presentCookies)
        );
    }

    private static String cookiesIntoLogRepresentaion(Collection<Long> rules) {
        return rules.stream().map(Cookie::toString).collect(Collectors.joining(", ", "[", "]"));
    }
}
