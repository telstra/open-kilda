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

import static org.springframework.security.config.Customizer.withDefaults;

import org.openkilda.constants.IConstants.SamlUrl;
import org.openkilda.saml.dao.entity.SamlConfigEntity;
import org.openkilda.saml.provider.DbMetadataProvider;
import org.openkilda.saml.provider.UrlMetadataProvider;
import org.openkilda.saml.repository.SamlRepository;
import org.openkilda.utility.StringUtil;

import lombok.Getter;
import lombok.Setter;

import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml4AuthenticationProvider;
import org.springframework.security.saml2.provider.service.authentication.OpenSamlAuthenticationProvider;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.saml2.provider.service.metadata.OpenSamlMetadataResolver;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.servlet.filter.Saml2WebSsoAuthenticationFilter;
import org.springframework.security.saml2.provider.service.web.DefaultRelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.web.RelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.web.Saml2MetadataFilter;
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
      .and()
//                .addFilterBefore(metadataGeneratorFilter(), ChannelProcessingFilter.class)
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
        List<AuthenticationProvider> authProviderList = new ArrayList<>();
        authProviderList.add(authProvider());
        authProviderList.add(samlAuthenticationProvider());
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



//
//    @Bean
//    public WebSSOProfileOptions defaultWebSsoProfileOptions() {
//        WebSSOProfileOptions webSsoProfileOptions = new WebSSOProfileOptions();
//        webSsoProfileOptions.setIncludeScoping(false);
//        return webSsoProfileOptions;
//    }
//
//    @Bean
//    public SAMLEntryPoint samlEntryPoint() {
//        SAMLEntryPoint samlEntryPoint = new SAMLEntryPoint();
//        samlEntryPoint.setDefaultProfileOptions(defaultWebSsoProfileOptions());
//        return samlEntryPoint;
//    }
//
//    @Bean
//    public MetadataDisplayFilter metadataDisplayFilter() {
//        return new MetadataDisplayFilter();
//    }
//
//    @Bean
//    public SimpleUrlAuthenticationFailureHandler authenticationFailureHandler() {
//        return new SimpleUrlAuthenticationFailureHandler();
//    }
//
//    @Bean
//    public SavedRequestAwareAuthenticationSuccessHandler successRedirectHandler() {
//        SavedRequestAwareAuthenticationSuccessHandler successRedirectHandler =
//                new SavedRequestAwareAuthenticationSuccessHandler();
//        successRedirectHandler.setDefaultTargetUrl(SamlUrl.SAML_AUTHENTICATE);
//        return successRedirectHandler;
//    }
//
//    @Bean
//    public SAMLProcessingFilter samlWebSsoProcessingFilter() throws Exception {
//        SAMLProcessingFilter samlWebSsoProcessingFilter = new SAMLProcessingFilter();
//        samlWebSsoProcessingFilter.setAuthenticationManager(authenticationManager());
//        samlWebSsoProcessingFilter.setAuthenticationSuccessHandler(successRedirectHandler());
//        samlWebSsoProcessingFilter.setAuthenticationFailureHandler(authenticationFailureHandler());
//        return samlWebSsoProcessingFilter;
//    }
//

//    @Autowired
//    private RelyingPartyRegistrationRepository relyingPartyRegistrationRepository = new InMemoryRelyingPartyRegistrationRepository(
//        RelyingPartyRegistration.withRegistrationId("registrationId")
//                .entityId("relyingPartyEntityId")
//        		.assertionConsumerServiceLocation("assertingConsumerServiceLocation")
//
////                .signingX509Credentials((c) -&gt; c.add(relyingPartySigningCredential))
////            .assertingPartyDetails((details) details
//// 	               .entityId("assertingPartyEntityId"));
//// 	.singleSignOnServiceLocation(singleSignOnServiceLocation))
////            * 				.verifyingX509Credentials((c) -&gt; c.add(assertingPartyVerificationCredential))
//            .build()
//);
//    @Bean
//    public Saml2MetadataFilter metadataGeneratorFilter() {
//        RelyingPartyRegistrationResolver relyingPartyRegistrationResolver = new DefaultRelyingPartyRegistrationResolver(
//                        relyingPartyRegistrationRepository);
//        return new Saml2MetadataFilter(relyingPartyRegistrationResolver, new OpenSamlMetadataResolver());
//    }

    @Bean
    public OpenSamlAuthenticationProvider samlAuthenticationProvider() {
        OpenSamlAuthenticationProvider authenticationProvider = new OpenSamlAuthenticationProvider();
        authenticationProvider.setAssertionValidator(OpenSamlAuthenticationProvider.createDefaultAssertionValidator());
        authenticationProvider.setResponseAuthenticationConverter(OpenSamlAuthenticationProvider.createDefaultResponseAuthenticationConverter());

        return authenticationProvider;
    }

