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

package org.openkilda;

import org.openkilda.config.FilterConfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The Class OpenKildaApplication.
 *
 * @author Gaurav Chugh
 */

@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
@ComponentScan({ "org.usermanagement", "org.openkilda" })
@Import({ FilterConfig.class })
@EnableJpaRepositories({ "org.usermanagement", "org.openkilda" })
@EntityScan({ "org.usermanagement", "org.openkilda" })
@EnableAutoConfiguration
@EnableScheduling
public class OpenKildaApplication extends SpringBootServletInitializer {

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.boot.web.support.SpringBootServletInitializer#
     * configure(org.springframework .boot.builder.SpringApplicationBuilder)
     */
    @Override
    protected SpringApplicationBuilder configure(final SpringApplicationBuilder application) {
        return application.sources(OpenKildaApplication.class);
    }

    /**
     * The main method.
     *
     * @param args
     *            the arguments
     * @throws Exception
     *             the exception
     */
    public static void main(final String[] args) throws Exception {
        System.setProperty("spring.devtools.restart.enabled", "false");
        try {
            SpringApplication sa = new SpringApplication(OpenKildaApplication.class);
            sa.setLogStartupInfo(false);
            sa.run(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
