package org.springframework.samples.petclinic.security;

import static org.mockito.Mockito.mock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;

/**
 * Registers the Thymeleaf Spring Security dialect for {@code @WebMvcTest} slices and
 * imports {@link SecurityConfig} so layout {@code sec:authorize} expressions resolve via
 * the production FilterInvocation expression handler.
 */
@TestConfiguration
@Import(SecurityConfig.class)
public class WebMvcSecurityTestConfig {

	@Bean
	public SpringSecurityDialect springSecurityDialect() {
		return new SpringSecurityDialect();
	}

	@Bean
	public CustomAuthenticationSuccessHandler successHandler() {
		return mock(CustomAuthenticationSuccessHandler.class);
	}

	@Bean
	public CustomAuthenticationFailureHandler failureHandler() {
		return mock(CustomAuthenticationFailureHandler.class);
	}

	@Bean
	public SessionRegistry sessionRegistry() {
		return new SessionRegistryImpl();
	}

}
