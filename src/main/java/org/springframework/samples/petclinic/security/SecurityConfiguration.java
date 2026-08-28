package org.springframework.samples.petclinic.security;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

	@Bean
	Clock schedulingClock() {
		return Clock.systemUTC();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain applicationSecurity(HttpSecurity http, AuthenticationSuccessHandler successHandler,
			AuthenticationFailureHandler failureHandler, TemporaryPasswordFilter temporaryPasswordFilter)
			throws Exception {
		http.authorizeHttpRequests(authorize -> authorize
			.requestMatchers("/", "/login", "/resources/**", "/webjars/**", "/actuator/health", "/error", "/oups")
			.permitAll()
			.requestMatchers("/password/change")
			.authenticated()
			.requestMatchers("/my/**")
			.hasRole("OWNER")
			.requestMatchers("/staff/**", "/owners/**", "/vets", "/vets.html")
			.hasRole("STAFF")
			.anyRequest()
			.authenticated())
			.formLogin(login -> login.loginPage("/login")
				.successHandler(successHandler)
				.failureHandler(failureHandler)
				.permitAll())
			.logout(logout -> logout.logoutSuccessUrl("/").permitAll())
			.sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId())
				.invalidSessionUrl("/login?expired"))
			.exceptionHandling(
					handling -> handling.accessDeniedHandler((request, response, exception) -> response.sendError(403)))
			.addFilterAfter(temporaryPasswordFilter, AnonymousAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	AuthenticationSuccessHandler authenticationSuccessHandler(AccountService accounts) {
		return (request, response, authentication) -> {
			accounts.signInSucceeded(authentication.getName());
			org.springframework.security.core.context.SecurityContext context = org.springframework.security.core.context.SecurityContextHolder
				.createEmptyContext();
			context.setAuthentication(authentication);
			request.getSession(true)
				.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
			response.sendRedirect(hasRole(authentication, "ROLE_STAFF") ? "/staff/calendar" : "/my/appointments");
		};
	}

	@Bean
	AuthenticationFailureHandler authenticationFailureHandler(AccountService accounts) {
		return (request, response, exception) -> {
			accounts.signInFailed(request.getParameter("username"));
			response.sendRedirect("/login?error");
		};
	}

	private boolean hasRole(org.springframework.security.core.Authentication authentication, String role) {
		return authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals(role));
	}

}
