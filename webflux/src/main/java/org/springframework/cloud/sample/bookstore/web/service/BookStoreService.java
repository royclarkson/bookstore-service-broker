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

import java.util.UUID;

import reactor.core.publisher.Mono;

import org.springframework.cloud.sample.bookstore.web.model.Book;
import org.springframework.cloud.sample.bookstore.web.model.BookStore;
import org.springframework.cloud.sample.bookstore.web.repository.BookStoreRepository;
import org.springframework.stereotype.Service;

@Service
public class BookStoreService {

	private final BookStoreRepository repository;

	public BookStoreService(BookStoreRepository bookStoreRepository) {
		this.repository = bookStoreRepository;
	}

	public Mono<BookStore> createBookStore(String storeId) {
		return repository.save(new BookStore(storeId));
	}

	public Mono<BookStore> createBookStore() {
		return createBookStore(generateRandomId());
	}

	public Mono<BookStore> getBookStore(String storeId) {
		return repository.findById(storeId);
	}

	public Mono<Void> deleteBookStore(String id) {
		return repository.deleteById(id);
	}

	public Mono<Book> putBookInStore(String storeId, Book book) {
		return  getBookStore(storeId)
				.flatMap(store -> Mono.just(new Book(generateRandomId(), book))
					.flatMap(bookWithId -> store.addBook(bookWithId)
							.then(repository.save(store))
							.thenReturn(bookWithId)));
	}

	public Mono<Book> getBookFromStore(String storeId, String bookId) {
		return getBookStore(storeId)
				.flatMap(store -> store.getBookById(bookId))
				.switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid book ID " + storeId + ":" + bookId + ".")));
	}

	public Mono<Book> removeBookFromStore(String storeId, String bookId) {
		return getBookStore(storeId)
				.flatMap(store -> getBookFromStore(storeId, bookId)
						.flatMap(book -> store.remove(bookId)
								.then(repository.save(store))
								.thenReturn(book)))
				.switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid book ID " + storeId + ":" + bookId + ".")));
	}

	private String generateRandomId() {
		return UUID.randomUUID().toString();
	}
}
