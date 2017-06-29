package org.bitbucket.openkilda.northbound.config;

import org.bitbucket.openkilda.northbound.utils.NorthboundBasicAuthenticationEntryPoint;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;

/**
 * Spring security configuration.
 */
@Configuration
@EnableWebSecurity
@PropertySource("classpath:northbound.properties")
public class SecurityConfig extends WebSecurityConfigurerAdapter {
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
    private NorthboundBasicAuthenticationEntryPoint authenticationEntryPoint;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
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
        auth.inMemoryAuthentication().withUser(username).password(password).roles(DEFAULT_ROLE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .authorizeRequests().anyRequest().fullyAuthenticated().and()
                .httpBasic().authenticationEntryPoint(authenticationEntryPoint).and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }
}
