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

package org.springframework.cloud.sample.bookstore.servicebroker.service;

import java.util.HashMap;
import java.util.Map;

import reactor.core.publisher.Mono;

import org.springframework.cloud.sample.bookstore.servicebroker.model.ServiceBinding;
import org.springframework.cloud.sample.bookstore.servicebroker.repository.ServiceBindingRepository;
import org.springframework.cloud.sample.bookstore.web.model.ApplicationInformation;
import org.springframework.cloud.sample.bookstore.web.model.User;
import org.springframework.cloud.sample.bookstore.web.service.UserService;
import org.springframework.cloud.servicebroker.exception.ServiceInstanceBindingDoesNotExistException;
import org.springframework.cloud.servicebroker.model.binding.CreateServiceInstanceAppBindingResponse;
import org.springframework.cloud.servicebroker.model.binding.CreateServiceInstanceBindingRequest;
import org.springframework.cloud.servicebroker.model.binding.CreateServiceInstanceBindingResponse;
import org.springframework.cloud.servicebroker.model.binding.DeleteServiceInstanceBindingRequest;
import org.springframework.cloud.servicebroker.model.binding.GetServiceInstanceAppBindingResponse;
import org.springframework.cloud.servicebroker.model.binding.GetServiceInstanceBindingRequest;
import org.springframework.cloud.servicebroker.model.binding.GetServiceInstanceBindingResponse;
import org.springframework.cloud.servicebroker.service.reactive.ServiceInstanceBindingService;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.cloud.sample.bookstore.web.security.SecurityAuthorities.BOOK_STORE_ID_PREFIX;
import static org.springframework.cloud.sample.bookstore.web.security.SecurityAuthorities.FULL_ACCESS;

@Service
public class BookStoreServiceInstanceBindingService implements ServiceInstanceBindingService {
	private static final String URI_KEY = "uri";
	private static final String USERNAME_KEY = "username";
	private static final String PASSWORD_KEY = "password";

	private final ServiceBindingRepository bindingRepository;
	private final UserService userService;
	private final ApplicationInformation applicationInformation;

	public BookStoreServiceInstanceBindingService(ServiceBindingRepository bindingRepository,
												  UserService userService,
												  ApplicationInformation applicationInformation) {
		this.bindingRepository = bindingRepository;
		this.userService = userService;
		this.applicationInformation = applicationInformation;
	}

	@Override
	public Mono<CreateServiceInstanceBindingResponse> createServiceInstanceBinding(CreateServiceInstanceBindingRequest request) {
		return Mono.just(request.getBindingId())
				.flatMap(bindingId -> bindingRepository.existsById(request.getBindingId())
						.flatMap(exists -> {
							if (exists) {
								return bindingRepository.findById(request.getBindingId())
										.flatMap(binding -> Mono.just(CreateServiceInstanceAppBindingResponse.builder()
												.bindingExisted(true)
												.credentials(binding.getCredentials())
												.build()));
							}
							else {
								return createUser(request)
										.flatMap(user -> buildCredentials(request.getServiceInstanceId(), user))
										.flatMap(credentials -> saveBinding(request, credentials)
												.thenReturn(CreateServiceInstanceAppBindingResponse.builder()
														.bindingExisted(false)
														.credentials(credentials)
														.build()));
							}
						}));
	}

	@Override
	public Mono<GetServiceInstanceBindingResponse> getServiceInstanceBinding(GetServiceInstanceBindingRequest request) {
		return Mono.just(request.getBindingId())
				.flatMap(bindingId -> bindingRepository.findById(bindingId)
						.flatMap(binding -> Mono.just(GetServiceInstanceAppBindingResponse.builder()
										.parameters(binding.getParameters())
										.credentials(binding.getCredentials())
										.build()))
						.switchIfEmpty(Mono.error(new ServiceInstanceBindingDoesNotExistException(bindingId))));
	}

	@Override
	public Mono<Void> deleteServiceInstanceBinding(DeleteServiceInstanceBindingRequest request) {
		return Mono.just(request.getBindingId())
				.flatMap(bindingId -> bindingRepository.existsById(bindingId)
						.flatMap(exists -> {
							if (exists) {
								return bindingRepository.deleteById(bindingId)
										.then(userService.deleteUser(bindingId));
							}
							else {
								return Mono.error(new ServiceInstanceBindingDoesNotExistException(bindingId));
							}
						}));
	}

	private Mono<User> createUser(CreateServiceInstanceBindingRequest request) {
		return userService.createUser(request.getBindingId(),
				FULL_ACCESS, BOOK_STORE_ID_PREFIX + request.getServiceInstanceId());
	}

	private Mono<Map<String, Object>> buildCredentials(String instanceId, User user) {
		return buildUri(instanceId)
				.flatMap(uri -> Mono.just(new HashMap<String, Object>())
						.flatMap(credentials -> {
							credentials.put(URI_KEY, uri);
							credentials.put(USERNAME_KEY, user.getUsername());
							credentials.put(PASSWORD_KEY, user.getPassword());
							return Mono.just(credentials);
						}));
	}

	private Mono<String> buildUri(String instanceId) {
		return Mono.just(UriComponentsBuilder
					.fromUriString(applicationInformation.getBaseUrl())
					.pathSegment("bookstores", instanceId)
					.build()
					.toUriString());
	}

	private Mono<ServiceBinding> saveBinding(CreateServiceInstanceBindingRequest request, Map<String, Object> credentials) {
		return Mono.just(new ServiceBinding(request.getBindingId(), request.getParameters(), credentials))
				.flatMap(bindingRepository::save);
	}
}