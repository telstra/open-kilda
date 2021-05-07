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

package org.openkilda.persistence.spi;

import org.openkilda.persistence.DummyPersistenceManager;
import org.openkilda.persistence.PersistenceManager;

import java.io.Serializable;
import java.util.function.Supplier;

public class PersistenceManagerSupplier implements Supplier<PersistenceManager>, Serializable {
    public static PersistenceManagerSupplier DEFAULT = new PersistenceManagerSupplier(new DummyPersistenceManager());

    private PersistenceManager implementation;

    public PersistenceManagerSupplier() {
        this(null);
    }

    public PersistenceManagerSupplier(PersistenceManager implementation) {
        this.implementation = implementation;
    }

    @Override
    public PersistenceManager get() {
        if (implementation == null) {
            throw new IllegalStateException(String.format(
                    "%s do not contain %s instance", getClass().getName(), PersistenceManager.class.getName()));
        }
        return implementation;
    }

    public void define(PersistenceManager manager) {
        implementation = manager;
    }
}
