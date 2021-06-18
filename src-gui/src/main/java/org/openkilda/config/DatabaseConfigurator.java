/* Copyright 2019 Telstra Open Source
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

package org.openkilda.config;

import org.openkilda.dao.entity.VersionEntity;
import org.openkilda.dao.repository.VersionRepository;

import com.ibatis.common.jdbc.ScriptRunner;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.math.BigInteger;

import java.sql.Connection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;

@Repository("databaseConfigurator")
public class DatabaseConfigurator {

    @PersistenceContext
    private EntityManager entityManager;
    
    private static final Logger LOGGER = Logger.getLogger(DatabaseConfigurator.class);
    
    private static final String SCRIPT_FILE_PREFIX = "import-script_";
    private static final String SCRIPT_FILE_SUFFIX = ".sql";
    private static final String SCRIPT_LOCATION = "db";
    
    private ResourceLoader resourceLoader;

    private VersionRepository versionEntityRepository;

    private DataSource dataSource;
    
    public DatabaseConfigurator(@Autowired final VersionRepository versionRepository, final DataSource dataSource,
            final ResourceLoader resourceLoader, EntityManager em) {
        this.versionEntityRepository = versionRepository;
        this.dataSource = dataSource;
        this.resourceLoader = resourceLoader;
        this.entityManager = em;
        init();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false)
    public void init() {
        loadInitialData();
    }

    private void loadInitialData() {
        List<Long> versionNumberList = versionEntityRepository.findAllVersionNumber();
        
        if (versionNumberList.size() == 0) {
            try {

                List<VersionEntity> list = new ArrayList<VersionEntity>();
                List<Long> newVersionList = new ArrayList<Long>();
                List<Object[]> results = entityManager.createNativeQuery("SELECT v.version_id ,"
                        + "v.version_deployment_date, v.version_number FROM version v").getResultList();
               
                for (Object[] perTestEntity :results) {
                    VersionEntity versionEntity = new VersionEntity();
                    versionEntity.setVersionId(BigInteger.valueOf(Long.valueOf(
                            (perTestEntity[0].toString()))).longValue());
                    versionEntity.setDeploymentDate(Timestamp.valueOf(perTestEntity[1].toString()));
                    versionEntity.setVersionNumber(BigInteger.valueOf(Long.valueOf(
                            perTestEntity[2].toString())).longValue());
                    list.add(versionEntity);
                    newVersionList.add(versionEntity.getVersionNumber());
                }
                versionEntityRepository.save(list);
                versionNumberList = newVersionList;
            } catch (Exception e) {
                LOGGER.warn("Failed to load version list", e);
            }
        }
        InputStream inputStream = null;
        try {
            ClassLoader  loader = getClass().getClassLoader();
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(loader);
            Resource[] resources = resolver.getResources("classpath:" + SCRIPT_LOCATION + "/*");
            List<String> dbScripts = Arrays.stream(resources)
                    .map(Resource::getFilename)
                    .collect(Collectors.toList());
            ArrayList<Long> sortedList = new ArrayList<Long>();
            for (String scriptFile :  dbScripts) {
                String scriptFileName = scriptFile.replaceFirst("[.][^.]+$", "");
                String[] scriptNumber = scriptFileName.split("_");
                Long scriptVersionNumber = Long.valueOf(scriptNumber[1]);
                sortedList.add(scriptVersionNumber);
            }
            Collections.sort(sortedList);
            for (Long scriptFileNumber :  sortedList) {
                if (!versionNumberList.isEmpty()) {
                    if (!versionNumberList.contains(scriptFileNumber)) {
                        inputStream = resourceLoader.getResource("classpath:" + SCRIPT_LOCATION + "/" 
               + SCRIPT_FILE_PREFIX + scriptFileNumber + SCRIPT_FILE_SUFFIX).getInputStream();
                        if (inputStream != null) {
                            runScript(inputStream);
                        } else {
                            break;
                        }
                    }
                } else {
                    inputStream = resourceLoader.getResource("classpath:" + SCRIPT_LOCATION + "/" 
                              + SCRIPT_FILE_PREFIX + scriptFileNumber + SCRIPT_FILE_SUFFIX).getInputStream();
                    if (inputStream != null) {
                        runScript(inputStream);
                    } else {
                        break;
                    }
                }
            }
        } catch (IOException ex) {
            LOGGER.error("Failed to load db scripts", ex);
        }
    }

    private void runScript(final InputStream inputStream) {
        try (Connection con = dataSource.getConnection()) {
            ScriptRunner sr = new ScriptRunner(con, false, false);
            sr.runScript(new InputStreamReader(inputStream));
        } catch (Exception e) {
            LOGGER.error("Error occurred while executing script", e);
        }
    }
}
