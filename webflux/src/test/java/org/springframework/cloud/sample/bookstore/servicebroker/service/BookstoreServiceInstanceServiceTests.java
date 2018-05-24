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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import reactor.core.publisher.Mono;

import org.springframework.cloud.sample.bookstore.servicebroker.model.ServiceInstance;
import org.springframework.cloud.sample.bookstore.servicebroker.repository.ServiceInstanceRepository;
import org.springframework.cloud.sample.bookstore.web.model.BookStore;
import org.springframework.cloud.sample.bookstore.web.service.BookStoreService;
import org.springframework.cloud.servicebroker.exception.ServiceInstanceDoesNotExistException;
import org.springframework.cloud.servicebroker.model.Context;
import org.springframework.cloud.servicebroker.model.instance.CreateServiceInstanceRequest;
import org.springframework.cloud.servicebroker.model.instance.CreateServiceInstanceResponse;
import org.springframework.cloud.servicebroker.model.instance.DeleteServiceInstanceRequest;
import org.springframework.cloud.servicebroker.model.instance.DeleteServiceInstanceResponse;
import org.springframework.cloud.servicebroker.model.instance.GetServiceInstanceRequest;
import org.springframework.cloud.servicebroker.model.instance.GetServiceInstanceResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(MockitoJUnitRunner.class)
public class BookstoreServiceInstanceServiceTests {
	private static final String SERVICE_INSTANCE_ID = "instance-id";

	@Mock
	private ServiceInstanceRepository repository;

	@Mock
	private BookStoreService store;

	private BookStoreServiceInstanceService service;

	@Before
	public void setUp() {
		initMocks(this);

		service = new BookStoreServiceInstanceService(store, repository);
	}

	@Test
	public void createServiceInstanceWhenInstanceExists() {
		when(repository.existsById(SERVICE_INSTANCE_ID))
				.thenReturn(Mono.just(true));

		CreateServiceInstanceRequest request = CreateServiceInstanceRequest.builder()
				.serviceInstanceId(SERVICE_INSTANCE_ID)
				.build();

		CreateServiceInstanceResponse response = service.createServiceInstance(request)
				.block();

		assertThat(response.isInstanceExisted()).isTrue();
		assertThat(response.getDashboardUrl()).isNull();
		assertThat(response.isAsync()).isFalse();
		assertThat(response.getOperation()).isNull();

		verify(repository).existsById(SERVICE_INSTANCE_ID);
		verifyNoMoreInteractions(repository);

		verifyZeroInteractions(store);
	}

	@Test
	public void createServiceInstanceWhenInstanceDoesNotExist() {
		Context context = Context.builder()
				.platform("test-platform")
				.build();

		when(repository.existsById(SERVICE_INSTANCE_ID))
				.thenReturn(Mono.just(false));

		BookStore bookStore = new BookStore(SERVICE_INSTANCE_ID);

		when(store.createBookStore(SERVICE_INSTANCE_ID))
				.thenReturn(Mono.just(bookStore));

		ServiceInstance serviceInstance = new ServiceInstance(SERVICE_INSTANCE_ID,
				null, null, null);

		when(repository.save(any(ServiceInstance.class)))
				.thenReturn(Mono.just(serviceInstance));

		CreateServiceInstanceRequest request = CreateServiceInstanceRequest.builder()
				.serviceInstanceId(SERVICE_INSTANCE_ID)
				.context(context)
				.build();

		CreateServiceInstanceResponse response = service.createServiceInstance(request)
				.block();

		assertThat(response.isInstanceExisted()).isFalse();
		assertThat(response.getDashboardUrl()).isNull();
		assertThat(response.isAsync()).isFalse();
		assertThat(response.getOperation()).isNull();

		verify(repository).existsById(SERVICE_INSTANCE_ID);
		ArgumentCaptor<ServiceInstance> argumentCaptor = ArgumentCaptor.forClass(ServiceInstance.class);
		verify(repository).save(argumentCaptor.capture());
		verifyNoMoreInteractions(repository);

		ServiceInstance actual = argumentCaptor.getValue();
		assertThat(actual.getInstanceId()).isEqualTo(SERVICE_INSTANCE_ID);

		verify(store).createBookStore(SERVICE_INSTANCE_ID);
		verifyNoMoreInteractions(store);
	}

	@Test
	public void getServiceInstanceWhenInstanceExists() {
		ServiceInstance serviceInstance = new ServiceInstance(SERVICE_INSTANCE_ID, "service-definition-id",
				"plan-id", new HashMap<>());

		when(repository.findById(SERVICE_INSTANCE_ID))
				.thenReturn(Mono.just(serviceInstance));

		GetServiceInstanceRequest request = GetServiceInstanceRequest.builder()
				.serviceInstanceId(SERVICE_INSTANCE_ID)
				.build();

		GetServiceInstanceResponse response = service.getServiceInstance(request)
				.block();

		assertThat(response.getServiceDefinitionId()).isEqualTo(serviceInstance.getServiceDefinitionId());
		assertThat(response.getPlanId()).isEqualTo(serviceInstance.getPlanId());
		assertThat(response.getParameters()).isEqualTo(serviceInstance.getParameters());

		verify(repository).findById(SERVICE_INSTANCE_ID);
		verifyNoMoreInteractions(repository);
	}

	@Test(expected = ServiceInstanceDoesNotExistException.class)
	public void getServiceInstanceWhenInstanceDoesNotExists() {
		when(repository.findById(SERVICE_INSTANCE_ID))
				.thenReturn(Mono.empty());

		GetServiceInstanceRequest request = GetServiceInstanceRequest.builder()
				.serviceInstanceId(SERVICE_INSTANCE_ID)
				.build();

		service.getServiceInstance(request)
			.block();
	}

	@Test
	public void deleteServiceInstanceWhenInstanceExists() {
		when(repository.existsById(SERVICE_INSTANCE_ID))
				.thenReturn(Mono.just(true));

		when(repository.deleteById(SERVICE_INSTANCE_ID))
				.thenReturn(Mono.empty());

		when(store.deleteBookStore(SERVICE_INSTANCE_ID))
				.thenReturn(Mono.empty());

		DeleteServiceInstanceRequest request = DeleteServiceInstanceRequest.builder()
				.serviceInstanceId(SERVICE_INSTANCE_ID)
				.build();

		DeleteServiceInstanceResponse response = service.deleteServiceInstance(request)
				.block();

		assertThat(response.isAsync()).isFalse();
		assertThat(response.getOperation()).isNull();

		verify(repository).existsById(SERVICE_INSTANCE_ID);
		verify(repository).deleteById(SERVICE_INSTANCE_ID);
		verifyNoMoreInteractions(repository);

		verify(store).deleteBookStore(SERVICE_INSTANCE_ID);
		verifyNoMoreInteractions(store);
	}

	@Test(expected = ServiceInstanceDoesNotExistException.class)
	public void deleteServiceInstanceWhenInstanceDoesNotExist() {
		when(repository.existsById(SERVICE_INSTANCE_ID))
				.thenReturn(Mono.just(false));

		DeleteServiceInstanceRequest request = DeleteServiceInstanceRequest.builder()
				.serviceInstanceId(SERVICE_INSTANCE_ID)
				.build();

		service.deleteServiceInstance(request)
			.block();
	}
}