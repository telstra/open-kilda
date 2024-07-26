/* Copyright 2024 Telstra Open Source
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

import org.openkilda.constants.IConstants.View;
import org.openkilda.utility.StringUtil;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.usermanagement.service.UserService;

import java.util.ArrayList;
import java.util.List;

/**
 * The Class SecurityConfig : used to configure security, authenticationManager and authProvider.
 *
 * @author Gaurav Chugh
 */
@Configuration
@DependsOn("ApplicationContextProvider")
@EnableWebSecurity
public class SecurityConfig {

    @Value("${server.contextPath:/}")
    String contextPath;

    @Value("${server.ssl.key-alias}")
    String alias;

    @Getter
    @Setter
    @Value("${server.ssl.key-store-password}")
    String secret;

    @Value("${server.port}")
    String port;

    @Value("${server.ssl.key-store}")
    @Getter
    @Setter
    private Resource keyStore;

    @Autowired
    private UserService serviceUser;

    @Autowired
    private AccessDeniedHandler accessDeniedHandler;

    private static final String[] AUTH_WHITELIST = {
            "/", "/login",
            "/authenticate",
            "/forgotpassword",
            "/401",
            "/saml/login/**", "/saml/metadata/**", "/saml/SSO/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .authorizeRequests(authorizeRequests -> authorizeRequests.requestMatchers(AUTH_WHITELIST).permitAll())
                .exceptionHandling(exceptionHandling -> exceptionHandling.accessDeniedHandler(accessDeniedHandler))
                .headers(headers -> headers
                        .addHeaderWriter(new StaticHeadersWriter("X-Content-Security-Policy", "default-src 'self'"))
                        .addHeaderWriter(new StaticHeadersWriter("Feature-Policy", "none"))
                        .addHeaderWriter(new StaticHeadersWriter("Referrer-Policy", "same-origin")))
                .sessionManagement(sessionManagement -> sessionManagement.invalidSessionUrl("/401"));
        return http.build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring()
                .requestMatchers("/resources/**", "/ui/**", "/lib/**");
    }

    @Bean("authenticationManager")
    public ProviderManager authenticationManager() {
        List<AuthenticationProvider> authProviderList = new ArrayList<>();
        authProviderList.add(authProvider());
        return new ProviderManager(authProviderList);
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

    @Bean
    public SimpleUrlAuthenticationFailureHandler authenticationFailureHandler() {
        return new SimpleUrlAuthenticationFailureHandler();
    }

    @Bean
    public SavedRequestAwareAuthenticationSuccessHandler successRedirectHandler() {
        SavedRequestAwareAuthenticationSuccessHandler successRedirectHandler =
                new SavedRequestAwareAuthenticationSuccessHandler();
        successRedirectHandler.setDefaultTargetUrl("/" + View.LOGOUT);
        return successRedirectHandler;
    }

    @Bean
    public SimpleUrlLogoutSuccessHandler successLogoutHandler() {
        return new SimpleUrlLogoutSuccessHandler();
    }

    // Logout handler terminating local session
    @Bean
    public SecurityContextLogoutHandler logoutHandler() {
        SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
        logoutHandler.setInvalidateHttpSession(true);
        logoutHandler.setClearAuthentication(true);
        return logoutHandler;
    }
}
