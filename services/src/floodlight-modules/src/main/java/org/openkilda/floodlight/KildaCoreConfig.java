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

package org.openkilda.floodlight;

import com.sabre.oss.conf4j.annotation.Default;
import com.sabre.oss.conf4j.annotation.Key;

public interface KildaCoreConfig {
    @Key("command-processor-workers-count")
    @Default("4")
    int getCommandPersistentWorkersCount();

    @Key("command-processor-workers-limit")
    @Default("32")
    int getCommandWorkersLimit();

    @Key("command-processor-deferred-requests-limit")
    @Default("8")
    int getCommandDeferredRequestsLimit();

    @Key("command-processor-idle-workers-keep-alive-seconds")
    @Default("300")
    long getCommandIdleWorkersKeepAliveSeconds();

    @Key("testing-mode")
    String getTestingMode();

    default boolean isTestingMode() {
        return "YES".equals(getTestingMode());
    }
}