//    @Bean
//    public MetadataGenerator metadataGenerator() {
//        MetadataGenerator metadataGenerator = new MetadataGenerator();
//        metadataGenerator.setEntityId("openkilda");
//        metadataGenerator.setExtendedMetadata(extendedMetadata());
//        metadataGenerator.setIncludeDiscoveryExtension(false);
//        metadataGenerator.setKeyManager(keyManager());
//        return metadataGenerator;
//    }


//    @Bean
//    public KeyManager keyManager() {
//        String storePass = getSecret();
//        Map<String, String> passwords = new HashMap<>();
//        passwords.put(alias, getSecret());
//        return new JKSKeyManager(keyStore, storePass, passwords, alias);
//    }
//
//    @Bean
//    public ExtendedMetadata extendedMetadata() {
//        ExtendedMetadata extendedMetadata = new ExtendedMetadata();
//        extendedMetadata.setIdpDiscoveryEnabled(false);
//        extendedMetadata.setSignMetadata(false);
//        return extendedMetadata;
//    }
//
//    @Bean
//    public FilterChainProxy samlFilter() throws Exception {
//        List<SecurityFilterChain> chains = new ArrayList<>();
//
//        chains.add(new DefaultSecurityFilterChain(new AntPathRequestMatcher(contextPath + SamlUrl.SAML_METADATA),
//                metadataDisplayFilter()));
//
//        chains.add(new DefaultSecurityFilterChain(new AntPathRequestMatcher(contextPath + SamlUrl.SAML_LOGIN),
//                samlEntryPoint()));
//
//        chains.add(new DefaultSecurityFilterChain(new AntPathRequestMatcher(contextPath + SamlUrl.SAML_SSO),
//                samlWebSsoProcessingFilter()));
//
//        chains.add(new DefaultSecurityFilterChain(new AntPathRequestMatcher(contextPath + SamlUrl.SAML_LOGOUT),
//                samlLogoutFilter()));
//
//        return new FilterChainProxy(chains);
//    }
//
//    @Bean
//    public SimpleUrlLogoutSuccessHandler successLogoutHandler() {
//        SimpleUrlLogoutSuccessHandler successLogoutHandler = new SimpleUrlLogoutSuccessHandler();
//        return successLogoutHandler;
//    }
//
//    // Logout handler terminating local session
//    @Bean
//    public SecurityContextLogoutHandler logoutHandler() {
//        SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
//        logoutHandler.setInvalidateHttpSession(true);
//        logoutHandler.setClearAuthentication(true);
//        return logoutHandler;
//    }
//
//    @Bean
//    public SAMLLogoutFilter samlLogoutFilter() {
//        return new SAMLLogoutFilter(successLogoutHandler(), new LogoutHandler[] { logoutHandler() },
//                new LogoutHandler[] { logoutHandler() });
//    }
//
//    @Bean
//    public VelocityEngine velocityEngine() {
//        return VelocityFactory.getEngine();
//    }
//
//    @Bean(initMethod = "initialize")
//    public StaticBasicParserPool parserPool() {
//        return new StaticBasicParserPool();
//    }
//
//    @Bean(name = "parserPoolHolder")
//    public ParserPoolHolder parserPoolHolder() {
//        return new ParserPoolHolder();
//    }
//
//    @Bean
//    public HTTPPostBinding httpPostBinding() {
//        return new HTTPPostBinding(parserPool(), velocityEngine());
//    }
//
//    @Bean
//    public HTTPRedirectDeflateBinding httpRedirectDeflateBinding() {
//        return new HTTPRedirectDeflateBinding(parserPool());
//    }
//
//    @Bean
//    public SAMLProcessorImpl processor() {
//        Collection<SAMLBinding> bindings = new ArrayList<>();
//        bindings.add(httpRedirectDeflateBinding());
//        bindings.add(httpPostBinding());
//        return new SAMLProcessorImpl(bindings);
//    }
//
//    @Bean
//    @Qualifier("webSSOprofileConsumer")
//    public WebSSOProfileConsumer webSsoProfileConsumer() {
//        return new WebSSOProfileConsumerImpl();
//    }
//
//    @Bean
//    @Qualifier("webSSOprofile")
//    public WebSSOProfile webSsoProfile() {
//        return new WebSSOProfileImpl();
//    }
//
//    @Bean
//    @Qualifier("hokWebSSOprofileConsumer")
//    public WebSSOProfileConsumerHoKImpl hokWebSsoProfileConsumer() {
//        return new WebSSOProfileConsumerHoKImpl();
//    }
//
//
//    @Bean
//    public HttpClient httpClient() throws IOException {
//        return new HttpClient(multiThreadedHttpConnectionManager());
//    }
//
//    @Bean
//    public MultiThreadedHttpConnectionManager multiThreadedHttpConnectionManager() {
//        return new MultiThreadedHttpConnectionManager();
//    }
//
//    @Bean
//    public static SAMLBootstrap samlBootstrap() {
//        return new SAMLBootstrap();
//    }
//
//    @Bean
//    public SAMLDefaultLogger samlLogger() {
//        return new SAMLDefaultLogger();
//    }
//
//    @Bean
//    public SAMLContextProviderImpl contextProvider() {
//        SAMLContextProviderImpl provider = new SAMLContextProviderImpl();
//        provider.setStorageFactory(new EmptyStorageFactory());
//        return provider;
//    }
//
//    @Bean
//    public SingleLogoutProfile logoutProfile() {
//        return new SingleLogoutProfileImpl();
//    }
//
//    @Bean
//    @Qualifier("metadata")
//    public CachingMetadataManager metadata(ExtendedMetadataDelegate extendedMetadataDelegate)
//            throws MetadataProviderException, IOException {
//        List<MetadataProvider> metadataProviderList = new ArrayList<>();
//        List<SamlConfigEntity> samlConfigEntityList = samlRepository.findAll();
//        if (samlConfigEntityList != null) {
//            for (final SamlConfigEntity samlConfigEntity : samlConfigEntityList) {
//                if (samlConfigEntity.getUrl() != null) {
//                    UrlMetadataProvider urlMetadataProvider = new UrlMetadataProvider(new Timer(true),
//                            new HttpClient(), samlConfigEntity.getUuid());
//                    urlMetadataProvider.setParserPool(ParserPoolHolder.getPool());
//                    ExtendedMetadataDelegate metadataDelegate = new ExtendedMetadataDelegate(urlMetadataProvider,
//                            extendedMetadata());
//                    metadataDelegate.setMetadataTrustCheck(false);
//                    metadataDelegate.setMetadataRequireSignature(false);
//                    metadataProviderList.add(metadataDelegate);
//                } else {
//                    DbMetadataProvider metadataProvider = new DbMetadataProvider(new Timer(true),
//                            samlConfigEntity.getUuid());
//                    metadataProvider.setParserPool(ParserPoolHolder.getPool());
//                    ExtendedMetadataDelegate metadataDelegate = new ExtendedMetadataDelegate(metadataProvider,
//                            extendedMetadata());
//                    metadataDelegate.setMetadataTrustCheck(false);
//                    metadataDelegate.setMetadataRequireSignature(false);
//                    metadataProviderList.add(metadataDelegate);
//                }
//            }
//        }
//        return new CachingMetadataManager(metadataProviderList);
//    }
//
//    @Bean
//    @Qualifier("idp-ssocircle")
//    public ExtendedMetadataDelegate ssoCircleExtendedMetadataProvider()
//            throws MetadataProviderException {
//        DbMetadataProvider provider = new DbMetadataProvider();
//        ExtendedMetadataDelegate extendedMetadataDelegate =
//                new ExtendedMetadataDelegate(provider, extendedMetadata());
//        extendedMetadataDelegate.setMetadataTrustCheck(false);
//        extendedMetadataDelegate.setMetadataRequireSignature(false);
//        return extendedMetadataDelegate;
//    }
//


}
