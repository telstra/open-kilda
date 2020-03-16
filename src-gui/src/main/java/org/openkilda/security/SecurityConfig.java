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

package org.openkilda.security;

import org.openkilda.utility.StringUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.access.AccessDeniedHandler;

import org.usermanagement.service.UserService;

import java.util.ArrayList;
import java.util.List;

/**
 * The Class SecurityConfig : used to configure security, authenticationManager and authProvider.
 *
 * @author Gaurav Chugh
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private UserService serviceUser;

    @Autowired
    private AccessDeniedHandler accessDeniedHandler;

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.security.config.annotation.web.configuration.
     * WebSecurityConfigurerAdapter
     * #configure(org.springframework.security.config.annotation.web.builders.
     * HttpSecurity)
     */

    @Override
    protected void configure(final HttpSecurity http) throws Exception {

        http.csrf().disable().authorizeRequests().antMatchers("/login", "/authenticate", "/forgotpassword", "/401")
                .permitAll().anyRequest().authenticated().and().formLogin().loginPage("/login").permitAll().and()
                .logout().permitAll().and().exceptionHandling().accessDeniedHandler(accessDeniedHandler).and()
                .sessionManagement().invalidSessionUrl("/401");
    }

    @Override
    public void configure(final WebSecurity web) throws Exception {
        web.ignoring().antMatchers("/resources/**", "/ui/**", "/lib/**");
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.springframework.security.config.annotation.web.configuration.
     * WebSecurityConfigurerAdapter #authenticationManager()
     */
    @Override
    @Bean("authenticationManager")
    public ProviderManager authenticationManager() {
        List<AuthenticationProvider> authProviderList = new ArrayList<AuthenticationProvider>();
        authProviderList.add(authProvider());
        ProviderManager providerManager = new ProviderManager(authProviderList);
        return providerManager;
    }
    
    /**
     * Auth provider.
     *
     * @return the dao authentication provider
     */
    @Bean("authProvider")
    public CustomAuthenticationProvider authProvider() {
        CustomAuthenticationProvider authProvider = new CustomAuthenticationProvider();
        authProvider.setUserDetailsService(serviceUser);
        authProvider.setPasswordEncoder(StringUtil.getEncoder());
        return authProvider;
    }

}
