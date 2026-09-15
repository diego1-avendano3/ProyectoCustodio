package com.custodia.pep.audit;

import com.custodia.pep.authz.AuthzModels.AuthzDecision;
import com.custodia.pep.authz.AuthzModels.AuthzInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Registro de auditoría de cada decisión de autorización — mitigación
 * directa de la amenaza "Repudiation" del modelo STRIDE
 * (docs/threat-model.md). Se registra tanto lo permitido como lo denegado,
 * antes de que la respuesta salga hacia el cliente.
 *
 * <p>En Fase 1 esto es un log estructurado por consola vía MDC. Fase 5
 * reemplaza el sumidero por OpenTelemetry + CloudWatch/OpenSearch, sin
 * tener que cambiar el punto de llamada (ver docs/architecture.md,
 * roadmap).</p>
 */
@Component
public class AuditLogger {

	private static final Logger log = LoggerFactory.getLogger("CUSTODIA_AUDIT");

	public void logDecision(AuthzInput input, AuthzDecision decision) {
		try {
			MDC.put("principal", input.subject().employeeId());
			MDC.put("role", input.subject().role());
			MDC.put("action", input.action());
			MDC.put("resourceOwner", input.resource().ownerId());
			MDC.put("allow", String.valueOf(decision.allow()));
			MDC.put("maskedFields", String.valueOf(decision.obligations().maskFields()));

			if (decision.allow()) {
				log.info("authz_decision");
			} else {
				log.warn("authz_decision");
			}
		} finally {
			MDC.clear();
		}
	}
}
