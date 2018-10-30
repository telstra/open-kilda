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

package org.openkilda.pce.provider;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;

@ToString
@Slf4j
public class AuthNeo4j implements Auth {
    private final String host;
    private final String login;
    private final String password;

    public AuthNeo4j(String host, String login, String password) {
        this.host = host;
        this.login = login;
        this.password = password;
    }

    @Override
    public Driver getDriver() {
        String address = String.format("bolt://%s", host);

        log.info("NEO4J connect {} (login=\"{}\", password=\"*****\")", address, login);
        return GraphDatabase.driver(address, AuthTokens.basic(login, password));
    }
}
