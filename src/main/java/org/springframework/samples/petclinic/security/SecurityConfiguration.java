package org.springframework.samples.petclinic.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

	private final AuthenticationSuccessHandler authenticationSuccessHandler;

	private final InteractiveSessionFilter interactiveSessionFilter;

	public SecurityConfiguration(AuthenticationSuccessHandler authenticationSuccessHandler,
			InteractiveSessionFilter interactiveSessionFilter) {
		this.authenticationSuccessHandler = authenticationSuccessHandler;
		this.interactiveSessionFilter = interactiveSessionFilter;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(authorize -> authorize
			.requestMatchers("/", "/login", "/logout", "/public/**", "/auth/**", "/account/**", "/session/**",
					"/resources/**", "/webjars/**", "/favicon.ico", "/error", "/oups")
			.permitAll()
			.requestMatchers("/owner/**")
			.hasRole("OWNER")
			.requestMatchers("/staff/**")
			.hasRole("STAFF")
			.anyRequest()
			.authenticated())
			.formLogin(form -> form.loginPage("/auth/login")
				.loginProcessingUrl("/auth/login")
				.successHandler(this.authenticationSuccessHandler)
				.failureUrl("/auth/login?error=true")
				.permitAll())
			.logout(logout -> logout.logoutUrl("/auth/logout")
				.logoutSuccessUrl("/auth/login?logout=true")
				.invalidateHttpSession(true)
				.clearAuthentication(true)
				.deleteCookies("JSESSIONID")
				.permitAll())
			.sessionManagement(session -> session.invalidSessionUrl("/auth/login?expired=true"))
			.addFilterBefore(this.interactiveSessionFilter, AuthorizationFilter.class);

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
