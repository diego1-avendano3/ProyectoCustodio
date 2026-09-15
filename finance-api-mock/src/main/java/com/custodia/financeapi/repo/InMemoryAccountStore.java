package com.custodia.financeapi.repo;

import com.custodia.financeapi.model.Account;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Datos de ejemplo en memoria, suficientes para probar Custodia de punta a
 * punta sin depender de una base de datos real en Fase 1.
 */
@Component
public class InMemoryAccountStore {

	private final Map<String, Account> accountsById = new ConcurrentHashMap<>();

	public InMemoryAccountStore() {
		seed(new Account("acc-1001", "emp-42", "4111-2233-4455-6677", new BigDecimal("2450000.00"), "COP"));
		seed(new Account("acc-1002", "emp-1", "4111-9988-7766-5544", new BigDecimal("980000.50"), "COP"));
	}

	private void seed(Account account) {
		accountsById.put(account.id(), account);
	}

	public Optional<Account> findById(String id) {
		return Optional.ofNullable(accountsById.get(id));
	}
}
