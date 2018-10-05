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

package org.openkilda.wfm.share.utils;

import org.junit.Assert;
import org.junit.Test;

public class WatchDogTest {
    @Test
    public void genericWorkflow() {
        WatchDog watchDog = new WatchDog("test", 2000, 0);

        Assert.assertFalse(watchDog.detectFailure(1000));
        Assert.assertTrue(watchDog.detectFailure(2001));

        watchDog.reset(3000);
        Assert.assertFalse(watchDog.detectFailure(4000));

        watchDog.reset(4000);
        Assert.assertFalse(watchDog.detectFailure(5000));
        Assert.assertTrue(watchDog.detectFailure(6001));
        Assert.assertTrue(watchDog.detectFailure(7000));
    }
}
