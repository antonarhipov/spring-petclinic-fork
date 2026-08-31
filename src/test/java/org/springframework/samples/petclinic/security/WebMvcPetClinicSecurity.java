package org.springframework.samples.petclinic.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Shared WebMvc slice support for Spring Security wiring introduced in Phase 2.
 * <p>
 * {@link SecurityConfiguration} registers {@link InteractiveSessionFilter}, which needs a
 * {@link SessionVersionService}. {@code @WebMvcTest} does not load {@code @Service}
 * beans, so this annotation supplies a mock collaborator and an authenticated principal
 * for routes that require login under {@code anyRequest().authenticated()}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@MockitoBean(types = SessionVersionService.class)
@WithMockUser(roles = "STAFF")
public @interface WebMvcPetClinicSecurity {

}
