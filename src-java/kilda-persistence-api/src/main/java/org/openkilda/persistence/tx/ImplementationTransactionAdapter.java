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

package org.openkilda.persistence.tx;

import org.openkilda.persistence.PersistenceImplementation;
import org.openkilda.persistence.PersistenceImplementationType;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ImplementationTransactionAdapter<T extends PersistenceImplementation> {
    @Getter
    private final T implementation;

    public ImplementationTransactionAdapter(T implementation) {
        this.implementation = implementation;
    }

    public Exception wrapException(Exception ex) {
        return ex;
    }

    public abstract void open() throws Exception;

    public abstract void commit() throws Exception;

    public abstract void rollback() throws Exception;

    public PersistenceImplementationType getImplementationType() {
        return implementation.getType();
    }
}
