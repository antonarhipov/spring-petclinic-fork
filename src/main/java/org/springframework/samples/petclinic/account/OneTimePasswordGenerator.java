package org.springframework.samples.petclinic.account;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class OneTimePasswordGenerator {

	static final int LENGTH = 12;

	private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

	private final SecureRandom random = new SecureRandom();

	public String generate() {
		char[] chars = new char[LENGTH];
		for (int i = 0; i < chars.length; i++) {
			chars[i] = ALPHABET[this.random.nextInt(ALPHABET.length)];
		}
		return new String(chars);
	}

}
