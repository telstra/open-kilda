/* Copyright 2017 Telstra Open Source
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

package org.bitbucket.openkilda.smoke;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.bitbucket.openkilda.topo.*;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;

/**
 * Created by carmine on 3/10/17.
 */
public class MininetSmokeTest {

    private void testIt(URL url) throws Exception {
        TestUtils.clearEverything();

        TopologyHelp.TestMininetCreate(
                Resources.toString(url, Charsets.UTF_8)
        );
    }

    @Test
    public void test5_20() throws Exception {
        testIt(Resources.getResource("topologies/rand-5-20.json"));
    }

}
