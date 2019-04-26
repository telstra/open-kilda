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

package org.openkilda.northbound.controller;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.neo4j.ogm.testutil.TestServer;

/**
 * Add common test functionality here.
 * <p/>
 * At present, a dependency on Neo4J has been added.
 */
public abstract class NorthboundBaseTest {

    /*
     * The neo4j block was added due to new dependency in FlowServiceImpl
     */
    static TestServer testServer;

    @BeforeClass
    public static void setUpOnce() {
        testServer = new TestServer(true, true, 5, 17687);
    }

    @AfterClass
    public static void teatDownOnce() {
        testServer.shutdown();
    }
    /*
     * End of special block for neo
     */
}
