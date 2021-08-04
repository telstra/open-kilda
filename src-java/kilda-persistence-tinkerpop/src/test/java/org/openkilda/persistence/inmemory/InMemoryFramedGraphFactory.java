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

package org.openkilda.persistence.inmemory;

import org.openkilda.persistence.ferma.AnnotationFrameFactoryWithConverterSupport;
import org.openkilda.persistence.ferma.FramedGraphFactory;

import com.syncleus.ferma.DelegatingFramedGraph;
import com.syncleus.ferma.typeresolvers.UntypedTypeResolver;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;

import java.io.Serializable;

public class InMemoryFramedGraphFactory implements FramedGraphFactory<DelegatingFramedGraph<?>>, Serializable {
    private transient volatile DelegatingFramedGraph<? extends TinkerGraph> framedGraph;

    /**
     * .
     */
    @Override
    public DelegatingFramedGraph<?> getGraph() {
        if (framedGraph == null) {
            synchronized (this) {
                if (framedGraph == null) {
                    framedGraph = new DelegatingFramedGraph<>(TinkerGraph.open(),
                            new AnnotationFrameFactoryWithConverterSupport(), new UntypedTypeResolver());
                }
            }
        }
        return framedGraph;
    }

    /**
     * .
     */
    public synchronized void purge() {
        if (framedGraph != null) {
            framedGraph.getBaseGraph().clear();
        }
    }
}
