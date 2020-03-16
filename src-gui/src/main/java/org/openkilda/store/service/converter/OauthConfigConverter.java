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
import org.openkilda.store.auth.dao.entity.OauthConfigEntity;
import org.openkilda.store.common.constants.Url;
import org.openkilda.store.model.OauthTwoConfigDto;
import org.openkilda.utility.GeneratePassword;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OauthConfigConverter {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(OauthConfigConverter.class);
    
    private OauthConfigConverter() {}

    /**
     * To oauth config entity.
     *
     * @param oauthTwoConfigDto the oauth two config dto
     * @param oauthConfigEntity the oauth config entity
     * @return the oauth config entity
     */
    public static OauthConfigEntity toOauthConfigEntity(final OauthTwoConfigDto oauthTwoConfigDto,
            final OauthConfigEntity oauthConfigEntity) {
        oauthConfigEntity.setUsername(oauthTwoConfigDto.getUsername());
        try {
            oauthConfigEntity.setPassword(GeneratePassword.encrypt(oauthTwoConfigDto.getPassword()));
        } catch (Exception e) {
            LOGGER.error("Password encryption failed for user: " + oauthTwoConfigDto.getUsername(), e);
        }
        oauthConfigEntity.setGenerateToken(UrlConverter.toUrlEntity(Url.OAUTH_GENERATE_TOKEN.getName(),
                oauthTwoConfigDto.getOauthGenerateTokenUrl(), oauthConfigEntity.getGenerateToken()));
        oauthConfigEntity.setRefreshToken(UrlConverter.toUrlEntity(Url.OAUTH_REFRESH_TOKEN.getName(),
                oauthTwoConfigDto.getOauthRefreshTokenUrl(), oauthConfigEntity.getRefreshToken()));
        oauthConfigEntity.setAuthType(AuthType.OAUTH_TWO.getAuthTypeEntity());
        return oauthConfigEntity;
    }
    
    /**
     * To oauth two config dto.
     *
     * @param oauthConfigEntity the oauth config entity
     * @return the oauth two config dto
     */
    public static OauthTwoConfigDto toOauthTwoConfigDto(final OauthConfigEntity oauthConfigEntity) {
        OauthTwoConfigDto oauthTwoConfigDto = new OauthTwoConfigDto();
        oauthTwoConfigDto.setUsername(oauthConfigEntity.getUsername());
        try {
            oauthTwoConfigDto.setPassword(GeneratePassword.decrypt(oauthConfigEntity.getPassword()));
        } catch (Exception e) {
            LOGGER.error("Password decryption failed for user: " + oauthConfigEntity.getUsername(), e);
        }
        oauthTwoConfigDto.setOauthGenerateTokenUrl(UrlConverter.toUrlDto(oauthConfigEntity.getGenerateToken()));
        oauthTwoConfigDto.setOauthRefreshTokenUrl(UrlConverter.toUrlDto(oauthConfigEntity.getRefreshToken()));
        return oauthTwoConfigDto;
    }
}
