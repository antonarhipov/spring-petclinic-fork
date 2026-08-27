package org.springframework.samples.petclinic.security;

import java.security.Principal;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/accounts")
public class AccountManagementController {

	private final AccountService accountService;

	public AccountManagementController(AccountService accountService) {
		this.accountService = accountService;
	}

	@GetMapping
	public String listAccounts(Model model) {
		List<Account> accounts = accountService.findAll();
		model.addAttribute("accounts", accounts);
		return "security/accounts";
	}

	@PostMapping("/staff")
	public String createStaffAccount(@RequestParam("username") String username,
			@RequestParam("password") String password, Principal principal, RedirectAttributes redirectAttributes) {
		try {
			accountService.createStaffAccount(username, password, principal.getName(), "STAFF");
			redirectAttributes.addFlashAttribute("message", "Staff account created successfully");
		}
		catch (Exception e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/accounts";
	}

	@PostMapping("/owner")
	public String createOwnerAccount(@RequestParam("username") String username,
			@RequestParam("password") String password, @RequestParam("ownerId") Integer ownerId, Principal principal,
			RedirectAttributes redirectAttributes) {
		try {
			accountService.createOwnerAccount(username, password, ownerId, principal.getName(), "STAFF");
			redirectAttributes.addFlashAttribute("message", "Owner account created successfully");
		}
		catch (Exception e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/accounts";
	}

	@PostMapping("/{id}/reset-password")
	public String resetPassword(@PathVariable("id") Integer id, @RequestParam("password") String password,
			Principal principal, RedirectAttributes redirectAttributes) {
		try {
			accountService.resetPassword(id, password, principal.getName(), "STAFF");
			redirectAttributes.addFlashAttribute("message", "Password reset successfully");
		}
		catch (Exception e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/accounts";
	}

	@PostMapping("/{id}/rename")
	public String renameAccount(@PathVariable("id") Integer id, @RequestParam("newUsername") String newUsername,
			Principal principal, RedirectAttributes redirectAttributes) {
		try {
			accountService.renameAccount(id, newUsername, principal.getName(), "STAFF");
			redirectAttributes.addFlashAttribute("message", "Account renamed successfully");
		}
		catch (Exception e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/accounts";
	}

	@PostMapping("/{id}/deactivate")
	public String deactivateAccount(@PathVariable("id") Integer id, Principal principal,
			RedirectAttributes redirectAttributes) {
		try {
			accountService.deactivateAccount(id, principal.getName(), "STAFF");
			redirectAttributes.addFlashAttribute("message", "Account deactivated successfully");
		}
		catch (Exception e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/accounts";
	}

	@PostMapping("/{id}/unlock")
	public String unlockAccount(@PathVariable("id") Integer id, Principal principal,
			RedirectAttributes redirectAttributes) {
		try {
			accountService.unlockAccount(id, principal.getName(), "STAFF");
			redirectAttributes.addFlashAttribute("message", "Account unlocked successfully");
		}
		catch (Exception e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/accounts";
	}

}
