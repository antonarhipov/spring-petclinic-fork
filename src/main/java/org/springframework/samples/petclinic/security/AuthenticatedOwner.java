package org.springframework.samples.petclinic.security;

import org.springframework.samples.petclinic.owner.Owner;

public record AuthenticatedOwner(Account account, Owner owner) {
}
