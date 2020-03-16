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

package org.openkilda.service;

import static java.util.Base64.getEncoder;

import org.openkilda.utility.ApplicationProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ApplicationService {

    @Autowired
    private ApplicationProperties applicationProperties;

    /**
     * Gets the auth header.
     *
     * @return the auth header
     */
    public String getAuthHeader() {
        String auth = applicationProperties.getKildaUsername() + ":" + applicationProperties.getKildaPassword();

        return "Basic " + getEncoder().encodeToString(auth.getBytes());
    }
}
