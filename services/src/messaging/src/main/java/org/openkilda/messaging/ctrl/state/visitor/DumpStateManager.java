/* Copyright 2017 Telstra Open Source
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

package org.openkilda.messaging.ctrl.state.visitor;


import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.CtrlResponse;
import org.openkilda.messaging.ctrl.DumpStateResponseData;
import org.openkilda.messaging.ctrl.state.CacheBoltState;
import org.openkilda.messaging.ctrl.state.CrudBoltState;
import org.openkilda.messaging.ctrl.state.OFELinkBoltState;
import org.openkilda.messaging.ctrl.state.ResorceCacheBoltState;
import org.openkilda.messaging.ctrl.state.TransactionBoltState;

import java.util.List;

/**
 * DumpStateManager is manager from visitor pattern. Main goal is avoid using instanceof on list
 * of AbstractDumpState to check type.
 */
public class DumpStateManager {

    private CacheBoltState cacheBoltState;
    private CrudBoltState crudBoltState;
    private OFELinkBoltState ofeLinkBoltState;
    private TransactionBoltState transactionBoltState;
    private ResorceCacheBoltState resorceCacheBoltState;

    private DumpStateVisitor addVisitor = new DumpStateVisitor(this);

    /**
     * Gets a dump state manager from responses list.
     * @param responses a responses.
     * @return the dump state manager.
     */
    public static DumpStateManager fromResponsesList(List<CtrlResponse> responses) {
        DumpStateManager dumpStateManager = new DumpStateManager();
        responses.forEach(
                r -> dumpStateManager.add(((DumpStateResponseData) r.getData()).getState()));
        return dumpStateManager;
    }

    public void add(AbstractDumpState abstractDumpState) {
        abstractDumpState.accept(addVisitor);
    }

    public CacheBoltState getCacheBoltState() {
        return cacheBoltState;
    }

    public void setCacheBoltState(CacheBoltState cacheBoltState) {
        this.cacheBoltState = cacheBoltState;
    }

    public CrudBoltState getCrudBoltState() {
        return crudBoltState;
    }

    public void setCrudBoltState(CrudBoltState crudBoltState) {
        this.crudBoltState = crudBoltState;
    }

    public OFELinkBoltState getOfeLinkBoltState() {
        return ofeLinkBoltState;
    }

    public void setOfeLinkBoltState(OFELinkBoltState ofeLinkBoltState) {
        this.ofeLinkBoltState = ofeLinkBoltState;
    }

    public TransactionBoltState getTransactionBoltState() {
        return transactionBoltState;
    }

    public void setTransactionBoltState(
            TransactionBoltState transactionBoltState) {
        this.transactionBoltState = transactionBoltState;
    }

    public ResorceCacheBoltState getResorceCacheBoltState() {
        return resorceCacheBoltState;
    }

    public void setResorceCacheBoltState(
            ResorceCacheBoltState resorceCacheBoltState) {
        this.resorceCacheBoltState = resorceCacheBoltState;
    }
}
