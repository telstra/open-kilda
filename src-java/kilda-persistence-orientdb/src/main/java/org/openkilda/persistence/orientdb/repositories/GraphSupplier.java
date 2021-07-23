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

package org.openkilda.persistence.orientdb.repositories;

import org.openkilda.persistence.context.PersistenceContext;
import org.openkilda.persistence.context.PersistenceContextManager;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.orientdb.OrientDbContextExtension;
import org.openkilda.persistence.orientdb.OrientDbPersistenceImplementation;

import com.syncleus.ferma.DelegatingFramedGraph;
import lombok.Getter;
import org.apache.tinkerpop.gremlin.orientdb.OrientGraph;

import java.util.function.Supplier;

/**
 * Workaround for loosing specific graph type on Ferma*Repository level. We need better solution.
 */
public class GraphSupplier implements Supplier<OrientGraph> {
    @Getter
    private final OrientDbPersistenceImplementation implementation;

    public GraphSupplier(OrientDbPersistenceImplementation implementation) {
        this.implementation = implementation;
    }

    @Override
    public OrientGraph get() {
        PersistenceContext context = PersistenceContextManager.INSTANCE.getContextCreateIfMissing();
        OrientDbContextExtension contextExtension = implementation.getContextExtension(context);
        DelegatingFramedGraph<OrientGraph> wrapper = contextExtension.getGraphCreateIfMissing();
        if (wrapper == null) {
            throw new PersistenceException("Failed to obtain a framed graph");
        }
        return wrapper.getBaseGraph();
    }
}
