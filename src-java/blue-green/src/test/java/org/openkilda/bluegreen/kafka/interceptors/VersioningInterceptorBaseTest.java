/* Copyright 2020 Telstra Open Source
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

package org.openkilda.bluegreen.kafka.interceptors;

import static org.openkilda.bluegreen.kafka.interceptors.VersioningInterceptorBase.VERSION_IS_NOT_SET_LOG_TIMEOUT;

import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class VersioningInterceptorBaseTest {

    @Test
    public void timeoutInitializationTest() {
        VersioningInterceptorBase interceptorBase = new TestClass();
        // initial value of timestamp is 0, so timeout is passed
        Assert.assertTrue(interceptorBase.isVersionTimeoutPassed());
    }

    @Test
    public void timeoutIsNotPassedTest() {
        VersioningInterceptorBase interceptorBase = new TestClass();
        interceptorBase.versionIsNotSetTimestamp = Instant.now();
        Assert.assertFalse(interceptorBase.isVersionTimeoutPassed());
    }

    @Test
    public void timeoutIsPassedTest() {
        VersioningInterceptorBase interceptorBase = new TestClass();
        interceptorBase.versionIsNotSetTimestamp = Instant.now()
                .minus(VERSION_IS_NOT_SET_LOG_TIMEOUT * 2, ChronoUnit.SECONDS);
        Assert.assertTrue(interceptorBase.isVersionTimeoutPassed());
    }

    // needed to create an instance of abstract class VersioningInterceptorBase
    private static class TestClass extends VersioningInterceptorBase {
        @Override
        public void handle(String buildVersion) {
        }
    }
}
