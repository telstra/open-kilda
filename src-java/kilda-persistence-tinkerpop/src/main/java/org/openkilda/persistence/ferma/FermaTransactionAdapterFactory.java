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

package org.openkilda.persistence.ferma;

import org.openkilda.persistence.tx.TransactionAdapter;
import org.openkilda.persistence.tx.TransactionAdapterFactory;
import org.openkilda.persistence.tx.TransactionArea;
import org.openkilda.persistence.tx.TransactionLayout;

import com.syncleus.ferma.DelegatingFramedGraph;

public class FermaTransactionAdapterFactory implements TransactionAdapterFactory {
    protected final TransactionArea area;

    protected final FramedGraphFactory<DelegatingFramedGraph<?>> graphFactory;

    public FermaTransactionAdapterFactory(
            TransactionArea area, FramedGraphFactory<DelegatingFramedGraph<?>> graphFactory) {
        this.area = area;
        this.graphFactory = graphFactory;
    }

    @Override
    public TransactionAdapter produce(TransactionLayout layout) {
        return new FermaTransactionAdapter(layout.evaluateEffectiveArea(area), graphFactory);
    }
}
