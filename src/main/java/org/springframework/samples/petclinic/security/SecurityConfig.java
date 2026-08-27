package org.springframework.samples.petclinic.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final CustomAuthenticationSuccessHandler successHandler;

	private final CustomAuthenticationFailureHandler failureHandler;

	public SecurityConfig(CustomAuthenticationSuccessHandler successHandler,
			CustomAuthenticationFailureHandler failureHandler) {
		this.successHandler = successHandler;
		this.failureHandler = failureHandler;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, SessionRegistry sessionRegistry)
			throws Exception {

		http.authorizeHttpRequests(authorize -> authorize
			// Public routes
			.requestMatchers("/", "/login", "/oups", "/resources/**", "/webjars/**", "/error",
					"/actuator/health/liveness", "/actuator/health/readiness", "/guidance/urgent-care", "/vets.html",
					"/vets.json", "/vets")
			.permitAll()

			// Staff-only routes
			.requestMatchers("/owners/**", "/vets/manage/**", "/staff/**", "/admin/**", "/scheduling/admin/**",
					"/scheduling/staff/**")
			.hasRole("STAFF")

			// Owner-only routes
			.requestMatchers("/my-profile/**", "/my-pets/**")
			.hasRole("OWNER")

			// Authenticated routes (either OWNER or STAFF)
			.requestMatchers("/change-password", "/notifications/**", "/scheduling/notifications/**",
					"/scheduling/requests/**", "/scheduling/appointments/**")
			.authenticated()

			// Any other route requires authentication
			.anyRequest()
			.authenticated())
			.formLogin(form -> form.loginPage("/login")
				.successHandler(successHandler)
				.failureHandler(failureHandler)
				.permitAll())
			.logout(logout -> logout.logoutUrl("/logout")
				.logoutSuccessUrl("/")
				.invalidateHttpSession(true)
				.deleteCookies("JSESSIONID")
				.permitAll())
			.sessionManagement(session -> session.sessionFixation()
				.migrateSession()
				.maximumSessions(-1)
				.sessionRegistry(sessionRegistry))
			.addFilterAfter(new ForcedPasswordChangeFilter(), AuthorizationFilter.class);

		return http.build();
	}

	@Bean
	public SecurityExpressionHandler<FilterInvocation> filterInvocationSecurityExpressionHandler() {
		return new FilterInvocationSecurityExpressionHandler();
	}

}
