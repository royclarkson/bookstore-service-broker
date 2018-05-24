/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sample.bookstore.web.service;

import java.security.SecureRandom;

import reactor.core.publisher.Mono;

import org.springframework.cloud.sample.bookstore.web.model.User;
import org.springframework.cloud.sample.bookstore.web.repository.UserRepository;
import org.springframework.cloud.sample.bookstore.web.security.SecurityAuthorities;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
	private static final String PASSWORD_CHARS =
			"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
	private static final int PASSWORD_LENGTH = 12;

	private static final SecureRandom RANDOM = new SecureRandom();

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	public Mono<Void> initializeUsers() {
		return userRepository.count()
				.map(count -> {
					if (count > 0) {
						return adminUser()
								.flatMap(user -> userRepository.save(user));
					}
					return Mono.empty();
				})
				.then();
	}

	public Mono<User> createUser(String username, String... authorities) {
		return generatePassword()
				.flatMap(password -> Mono.fromCallable(() -> passwordEncoder.encode(password))
						.flatMap(encodedPassword ->
								userRepository.save(new User(username, encodedPassword, authorities))));
	}

	public Mono<Void> deleteUser(String username) {
		return userRepository.findByUsername(username)
				.last()
				.flatMap(user -> userRepository.deleteById(user.getId()));
	}

	private Mono<User> adminUser() {
		return Mono.just(new User("admin", passwordEncoder.encode("supersecret"),
				SecurityAuthorities.ADMIN, SecurityAuthorities.FULL_ACCESS));
	}

	private Mono<String> generatePassword() {
		return Mono.just(new StringBuilder(PASSWORD_LENGTH))
				.map(stringBuilder -> {
					for (int i = 0; i < PASSWORD_LENGTH; i++) {
						stringBuilder.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
					}
					return stringBuilder.toString();
				});
	}
}
