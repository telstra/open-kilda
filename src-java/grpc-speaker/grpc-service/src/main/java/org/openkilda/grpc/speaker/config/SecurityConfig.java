/* Copyright 2017 Telstra Open Source
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

package org.openkilda.grpc.speaker.config;

import org.openkilda.grpc.speaker.utils.GrpcBasicAuthenticationEntryPoint;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring security configuration.
 */
@Configuration
@EnableWebSecurity
@PropertySource("classpath:grpc-service.properties")
public class SecurityConfig {
    /**
     * Default role for admin user.
     */
    private static final String DEFAULT_ROLE = "ADMIN";

    /**
     * The environment variable for username.
     */
    @Value("${security.rest.username.env}")
    private String envUsername;

    /**
     * The environment variable for password.
     */
    @Value("${security.rest.password.env}")
    private String envPassword;

    /**
     * The service username environment variable name.
     */
    @Value("${security.rest.username.default}")
    private String defaultUsername;

    /**
     * The service password environment variable name.
     */
    @Value("${security.rest.password.default}")
    private String defaultPassword;

    /**
     * Basic auth entry point.
     */
    @Autowired
    private GrpcBasicAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    protected InMemoryUserDetailsManager userDetailsService() {
        // get username from environment variable, otherwise use default
        String username = System.getenv(envUsername);
        if (username == null || username.isEmpty()) {
            username = defaultUsername;
        }
        // get password from environment variable, otherwise use default
        String password = System.getenv(envPassword);
        if (password == null || password.isEmpty()) {
            password = defaultPassword;
        }

        UserDetails user = User.withDefaultPasswordEncoder()
                .username(username)
                .password(password)
                .roles(DEFAULT_ROLE)
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    protected SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf().disable().authorizeHttpRequests().requestMatchers("/health-check").permitAll().and()
                .authorizeHttpRequests().anyRequest().fullyAuthenticated().and()
                .httpBasic().authenticationEntryPoint(authenticationEntryPoint).and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        return http.build();
    }

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }
}
