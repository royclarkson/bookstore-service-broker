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

import reactor.core.publisher.Mono;

import org.springframework.cloud.sample.bookstore.servicebroker.model.ServiceInstance;
import org.springframework.cloud.sample.bookstore.servicebroker.repository.ServiceInstanceRepository;
import org.springframework.cloud.sample.bookstore.web.service.BookStoreService;
import org.springframework.cloud.servicebroker.exception.ServiceInstanceDoesNotExistException;
import org.springframework.cloud.servicebroker.model.instance.CreateServiceInstanceRequest;
import org.springframework.cloud.servicebroker.model.instance.CreateServiceInstanceResponse;
import org.springframework.cloud.servicebroker.model.instance.DeleteServiceInstanceRequest;
import org.springframework.cloud.servicebroker.model.instance.DeleteServiceInstanceResponse;
import org.springframework.cloud.servicebroker.model.instance.GetLastServiceOperationRequest;
import org.springframework.cloud.servicebroker.model.instance.GetLastServiceOperationResponse;
import org.springframework.cloud.servicebroker.model.instance.GetServiceInstanceRequest;
import org.springframework.cloud.servicebroker.model.instance.GetServiceInstanceResponse;
import org.springframework.cloud.servicebroker.model.instance.UpdateServiceInstanceRequest;
import org.springframework.cloud.servicebroker.model.instance.UpdateServiceInstanceResponse;
import org.springframework.cloud.servicebroker.service.reactive.ServiceInstanceService;
import org.springframework.stereotype.Service;

@Service
public class BookStoreServiceInstanceService implements ServiceInstanceService {

	private final BookStoreService storeService;

	private final ServiceInstanceRepository instanceRepository;

	public BookStoreServiceInstanceService(BookStoreService storeService, ServiceInstanceRepository instanceRepository) {
		this.storeService = storeService;
		this.instanceRepository = instanceRepository;
	}

	@Override
	public Mono<CreateServiceInstanceResponse> createServiceInstance(CreateServiceInstanceRequest request) {
		return Mono.just(request.getServiceInstanceId())
				.flatMap(instanceId -> Mono.just(CreateServiceInstanceResponse.builder())
						.flatMap(responseBuilder -> instanceRepository.existsById(instanceId)
								.flatMap(exists -> {
									if (exists) {
										return Mono.just(responseBuilder.instanceExisted(true))
												.then(Mono.just(responseBuilder.build()));
									}
									else {
										return storeService.createBookStore(instanceId)
												.then(saveInstance(request, instanceId))
												.then(Mono.just(responseBuilder.build()));
									}
								})));
	}

	@Override
	public Mono<GetServiceInstanceResponse> getServiceInstance(GetServiceInstanceRequest request) {
		return Mono.just(request.getServiceInstanceId())
				.flatMap(instanceId -> instanceRepository.findById(instanceId)
						.flatMap(serviceInstance -> Mono.just(GetServiceInstanceResponse.builder()
										.serviceDefinitionId(serviceInstance.getServiceDefinitionId())
										.planId(serviceInstance.getPlanId())
										.parameters(serviceInstance.getParameters())
										.build()))
						.switchIfEmpty(Mono.error(new ServiceInstanceDoesNotExistException(instanceId))));
	}

	@Override
	public Mono<GetLastServiceOperationResponse> getLastOperation(GetLastServiceOperationRequest request) {
		return Mono.empty();
	}

	@Override
	public Mono<DeleteServiceInstanceResponse> deleteServiceInstance(DeleteServiceInstanceRequest request) {
		return Mono.just(request.getServiceInstanceId())
				.flatMap(instanceId -> instanceRepository.existsById(instanceId)
						.flatMap(exists -> {
							if (exists) {
								return storeService.deleteBookStore(instanceId)
										.then(instanceRepository.deleteById(instanceId))
										.then(Mono.just(DeleteServiceInstanceResponse.builder().build()));
							}
							else {
								return Mono.error(new ServiceInstanceDoesNotExistException(instanceId));
							}
						}));
	}

	@Override
	public Mono<UpdateServiceInstanceResponse> updateServiceInstance(UpdateServiceInstanceRequest request) {
		return Mono.empty();
	}

	private Mono<ServiceInstance> saveInstance(CreateServiceInstanceRequest request, String instanceId) {
		return Mono.just(new ServiceInstance(instanceId, request.getServiceDefinitionId(),
				request.getPlanId(), request.getParameters()))
				.flatMap(instanceRepository::save);
	}
}
