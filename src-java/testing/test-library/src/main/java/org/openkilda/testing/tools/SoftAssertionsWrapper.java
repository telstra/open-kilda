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

package org.openkilda.testing.tools;


import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.ThrowableAssert;

import java.util.concurrent.Callable;

/**
 * Allows to assert multiple times without failing, then fail with multiple assertion errors at once.
 */
public class SoftAssertionsWrapper {

    private final SoftAssertions softAssertions = new SoftAssertions();

    public void verify() {
        softAssertions.assertAll();
    }

    /**
     * Make an assertion. Delay potential assertion error throw until 'verify()' is called
     */
    public boolean checkSucceeds(Callable<?> callable) {
        try {
            softAssertions.assertThatCode(wrap(callable)).doesNotThrowAnyException();
        } catch (AssertionError e) {
            return true;
        }
        return true;
    }

    private ThrowableAssert.ThrowingCallable wrap(Callable<?> callable) {
        return callable::call;
    }
}
