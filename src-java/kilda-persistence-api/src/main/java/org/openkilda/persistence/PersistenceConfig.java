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

package org.openkilda.persistence;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Default;
import com.sabre.oss.conf4j.annotation.Key;

import java.io.Serializable;

@Configuration
@Key("persistence")
public interface PersistenceConfig extends Serializable {
    @Key("implementation")
    @Default("orientdb")
    String getImplementationName();

    @Key("transaction.retries.limit")
    @Default("5")
    int getTransactionRetriesLimit();

    @Key("transaction.retries.maxdelay")
    @Default("50")
    int getTransactionRetriesMaxDelay();
}
