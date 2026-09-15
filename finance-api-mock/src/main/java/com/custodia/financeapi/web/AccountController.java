package com.custodia.financeapi.web;

import com.custodia.financeapi.model.Account;
import com.custodia.financeapi.repo.InMemoryAccountStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint del recurso protegido. No valida quién llama ni por qué: esa es
 * exactamente la responsabilidad que custodia-pep centraliza delante de
 * este servicio (ver docs/architecture.md).
 */
@RestController
public class AccountController {

	private final InMemoryAccountStore accounts;

	public AccountController(InMemoryAccountStore accounts) {
		this.accounts = accounts;
	}

	@GetMapping("/accounts/{id}/balance")
	public ResponseEntity<Account> getBalance(@PathVariable String id) {
		return accounts.findById(id)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}
}
