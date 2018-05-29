package org.openkilda.security;


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

import java.util.ArrayList;
import java.util.List;

import org.openkilda.utility.StringUtil;
import org.usermanagement.service.UserService;

/**
 * The Class SecurityConfig : used to configure security, authenticationManager and authProvider
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
     * @see
     * org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
     * #configure(org.springframework.security.config.annotation.web.builders.HttpSecurity)
     */

    @Override
    protected void configure(final HttpSecurity http) throws Exception {

        http.csrf().disable().authorizeRequests()
                .antMatchers("/login", "/authenticate", "/forgotpassword")
                .permitAll().anyRequest().authenticated().and().formLogin().loginPage("/login").permitAll().and()
                .logout().permitAll().and().exceptionHandling().accessDeniedHandler(accessDeniedHandler);
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

    /*
     * (non-Javadoc)
     *
     * @see
     * org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
     * #authenticationManager()
     */
    @Override
    @Bean("authenticationManager")
    public ProviderManager authenticationManager() {
        List<AuthenticationProvider> authProviderList = new ArrayList<AuthenticationProvider>();
        authProviderList.add(authProvider());
        ProviderManager providerManager = new ProviderManager(authProviderList);
        return providerManager;
    }

    @Override
    public void configure(final WebSecurity web) throws Exception {
        web.ignoring().antMatchers("/resources/**", "/fonts/**", "/css/**", "/javascript/**", "/templates/**");
    }
}
