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

package org.openkilda.atdd.staging.service.neo4j;

import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;

public class Neo4jServiceImpl implements DisposableBean, Neo4jService {

    @Value("${neo.uri}")
    private String neoUri;

    private Driver neo;

    @Override
    public Driver getDriver() {
        if (neo == null) {
            neo = GraphDatabase.driver(neoUri);
        }
        return neo;
    }

    @Override
    public void destroy() {
        if (neo != null) {
            neo.close();
        }
    }
}
