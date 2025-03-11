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

import org.openkilda.constants.IConstants.SamlUrl;
import org.openkilda.saml.dao.entity.SamlConfigEntity;
import org.openkilda.saml.provider.DbMetadataProvider;
import org.openkilda.saml.provider.UrlMetadataProvider;
import org.openkilda.saml.repository.SamlRepository;
import org.openkilda.utility.StringUtil;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.velocity.app.VelocityEngine;
import org.opensaml.saml2.metadata.provider.MetadataProvider;
import org.opensaml.saml2.metadata.provider.MetadataProviderException;
import org.opensaml.xml.parse.StaticBasicParserPool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.saml.SAMLAuthenticationProvider;
import org.springframework.security.saml.SAMLBootstrap;
import org.springframework.security.saml.SAMLEntryPoint;
import org.springframework.security.saml.SAMLLogoutFilter;
import org.springframework.security.saml.SAMLProcessingFilter;
import org.springframework.security.saml.context.SAMLContextProviderImpl;
import org.springframework.security.saml.key.JKSKeyManager;
import org.springframework.security.saml.key.KeyManager;
import org.springframework.security.saml.log.SAMLDefaultLogger;
import org.springframework.security.saml.metadata.CachingMetadataManager;
import org.springframework.security.saml.metadata.ExtendedMetadata;
import org.springframework.security.saml.metadata.ExtendedMetadataDelegate;
import org.springframework.security.saml.metadata.MetadataDisplayFilter;
import org.springframework.security.saml.metadata.MetadataGenerator;
import org.springframework.security.saml.metadata.MetadataGeneratorFilter;
import org.springframework.security.saml.parser.ParserPoolHolder;
import org.springframework.security.saml.processor.HTTPPostBinding;
import org.springframework.security.saml.processor.HTTPRedirectDeflateBinding;
import org.springframework.security.saml.processor.SAMLBinding;
import org.springframework.security.saml.processor.SAMLProcessorImpl;
import org.springframework.security.saml.storage.EmptyStorageFactory;
import org.springframework.security.saml.util.VelocityFactory;
import org.springframework.security.saml.websso.SingleLogoutProfile;
import org.springframework.security.saml.websso.SingleLogoutProfileImpl;
import org.springframework.security.saml.websso.WebSSOProfile;
import org.springframework.security.saml.websso.WebSSOProfileConsumer;
import org.springframework.security.saml.websso.WebSSOProfileConsumerHoKImpl;
import org.springframework.security.saml.websso.WebSSOProfileConsumerImpl;
import org.springframework.security.saml.websso.WebSSOProfileImpl;
import org.springframework.security.saml.websso.WebSSOProfileOptions;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.channel.ChannelProcessingFilter;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.usermanagement.service.UserService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;

/**
 * The Class SecurityConfig : used to configure security, authenticationManager and authProvider.
 *
 * @author Gaurav Chugh
 */
