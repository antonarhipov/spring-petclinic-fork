package org.springframework.samples.petclinic.security;

import java.util.Arrays;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

	private final Environment environment;

	public LoginController(Environment environment) {
		this.environment = environment;
	}

	@GetMapping("/login")
	public String login(@RequestParam(value = "error", required = false) String error,
			@RequestParam(value = "logout", required = false) String logout, Model model) {

		boolean isDemo = Arrays.asList(environment.getActiveProfiles()).contains("demo-data");
		model.addAttribute("isDemo", isDemo);

		if (error != null) {
			model.addAttribute("errorMessage", "loginError");
		}
		if (logout != null) {
			model.addAttribute("logoutMessage", true);
		}

		return "login";
	}

}
