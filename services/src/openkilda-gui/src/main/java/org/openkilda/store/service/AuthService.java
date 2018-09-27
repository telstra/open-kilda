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

package org.openkilda.store.service;

import org.openkilda.store.auth.constants.AuthType;
import org.openkilda.store.auth.dao.entity.OauthConfigEntity;
import org.openkilda.store.auth.dao.repository.OauthConfigRepository;
import org.openkilda.store.model.AuthTypeDto;
import org.openkilda.store.model.LinkStoreConfigDto;
import org.openkilda.store.model.OauthTwoConfigDto;
import org.openkilda.store.service.converter.AuthTypeConverter;
import org.openkilda.store.service.converter.OauthConfigConverter;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Class AuthService.
 */

@Service
public class AuthService {
    
    @Autowired
    private OauthConfigRepository oauthConfigRepository;
    
    /**
     * Gets the auth types.
     *
     * @return the auth types
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public List<AuthTypeDto> getAuthTypes() {
        List<AuthTypeDto> list = new ArrayList<AuthTypeDto>();
        AuthType[] authTypes = AuthType.values();
        for (AuthType authType : authTypes) {
            list.add(AuthTypeConverter.toAuthTypeDto(authType));
        }
        return list;
    }
    
    /**
     * Save or update link store config.
     *
     * @param linkStoreConfigDto the link store config dto
     * @return the link store config dto
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public OauthTwoConfigDto saveOrUpdateOauthConfig(final OauthTwoConfigDto oauthTwoConfigDto) {
        OauthConfigConverter oauthConfigConverter = new OauthConfigConverter();
        List<OauthConfigEntity> oauthConfigEntityList = oauthConfigRepository
                .findByAuthType_authTypeId(AuthType.OAUTH_TWO.getAuthTypeEntity().getAuthTypeId());
        OauthConfigEntity oauthConfigEntity = null;
        if (oauthConfigEntityList.size() > 0) {
            oauthConfigEntity = oauthConfigEntityList.get(0);
        }
        if (oauthConfigEntity == null) {
            oauthConfigEntity = new OauthConfigEntity();
        }
        oauthConfigEntity = oauthConfigConverter.toOauthConfigEntity(oauthTwoConfigDto, oauthConfigEntity);
        
        oauthConfigEntity = oauthConfigRepository.save(oauthConfigEntity);
        
        return oauthConfigConverter.toOauthTwoConfigDto(oauthConfigEntity);
    }
    
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public OauthTwoConfigDto getOauthConfig() {
        OauthConfigConverter oauthConfigConverter = new OauthConfigConverter();
        List<OauthConfigEntity> oauthConfigEntityList = oauthConfigRepository
                .findByAuthType_authTypeId(AuthType.OAUTH_TWO.getAuthTypeEntity().getAuthTypeId());
        OauthConfigEntity oauthConfigEntity = null;
        if (oauthConfigEntityList.size() > 0) {
            oauthConfigEntity = oauthConfigEntityList.get(0);
        }
        if (oauthConfigEntity == null) {
            oauthConfigEntity = new OauthConfigEntity();
        }
        return oauthConfigConverter.toOauthTwoConfigDto(oauthConfigEntity);
    }
}
