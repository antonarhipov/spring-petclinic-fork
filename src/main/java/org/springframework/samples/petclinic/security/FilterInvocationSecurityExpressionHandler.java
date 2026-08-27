package org.springframework.samples.petclinic.security;

import org.springframework.security.access.expression.AbstractSecurityExpressionHandler;
import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.security.access.expression.SecurityExpressionOperations;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.access.expression.WebSecurityExpressionRoot;

/**
 * Thymeleaf Spring Security dialect still resolves
 * {@code SecurityExpressionHandler&lt;FilterInvocation&gt;} for {@code sec:authorize}
 * expressions. Spring Security 7 no longer ships that handler, so this adapter restores
 * the FilterInvocation-based evaluation path used by layout templates.
 */
public class FilterInvocationSecurityExpressionHandler extends AbstractSecurityExpressionHandler<FilterInvocation>
		implements SecurityExpressionHandler<FilterInvocation> {

	private AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

	private String defaultRolePrefix = "ROLE_";

	@Override
	protected SecurityExpressionOperations createSecurityExpressionRoot(Authentication authentication,
			FilterInvocation invocation) {
		WebSecurityExpressionRoot root = new WebSecurityExpressionRoot(authentication, invocation);
		root.setPermissionEvaluator(getPermissionEvaluator());
		root.setTrustResolver(this.trustResolver);
		root.setRoleHierarchy(getRoleHierarchy());
		root.setDefaultRolePrefix(this.defaultRolePrefix);
		return root;
	}

	public void setTrustResolver(AuthenticationTrustResolver trustResolver) {
		this.trustResolver = trustResolver;
	}

	public void setDefaultRolePrefix(String defaultRolePrefix) {
		this.defaultRolePrefix = defaultRolePrefix;
	}

}