@Configuration
@DependsOn("ApplicationContextProvider")
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Value("${server.contextPath}")
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

    @Autowired
    private SamlRepository samlRepository;

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
        http.csrf().ignoringAntMatchers("/saml/**")
        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()).and()
        .authorizeRequests()
        .antMatchers("/login", "/authenticate", "/saml/authenticate", "/forgotpassword", "/401", "/saml/login/**",
             "/saml/metadata/**", "/saml/SSO/**")
      .permitAll()
      .and().addFilterBefore(metadataGeneratorFilter(), ChannelProcessingFilter.class)
      .exceptionHandling().accessDeniedHandler(accessDeniedHandler).and()
      .headers().addHeaderWriter(new StaticHeadersWriter("X-Content-Security-Policy", "default-src 'self'"))
      .addHeaderWriter(new StaticHeadersWriter("Feature-Policy", "none"))
      .addHeaderWriter(new StaticHeadersWriter("Referrer-Policy", "same-origin"))
      .and().sessionManagement().invalidSessionUrl("/401");

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
        authProviderList.add(samlAuthenticationProvider());
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

    @Bean
    public WebSSOProfileOptions defaultWebSsoProfileOptions() {
        WebSSOProfileOptions webSsoProfileOptions = new WebSSOProfileOptions();
        webSsoProfileOptions.setIncludeScoping(false);
        return webSsoProfileOptions;
    }

    @Bean
    public SAMLEntryPoint samlEntryPoint() {
        SAMLEntryPoint samlEntryPoint = new SAMLEntryPoint();
        samlEntryPoint.setDefaultProfileOptions(defaultWebSsoProfileOptions());
        return samlEntryPoint;
    }

    @Bean
    public MetadataDisplayFilter metadataDisplayFilter() {
        return new MetadataDisplayFilter();
    }

    @Bean
    public SimpleUrlAuthenticationFailureHandler authenticationFailureHandler() {
        return new SimpleUrlAuthenticationFailureHandler();
    }

    @Bean
    public SavedRequestAwareAuthenticationSuccessHandler successRedirectHandler() {
        SavedRequestAwareAuthenticationSuccessHandler successRedirectHandler =
                new SavedRequestAwareAuthenticationSuccessHandler();
        successRedirectHandler.setDefaultTargetUrl(SamlUrl.SAML_AUTHENTICATE);
        return successRedirectHandler;
    }

    @Bean
    public SAMLProcessingFilter samlWebSsoProcessingFilter() throws Exception {
        SAMLProcessingFilter samlWebSsoProcessingFilter = new SAMLProcessingFilter();
        samlWebSsoProcessingFilter.setAuthenticationManager(authenticationManager());
        samlWebSsoProcessingFilter.setAuthenticationSuccessHandler(successRedirectHandler());
        samlWebSsoProcessingFilter.setAuthenticationFailureHandler(authenticationFailureHandler());
        return samlWebSsoProcessingFilter;
    }

    @Bean
    public MetadataGeneratorFilter metadataGeneratorFilter() {
        return new MetadataGeneratorFilter(metadataGenerator());
    }

    @Bean
    public MetadataGenerator metadataGenerator() {
        MetadataGenerator metadataGenerator = new MetadataGenerator();
        metadataGenerator.setEntityId("openkilda");
        metadataGenerator.setExtendedMetadata(extendedMetadata());
        metadataGenerator.setIncludeDiscoveryExtension(false);
        metadataGenerator.setKeyManager(keyManager());
        return metadataGenerator;
    }

    @Bean
    public KeyManager keyManager() {
        String storePass = getSecret();
        Map<String, String> passwords = new HashMap<>();
        passwords.put(alias, getSecret());
        return new JKSKeyManager(keyStore, storePass, passwords, alias);
    }

    @Bean
    public ExtendedMetadata extendedMetadata() {
        ExtendedMetadata extendedMetadata = new ExtendedMetadata();
        extendedMetadata.setIdpDiscoveryEnabled(false);
        extendedMetadata.setSignMetadata(false);
        return extendedMetadata;
    }

    @Bean
    public FilterChainProxy samlFilter() throws Exception {
        List<SecurityFilterChain> chains = new ArrayList<>();

        chains.add(new DefaultSecurityFilterChain(new AntPathRequestMatcher(contextPath + SamlUrl.SAML_METADATA),
                metadataDisplayFilter()));

        chains.add(new DefaultSecurityFilterChain(new AntPathRequestMatcher(contextPath + SamlUrl.SAML_LOGIN),
                samlEntryPoint()));

        chains.add(new DefaultSecurityFilterChain(new AntPathRequestMatcher(contextPath + SamlUrl.SAML_SSO),
                samlWebSsoProcessingFilter()));

        chains.add(new DefaultSecurityFilterChain(new AntPathRequestMatcher(contextPath + SamlUrl.SAML_LOGOUT),
                samlLogoutFilter()));

        return new FilterChainProxy(chains);
    }

    @Bean
    public SimpleUrlLogoutSuccessHandler successLogoutHandler() {
        SimpleUrlLogoutSuccessHandler successLogoutHandler = new SimpleUrlLogoutSuccessHandler();
        return successLogoutHandler;
    }

    // Logout handler terminating local session
    @Bean
    public SecurityContextLogoutHandler logoutHandler() {
        SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
        logoutHandler.setInvalidateHttpSession(true);
        logoutHandler.setClearAuthentication(true);
        return logoutHandler;
    }

    @Bean
    public SAMLLogoutFilter samlLogoutFilter() {
        return new SAMLLogoutFilter(successLogoutHandler(), new LogoutHandler[]{logoutHandler()},
                new LogoutHandler[]{logoutHandler()});
    }

    @Bean
    public VelocityEngine velocityEngine() {
        return VelocityFactory.getEngine();
    }

    @Bean(initMethod = "initialize")
    public StaticBasicParserPool parserPool() {
        return new StaticBasicParserPool();
    }

    @Bean(name = "parserPoolHolder")
    public ParserPoolHolder parserPoolHolder() {
        return new ParserPoolHolder();
    }

    @Bean
    public HTTPPostBinding httpPostBinding() {
        return new HTTPPostBinding(parserPool(), velocityEngine());
    }

    @Bean
    public HTTPRedirectDeflateBinding httpRedirectDeflateBinding() {
        return new HTTPRedirectDeflateBinding(parserPool());
    }

    @Bean
    public SAMLProcessorImpl processor() {
        Collection<SAMLBinding> bindings = new ArrayList<>();
        bindings.add(httpRedirectDeflateBinding());
        bindings.add(httpPostBinding());
        return new SAMLProcessorImpl(bindings);
    }

    @Bean
    @Qualifier("webSSOprofileConsumer")
    public WebSSOProfileConsumer webSsoProfileConsumer() {
        return new WebSSOProfileConsumerImpl();
    }

    @Bean
    @Qualifier("webSSOprofile")
    public WebSSOProfile webSsoProfile() {
        return new WebSSOProfileImpl();
    }

    @Bean
    @Qualifier("hokWebSSOprofileConsumer")
    public WebSSOProfileConsumerHoKImpl hokWebSsoProfileConsumer() {
        return new WebSSOProfileConsumerHoKImpl();
    }


    @Bean
    public HttpClient httpClient() throws IOException {
        return new HttpClient(multiThreadedHttpConnectionManager());
    }

    @Bean
    public MultiThreadedHttpConnectionManager multiThreadedHttpConnectionManager() {
        return new MultiThreadedHttpConnectionManager();
    }

    @Bean
    public static SAMLBootstrap samlBootstrap() {
        return new SAMLBootstrap();
    }

    @Bean
    public SAMLDefaultLogger samlLogger() {
        return new SAMLDefaultLogger();
    }

    @Bean
    public SAMLContextProviderImpl contextProvider() {
        SAMLContextProviderImpl provider = new SAMLContextProviderImpl();
        provider.setStorageFactory(new EmptyStorageFactory());
        return provider;
    }

    @Bean
    public SingleLogoutProfile logoutProfile() {
        return new SingleLogoutProfileImpl();
    }

    @Bean
    @Qualifier("metadata")
    public CachingMetadataManager metadata(ExtendedMetadataDelegate extendedMetadataDelegate)
            throws MetadataProviderException, IOException {
        List<MetadataProvider> metadataProviderList = new ArrayList<>();
        List<SamlConfigEntity> samlConfigEntityList = samlRepository.findAll();
        if (samlConfigEntityList != null) {
            for (final SamlConfigEntity samlConfigEntity : samlConfigEntityList) {
                if (samlConfigEntity.getUrl() != null) {
                    UrlMetadataProvider urlMetadataProvider = new UrlMetadataProvider(new Timer(true),
                            new HttpClient(), samlConfigEntity.getUuid());
                    urlMetadataProvider.setParserPool(ParserPoolHolder.getPool());
                    ExtendedMetadataDelegate metadataDelegate = new ExtendedMetadataDelegate(urlMetadataProvider,
                            extendedMetadata());
                    metadataDelegate.setMetadataTrustCheck(false);
                    metadataDelegate.setMetadataRequireSignature(false);
                    metadataProviderList.add(metadataDelegate);
                } else {
                    DbMetadataProvider metadataProvider = new DbMetadataProvider(new Timer(true),
                            samlConfigEntity.getUuid());
                    metadataProvider.setParserPool(ParserPoolHolder.getPool());
                    ExtendedMetadataDelegate metadataDelegate = new ExtendedMetadataDelegate(metadataProvider,
                            extendedMetadata());
                    metadataDelegate.setMetadataTrustCheck(false);
                    metadataDelegate.setMetadataRequireSignature(false);
                    metadataProviderList.add(metadataDelegate);
                }
            }
        }
        return new CachingMetadataManager(metadataProviderList);
    }

    @Bean
    @Qualifier("idp-ssocircle")
    public ExtendedMetadataDelegate ssoCircleExtendedMetadataProvider()
            throws MetadataProviderException {
        DbMetadataProvider provider = new DbMetadataProvider();
        ExtendedMetadataDelegate extendedMetadataDelegate =
                new ExtendedMetadataDelegate(provider, extendedMetadata());
        extendedMetadataDelegate.setMetadataTrustCheck(false);
        extendedMetadataDelegate.setMetadataRequireSignature(false);
        return extendedMetadataDelegate;
    }

    @Bean
    public SAMLAuthenticationProvider samlAuthenticationProvider() {
        SAMLAuthenticationProvider samlAuthenticationProvider = new SAMLAuthenticationProvider();
        samlAuthenticationProvider.setForcePrincipalAsString(false);
        return samlAuthenticationProvider;
    }

}
