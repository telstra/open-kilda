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

package org.openkilda.model;

import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;

public class LagLogicalPortTest {
    public static final int OFFSET = 2000;
    public static final int POST_1 = 1;
    public static final int POST_2 = 2;
    public static final int POST_3 = 3;

    @Test
    public void generateLogicalPortNumberTest() {
        Assert.assertEquals(POST_1 + OFFSET,
                LagLogicalPort.generateLogicalPortNumber(Lists.newArrayList(POST_1, POST_2, POST_3), OFFSET));
    }
}
