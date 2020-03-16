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

import org.openkilda.store.auth.constants.AuthType;
import org.openkilda.store.auth.dao.entity.AuthTypeEntity;
import org.openkilda.store.model.AuthTypeDto;

public final class AuthTypeConverter {
    
    private AuthTypeConverter() { }

    /**
     * To auth type dto.
     *
     * @param authType the auth type
     * @return the auth type dto
     */
    public static AuthTypeDto toAuthTypeDto(AuthType authType) {
        AuthTypeEntity authTypeEntity = authType.getAuthTypeEntity();
        AuthTypeDto authTypeDto = new AuthTypeDto();
        authTypeDto.setAuthTypeId(authTypeEntity.getAuthTypeId());
        authTypeDto.setAuthTypeName(authTypeEntity.getAuthTypeName());
        authTypeDto.setAuthTypeCode(authTypeEntity.getAuthTypeCode());
        return authTypeDto;
    }
}
