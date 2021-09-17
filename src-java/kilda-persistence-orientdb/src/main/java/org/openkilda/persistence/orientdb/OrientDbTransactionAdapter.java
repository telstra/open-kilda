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

import org.openkilda.persistence.exceptions.ConstraintViolationException;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.exceptions.RecoverablePersistenceException;
import org.openkilda.persistence.ferma.FermaTransactionAdapter;

import com.orientechnologies.common.concur.ONeedRetryException;
import com.orientechnologies.common.exception.OException;
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException;

public class OrientDbTransactionAdapter extends FermaTransactionAdapter<OrientDbPersistenceImplementation> {
    public OrientDbTransactionAdapter(OrientDbPersistenceImplementation implementation) {
        super(implementation);
    }

    @Override
    public Exception wrapException(Exception ex) {
        if (ex instanceof ORecordDuplicatedException) {
            return new ConstraintViolationException("Failure in transaction", ex);
        } else if (ex instanceof ONeedRetryException) {
            return new RecoverablePersistenceException("Failure in transaction", ex);
        } else if (ex instanceof OException) {
            return new PersistenceException("Failure in transaction", ex);
        } else {
            return super.wrapException(ex);
        }
    }
}
