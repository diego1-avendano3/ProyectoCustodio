package com.custodia.pep.authz;

import com.custodia.pep.authz.AuthzModels.Subject;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Resuelve el sujeto autenticado a partir de headers de desarrollo
 * (X-Employee-Id, X-Role, X-Risk-Score).
 *
 * <p><b>Esto es una simplificación deliberada de Fase 1</b>, documentada en
 * docs/architecture.md y docs/threat-model.md (amenaza de Spoofing): un
 * header no es una prueba de identidad, cualquiera puede falsificarlo. Sirve
 * para probar de punta a punta el mecanismo de autorización por política
 * antes de resolver la integración OIDC completa. Fase 3 reemplaza esta
 * clase por un resolver que valida un JWT firmado por el Identity Provider
 * y nunca confía en un valor provisto por el cliente.</p>
 */
@Component
public class DevHeaderSubjectResolver {

	public static final String HEADER_EMPLOYEE_ID = "X-Employee-Id";
	public static final String HEADER_ROLE = "X-Role";
	public static final String HEADER_RISK_SCORE = "X-Risk-Score";

	public Subject resolve(HttpHeaders headers) {
		String employeeId = requireHeader(headers, HEADER_EMPLOYEE_ID);
		String role = headers.getFirst(HEADER_ROLE);
		int riskScore = parseRiskScore(headers.getFirst(HEADER_RISK_SCORE));
		return new Subject(employeeId, role == null ? "employee" : role, riskScore);
	}

	private String requireHeader(HttpHeaders headers, String name) {
		String value = headers.getFirst(name);
		if (value == null || value.isBlank()) {
			throw new MissingSubjectHeaderException(name);
		}
		return value;
	}

	private int parseRiskScore(String raw) {
		if (raw == null || raw.isBlank()) {
			return 0;
		}
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException ex) {
			throw new MissingSubjectHeaderException(HEADER_RISK_SCORE + " debe ser numérico");
		}
	}

	public static class MissingSubjectHeaderException extends RuntimeException {
		public MissingSubjectHeaderException(String message) {
			super(message);
		}
	}
}
