/* Copyright 2018 Telstra Open Source
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

package org.openkilda.store.service.converter;

import org.openkilda.store.common.constants.StoreType;
import org.openkilda.store.common.dao.entity.StoreTypeEntity;
import org.openkilda.store.model.StoreTypeDto;

public final class StoreTypeConverter {
    
    private StoreTypeConverter() {}

    /**
     * To store type dto.
     *
     * @param storeType the store type
     * @return the store type dto
     */
    public static StoreTypeDto toStoreTypeDto(StoreType storeType) {
        StoreTypeEntity storeTypeEntity = storeType.getStoreTypeEntity();
        StoreTypeDto storeTypeDto = new StoreTypeDto();
        storeTypeDto.setStoreTypeId(storeTypeEntity.getStoreTypeId());
        storeTypeDto.setStoreTypeName(storeTypeEntity.getStoreTypeName());
        storeTypeDto.setStoreTypeCode(storeTypeEntity.getStoreTypeCode());
        return storeTypeDto;
    }
}
