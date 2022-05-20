/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.switchmanager.service.configs;

import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.share.utils.PoolManager;

import com.google.common.base.Preconditions;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
public class LagPortOperationConfig {
    @NonNull
    RepositoryFactory repositoryFactory;

    @NonNull
    TransactionManager transactionManager;

    int bfdPortOffset;
    int bfdPortMaxNumber;

    int poolCacheSize;

    PoolManager.PoolConfig poolConfig;

    @Builder
    public LagPortOperationConfig(
            @NonNull RepositoryFactory repositoryFactory, @NonNull TransactionManager transactionManager,
            int bfdPortOffset, int bfdPortMaxNumber,
            int portNumberFirst, int portNumberLast, int poolChunksCount, int poolCacheSize) {
        this.repositoryFactory = repositoryFactory;
        this.transactionManager = transactionManager;

        Preconditions.checkArgument(0 < bfdPortOffset, String.format(
                "BFD logical port offset bfdPortOffset==%d must be greater than 0", bfdPortOffset));
        this.bfdPortOffset = bfdPortOffset;

        Preconditions.checkArgument(bfdPortOffset <= bfdPortMaxNumber, String.format(
                "BFD logical port maximum value bfdPortMaxNumber==%d must be greater than bfdPortOffset==%d",
                bfdPortMaxNumber, bfdPortOffset));
        this.bfdPortMaxNumber = bfdPortMaxNumber;

        Preconditions.checkArgument(0 < poolCacheSize, String.format(
                "LAG port number pool managers cache size poolCacheSize==%d must be greater than 0",
                poolCacheSize));
        this.poolCacheSize = poolCacheSize;

        poolConfig = new PoolManager.PoolConfig(portNumberFirst, portNumberLast, poolChunksCount);
    }
}
