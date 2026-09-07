package org.springframework.samples.petclinic.system;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.samples.petclinic.scheduling.security.UserAccountRepository;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfFilter;

import jakarta.servlet.http.HttpServletResponse;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, RoleAwareAuthenticationSuccessHandler successHandler)
			throws Exception {
		http.authorizeHttpRequests(authorize -> authorize
			.requestMatchers(HttpMethod.GET, "/login", "/actuator/health", "/favicon.ico", "/resources/**",
					"/webjars/**", "/css/**", "/images/**")
			.permitAll()
			.requestMatchers(HttpMethod.POST, "/login")
			.permitAll()
			.requestMatchers("/my/**")
			.hasRole("OWNER")
			.requestMatchers("/owners/**", "/vets", "/vets.html", "/oups", "/staff/**", "/actuator/**",
					"/h2-console/**")
			.hasRole("STAFF")
			.anyRequest()
			.authenticated());
		http.formLogin(form -> form.loginPage("/login").successHandler(successHandler));
		http.logout(logout -> logout.logoutSuccessUrl("/login"));
		AccessDeniedHandler denied = authenticationAwareAccessDeniedHandler();
		http.exceptionHandling(exceptions -> exceptions.accessDeniedHandler(denied));
		http.csrf(csrf -> csrf.withObjectPostProcessor(new ObjectPostProcessor<CsrfFilter>() {
			@Override
			public <O extends CsrfFilter> O postProcess(O filter) {
				filter.setAccessDeniedHandler(denied);
				return filter;
			}
		}));
		http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
		return http.build();
	}

	@Bean
	UserDetailsService userDetailsService(UserAccountRepository accounts) {
		return username -> accounts.findByUsername(username)
			.map(account -> User.withUsername(account.getUsername())
				.password(account.getPasswordHash())
				.roles(account.getRole().name())
				.build())
			.orElseThrow(
					() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("Unknown user"));
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	private AccessDeniedHandler authenticationAwareAccessDeniedHandler() {
		return (request, response, exception) -> {
			var authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication == null || authentication.getPrincipal().equals("anonymousUser")) {
				response.sendRedirect("/login");
			}
			else {
				response.sendError(HttpServletResponse.SC_FORBIDDEN);
			}
		};
	}

}
