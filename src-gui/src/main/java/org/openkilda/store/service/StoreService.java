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
import org.openkilda.store.common.constants.StoreType;
import org.openkilda.store.common.constants.Url;
import org.openkilda.store.common.dao.repository.StoreTypeRepository;
import org.openkilda.store.linkstore.dao.entity.LinkStoreRequestUrlsEntity;
import org.openkilda.store.linkstore.dao.repository.LinkStoreRequestUrlsRepository;
import org.openkilda.store.model.LinkStoreConfigDto;
import org.openkilda.store.model.StoreTypeDto;
import org.openkilda.store.model.SwitchStoreConfigDto;
import org.openkilda.store.model.UrlDto;
import org.openkilda.store.service.converter.StoreTypeConverter;
import org.openkilda.store.service.converter.UrlConverter;
import org.openkilda.store.switchstore.dao.entity.SwitchStoreRequestUrlsEntity;
import org.openkilda.store.switchstore.dao.repository.SwitchStoreRequestUrlsRepository;
import org.openkilda.utility.CollectionUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * The Class StoreService.
 */
@Service
public class StoreService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StoreService.class);
    
    @Autowired
    private LinkStoreRequestUrlsRepository linkStoreRequestUrlsRepository;

    @Autowired
    private SwitchStoreRequestUrlsRepository switchStoreRequestUrlsRepository;

    @Autowired
    private OauthConfigRepository oauthConfigRepository;

    @Autowired
    private StoreTypeRepository storeTypeRepository;

    /**
     * Gets the store types.
     *
     * @return the store types
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public List<StoreTypeDto> getStoreTypes() {
        LOGGER.info("Get store types");
        List<StoreTypeDto> list = new ArrayList<StoreTypeDto>();
        StoreType[] storeTypes = StoreType.values();
        for (StoreType storeType : storeTypes) {
            list.add(StoreTypeConverter.toStoreTypeDto(storeType));
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
    public LinkStoreConfigDto saveOrUpdateLinkStoreConfig(final LinkStoreConfigDto linkStoreConfigDto) {
        LOGGER.info("Save or update link store configuration");
        saveOrUpdateStoreTypeEntity(StoreType.LINK_STORE.getCode());
        List<LinkStoreRequestUrlsEntity> linkStoreRequestUrlsEntitiesList = new ArrayList<LinkStoreRequestUrlsEntity>();
        List<LinkStoreRequestUrlsEntity> linkStoreRequestUrlsEntities = linkStoreRequestUrlsRepository.findAll();
        for (Entry<String, UrlDto> urlEntrySet : linkStoreConfigDto.getUrls().entrySet()) {
            if (linkStoreRequestUrlsEntities != null) {
                LinkStoreRequestUrlsEntity linkStoreRequestUrlsEntity = null;
                for (LinkStoreRequestUrlsEntity linkStoreRequestUrlsEntityObj : linkStoreRequestUrlsEntities) {
                    if (linkStoreRequestUrlsEntityObj.getUrlEntity().getName()
                            .equalsIgnoreCase(urlEntrySet.getValue().getName())) {
                        linkStoreRequestUrlsEntity = linkStoreRequestUrlsEntityObj;
                        break;
                    }
                }
                if (linkStoreRequestUrlsEntity == null) {
                    linkStoreRequestUrlsEntity = new LinkStoreRequestUrlsEntity();
                }
                linkStoreRequestUrlsEntity.setUrlEntity(UrlConverter.toUrlEntity(urlEntrySet.getKey(),
                        urlEntrySet.getValue(), linkStoreRequestUrlsEntity.getUrlEntity()));
                linkStoreRequestUrlsEntitiesList.add(linkStoreRequestUrlsEntity);
            }
        }
        linkStoreRequestUrlsEntitiesList = linkStoreRequestUrlsRepository.save(linkStoreRequestUrlsEntitiesList);

        Map<String, UrlDto> urls = new HashMap<String, UrlDto>();
        for (LinkStoreRequestUrlsEntity linkStoreRequestUrlsEntity : linkStoreRequestUrlsEntitiesList) {
            urls.put(linkStoreRequestUrlsEntity.getUrlEntity().getName(),
                    UrlConverter.toUrlDto(linkStoreRequestUrlsEntity.getUrlEntity()));
        }
        linkStoreConfigDto.setUrls(urls);
        return linkStoreConfigDto;
    }

    /**
     * Gets the link store config.
     *
     * @return the link store config
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public LinkStoreConfigDto getLinkStoreConfig() {
        LOGGER.info("Get link store configuration");
        LinkStoreConfigDto linkStoreConfigDto = new LinkStoreConfigDto();
        List<LinkStoreRequestUrlsEntity> linkStoreRequestUrlsEntitiesList = linkStoreRequestUrlsRepository.findAll();

        Map<String, UrlDto> urls = new HashMap<String, UrlDto>();
        for (LinkStoreRequestUrlsEntity linkStoreRequestUrlsEntity : linkStoreRequestUrlsEntitiesList) {
            urls.put(linkStoreRequestUrlsEntity.getUrlEntity().getName(),
                    UrlConverter.toUrlDto(linkStoreRequestUrlsEntity.getUrlEntity()));
        }
        linkStoreConfigDto.setUrls(urls);
        return linkStoreConfigDto;
    }

    /**
     * Gets the url.
     *
     * @param storeType the store type

     * @param url the url

     * @return the url
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public UrlDto getUrl(final StoreType storeType, final Url url) {
        LOGGER.info("Get urls for store type");
        UrlDto urlDto = null;

        if (storeType == StoreType.LINK_STORE) {
            LinkStoreRequestUrlsEntity urlsEntity = linkStoreRequestUrlsRepository.findByUrlEntity_name(url.getName());
            urlDto = UrlConverter.toUrlDto(urlsEntity.getUrlEntity());
        }

        if (storeType == StoreType.SWITCH_STORE) {
            SwitchStoreRequestUrlsEntity urlsEntity = switchStoreRequestUrlsRepository
                    .findByUrlEntity_name(url.getName());
            urlDto = UrlConverter.toUrlDto(urlsEntity.getUrlEntity());
        }

        return urlDto;
    }

    /**
     * Delete link store config.
     *
     * @return true, if successful
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public boolean deleteLinkStoreConfig() {
        LOGGER.info("delete link store configuration");
        linkStoreRequestUrlsRepository.deleteAll();
        List<LinkStoreRequestUrlsEntity> linkStoreRequestUrlsEntitiesList = linkStoreRequestUrlsRepository.findAll();
        return CollectionUtil.isEmpty(linkStoreRequestUrlsEntitiesList);
    }

    /**
     * Save or update switch store config.
     *
     * @param switchStoreConfigDto he link store config dto

     * @return the switch store config dto
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public SwitchStoreConfigDto saveOrUpdateSwitchStoreConfig(final SwitchStoreConfigDto switchStoreConfigDto) {
        LOGGER.info("Save or update switch store configuration");
        saveOrUpdateStoreTypeEntity(StoreType.SWITCH_STORE.getCode());
        List<SwitchStoreRequestUrlsEntity> switchStoreRequestUrlsEntitiesList =
                new ArrayList<SwitchStoreRequestUrlsEntity>();
        List<SwitchStoreRequestUrlsEntity> switchStoreRequestUrlsEntities = switchStoreRequestUrlsRepository.findAll();
        for (Entry<String, UrlDto> urlEntrySet : switchStoreConfigDto.getUrls().entrySet()) {
            if (switchStoreRequestUrlsEntities != null) {
                SwitchStoreRequestUrlsEntity switchStoreRequestUrlsEntity = null;
                for (SwitchStoreRequestUrlsEntity switchStoreRequestUrlsEntityObj : switchStoreRequestUrlsEntities) {
                    if (switchStoreRequestUrlsEntityObj.getUrlEntity().getName()
                            .equalsIgnoreCase(urlEntrySet.getValue().getName())) {
                        switchStoreRequestUrlsEntity = switchStoreRequestUrlsEntityObj;
                        break;
                    }
                }
                if (switchStoreRequestUrlsEntity == null) {
                    switchStoreRequestUrlsEntity = new SwitchStoreRequestUrlsEntity();
                }
                switchStoreRequestUrlsEntity.setUrlEntity(UrlConverter.toUrlEntity(urlEntrySet.getKey(),
                        urlEntrySet.getValue(), switchStoreRequestUrlsEntity.getUrlEntity()));
                switchStoreRequestUrlsEntitiesList.add(switchStoreRequestUrlsEntity);
            }
        }
        switchStoreRequestUrlsEntitiesList = switchStoreRequestUrlsRepository.save(switchStoreRequestUrlsEntitiesList);

        Map<String, UrlDto> urls = new HashMap<String, UrlDto>();
        for (SwitchStoreRequestUrlsEntity switchStoreRequestUrlsEntity : switchStoreRequestUrlsEntitiesList) {
            urls.put(switchStoreRequestUrlsEntity.getUrlEntity().getName(),
                    UrlConverter.toUrlDto(switchStoreRequestUrlsEntity.getUrlEntity()));
        }
        switchStoreConfigDto.setUrls(urls);
        return switchStoreConfigDto;
    }

    /**
     * Delete link store config.
     *
     * @return true, if successful
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public boolean deleteSwitchStoreConfig() {
        LOGGER.info("Delete switch store configuration");
        switchStoreRequestUrlsRepository.deleteAll();
        List<SwitchStoreRequestUrlsEntity> switchStoreRequestUrlsEntitiesList = switchStoreRequestUrlsRepository
                .findAll();
        return CollectionUtil.isEmpty(switchStoreRequestUrlsEntitiesList);
    }

    /**
     * Gets the switch store config.
     *
     * @return the switch store config
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public SwitchStoreConfigDto getSwitchStoreConfig() {
        LOGGER.info("Get switch store configuration");
        SwitchStoreConfigDto switchStoreConfigDto = new SwitchStoreConfigDto();
        List<SwitchStoreRequestUrlsEntity> switchStoreRequestUrlsEntitiesList = switchStoreRequestUrlsRepository
                .findAll();

        Map<String, UrlDto> urls = new HashMap<String, UrlDto>();
        for (SwitchStoreRequestUrlsEntity switchStoreRequestUrlsEntity : switchStoreRequestUrlsEntitiesList) {
            urls.put(switchStoreRequestUrlsEntity.getUrlEntity().getName(),
                    UrlConverter.toUrlDto(switchStoreRequestUrlsEntity.getUrlEntity()));
        }
        switchStoreConfigDto.setUrls(urls);
        return switchStoreConfigDto;
    }

    /**
     * Save or update store type entity.
     * 
     * @param storeType the storeType
     */
    private void saveOrUpdateStoreTypeEntity(String storeType) {
        LOGGER.info("Save or update store types.");
        List<OauthConfigEntity> oauthConfigEntityList = oauthConfigRepository
                .findByAuthType_authTypeId(AuthType.OAUTH_TWO.getAuthTypeEntity().getAuthTypeId());

        if (StoreType.LINK_STORE.getCode().equalsIgnoreCase(storeType)) {
            StoreType.LINK_STORE.getStoreTypeEntity().setOauthConfigEntity(oauthConfigEntityList.get(0));
            storeTypeRepository.save(StoreType.LINK_STORE.getStoreTypeEntity());
        }

        if (StoreType.SWITCH_STORE.getCode().equalsIgnoreCase(storeType)) {
            StoreType.SWITCH_STORE.getStoreTypeEntity().setOauthConfigEntity(oauthConfigEntityList.get(0));
            storeTypeRepository.save(StoreType.SWITCH_STORE.getStoreTypeEntity());
        }

    }
}
