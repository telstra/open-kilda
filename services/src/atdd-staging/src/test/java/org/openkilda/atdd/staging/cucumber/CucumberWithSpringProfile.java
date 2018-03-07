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

package org.openkilda.atdd.staging.cucumber;

import cucumber.api.junit.Cucumber;
import org.junit.runners.model.InitializationError;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;

/**
 * Extends Cucumber runner to activate Spring profile which is specified via ActiveProfiles annotation.
 */
public class CucumberWithSpringProfile extends Cucumber {

    public CucumberWithSpringProfile(Class clazz) throws InitializationError, IOException {
        super(clazz);

        ActiveProfiles ap = (ActiveProfiles) clazz.getAnnotation(ActiveProfiles.class);
        if (ap != null) {
            System.setProperty("spring.profiles.active", String.join(",", ap.value()));
        }
    }
}
