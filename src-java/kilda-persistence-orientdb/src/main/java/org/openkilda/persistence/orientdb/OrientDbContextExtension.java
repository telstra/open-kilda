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

package org.openkilda.persistence.orientdb;

import org.openkilda.persistence.PersistenceImplementationType;
import org.openkilda.persistence.ferma.FermaContextExtension;

import com.syncleus.ferma.DelegatingFramedGraph;
import lombok.Getter;
import org.apache.tinkerpop.gremlin.orientdb.OrientGraph;

public class OrientDbContextExtension implements FermaContextExtension {
    @Getter
    private final PersistenceImplementationType implementationType;

    private final OrientDbGraphFactory graphFactory;

    private DelegatingFramedGraph<OrientGraph> graph = null;

    public OrientDbContextExtension(
            PersistenceImplementationType implementationType, OrientDbGraphFactory graphFactory) {
        this.implementationType = implementationType;
        this.graphFactory = graphFactory;
    }

    @Override
    public DelegatingFramedGraph<OrientGraph> getGraphCreateIfMissing() {
        if (graph == null) {
            graph = graphFactory.getGraph();
        }
        return graph;
    }

    public DelegatingFramedGraph<OrientGraph> removeGraph() {
        DelegatingFramedGraph<OrientGraph> effective = graph;
        graph = null;
        return effective;
    }
}
