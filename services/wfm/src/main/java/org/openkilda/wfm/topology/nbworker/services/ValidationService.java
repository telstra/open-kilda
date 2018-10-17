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
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowSegment;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;

import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ValidationService {
    private static final Logger logger = LoggerFactory.getLogger(ValidationService.class);
    public static final Set<Long> SERVICE_RULES =
            ImmutableSet.of(0x8000000000000001L, 0x8000000000000002L, 0x8000000000000003L);
    private SwitchFlowEntries request;
    private RepositoryFactory repositoryFactory;
    private SwitchId switchId;

    public ValidationService(SwitchFlowEntries request, RepositoryFactory repositoryFactory) {
        this.request = request;
        this.repositoryFactory = repositoryFactory;
        switchId = request.getSwitchId();
    }

    /**
     * Runs validating process.
     * @return validation result as RulesResponse
     */
    public SyncRulesResponse validate() {
        logger.debug("Validating rules on switch {}", switchId);

        SwitchRepository switchRepository = repositoryFactory.createSwitchRepository();
        org.openkilda.model.SwitchId modelSwitchId = new org.openkilda.model.SwitchId(switchId.toLong());

        Set<Long> expectedCookies =
                StreamSupport.stream(switchRepository.findFlowSegmentsToSwitch(modelSwitchId).spliterator(), false)
                    .map(FlowSegment::getCookieId)
                    .collect(Collectors.toSet());
        expectedCookies.addAll(
                StreamSupport.stream(switchRepository.findFlowsFromSwitch(modelSwitchId).spliterator(), false)
                    .map(Flow::getCookie)
                    .collect(Collectors.toSet()));
        if (logger.isDebugEnabled()) {
            logger.debug("Expected rules on switch {}: {}", switchId, cookiesIntoLogRepresentaion(expectedCookies));
        }

        Set<Long> presentedCookies = request.getFlowEntries().stream()
                .map(FlowEntry::getCookie)
                .collect(Collectors.toSet());
        presentedCookies.removeAll(SERVICE_RULES);
        if (logger.isDebugEnabled()) {
            logger.debug("Presented rules on switch {}: {}", switchId, cookiesIntoLogRepresentaion(presentedCookies));
        }

        return makeRulesResponse(expectedCookies, presentedCookies, switchId);
    }

    private SyncRulesResponse makeRulesResponse(Set<Long> expectedCookies,
                                                Set<Long> presentedCookies,
                                                SwitchId switchId) {
        Set<Long> missingRules = new HashSet<>(expectedCookies);
        missingRules.removeAll(presentedCookies);
        if (!missingRules.isEmpty() && logger.isErrorEnabled()) {
            logger.error("On switch {} the following rules are missed: {}", switchId,
                    cookiesIntoLogRepresentaion(missingRules));
        }

        Set<Long> properRules = new HashSet<>(expectedCookies);
        properRules.retainAll(presentedCookies);

        Set<Long> excessRules = new HashSet<>(presentedCookies);
        excessRules.removeAll(expectedCookies);
        if (!excessRules.isEmpty() && logger.isWarnEnabled()) {
            logger.warn("On switch {} the following rules are excessive: {}", switchId,
                    cookiesIntoLogRepresentaion(excessRules));
        }

        return new SyncRulesResponse(
                new ArrayList<>(missingRules),
                new ArrayList<>(properRules),
                new ArrayList<>(excessRules),
                new ArrayList<>(presentedCookies)
        );
    }

    private static String cookiesIntoLogRepresentaion(Collection<Long> rules) {
        return rules.stream().map(rule -> String.format("0x%016X", rule)).collect(Collectors.joining(", ", "[", "]"));
    }
}
