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

import org.openkilda.store.common.dao.entity.UrlEntity;
import org.openkilda.store.model.UrlDto;
import org.openkilda.utility.StringUtil;

/**
 * The Class UrlConverter.
 */

public final class UrlConverter {
    
    private UrlConverter() {}
    
    /**
     * To url entity.
     *
     * @param name the name
     * @param dto the dto
     * @param urlEntity the url entity
     * @return the url entity
     */
    public static UrlEntity toUrlEntity(final String name, final UrlDto dto, final UrlEntity urlEntity) {
        urlEntity.setName(name);
        urlEntity.setUrl(dto.getUrl().trim());
        urlEntity.setMethodType(dto.getMethodType());
        urlEntity.setHeader(dto.getHeader());
        urlEntity.setBody(dto.getBody());
        return urlEntity;
    }
    
    /**
     * To url dto.
     *
     * @param urlEntity the url entity
     * @return the url dto
     */
    public static UrlDto toUrlDto(final UrlEntity urlEntity) {
        UrlDto urlDto = new UrlDto();
        urlDto.setName(urlEntity.getName());
        urlDto.setUrl(StringUtil.isNullOrEmpty(urlEntity.getUrl()) ? urlEntity.getUrl() : urlEntity.getUrl().trim());
        urlDto.setMethodType(urlEntity.getMethodType());
        urlDto.setHeader(urlEntity.getHeader());
        urlDto.setBody(urlEntity.getBody());
        return urlDto;
    }
    
}
