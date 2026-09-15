package com.custodia.financeapi.model;

import java.math.BigDecimal;

/**
 * Cuenta financiera de ejemplo. {@code accountNumber} es el campo que
 * las políticas de Custodia pueden pedir enmascarar (ver
 * policies/authz.rego, obligations.mask_fields).
 */
public record Account(
		String id,
		String ownerId,
		String accountNumber,
		BigDecimal balance,
		String currency
) {
}
