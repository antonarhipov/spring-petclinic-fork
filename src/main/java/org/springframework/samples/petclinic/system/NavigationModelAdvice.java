package org.springframework.samples.petclinic.system;

import java.security.Principal;

import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class NavigationModelAdvice {

	@ModelAttribute
	void navigation(Principal principal, Model model) {
		if (!(principal instanceof Authentication authentication) || !authentication.isAuthenticated()) {
			return;
		}
		model.addAttribute("navigationUsername", authentication.getName());
		authentication.getAuthorities()
			.stream()
			.map(authority -> authority.getAuthority())
			.filter(authority -> authority.startsWith("ROLE_"))
			.map(authority -> authority.substring("ROLE_".length()))
			.findFirst()
			.ifPresent(role -> model.addAttribute("navigationRole", role));
	}

}
