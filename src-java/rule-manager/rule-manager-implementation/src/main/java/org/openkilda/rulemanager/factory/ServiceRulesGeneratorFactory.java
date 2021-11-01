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

package org.openkilda.rulemanager.factory;

import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.factory.generator.service.BroadCastDiscoveryRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.TableDefaultRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.UniCastDiscoveryRuleGenerator;

public class ServiceRulesGeneratorFactory {

    private final RuleManagerConfig config;

    public ServiceRulesGeneratorFactory(RuleManagerConfig config) {
        this.config = config;
    }

    /**
     * Get default drop rule generator.
     */
    public RuleGenerator getTableDefaultRuleGenerator(Cookie cookie, OfTable table) {
        return TableDefaultRuleGenerator.builder()
                .cookie(cookie)
                .ofTable(table)
                .build();
    }

    /**
     * Get unicast discovery rule generator.
     */
    public RuleGenerator getUniCastDiscoveryRuleGenerator() {
        return UniCastDiscoveryRuleGenerator.builder()
                .config(config)
                .build();
    }

    /**
     * Get broadcast discovery rule generator.
     */
    public RuleGenerator getBroadCastDiscoveryRuleGenerator() {
        return BroadCastDiscoveryRuleGenerator.builder()
                .config(config)
                .build();
    }


}
