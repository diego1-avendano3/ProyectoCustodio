package com.custodia.pep.authz;

import com.custodia.pep.authz.AuthzModels.AuthzContext;
import com.custodia.pep.authz.AuthzModels.AuthzDecision;
import com.custodia.pep.authz.AuthzModels.AuthzInput;
import com.custodia.pep.authz.AuthzModels.Resource;
import com.custodia.pep.authz.AuthzModels.Subject;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica ADR-0001 (fail-closed) a nivel de código: si el PDP es
 * inalcanzable, OpaClient nunca deja "pasar" la solicitud por defecto.
 * Apunta a un puerto sin nada escuchando para forzar el fallo de conexión,
 * sin depender de un OPA real corriendo (esa cobertura de integración llega
 * en Fase 4 con Testcontainers, ver Proyecto Custodia § Plan de pruebas).
 */
class OpaClientFailClosedTest {

	@Test
	void deniesByDefaultWhenThePdpIsUnreachable() {
		OpaClient opaClient = new OpaClient(
				WebClient.builder(),
				"http://localhost:1", // puerto sin listener: fuerza fallo de conexión
				"/v1/data/officesec/authz",
				200
		);

		AuthzInput input = new AuthzInput(
				"view_balance",
				new Subject("emp-42", "employee", 10),
				new Resource("emp-42"),
				new AuthzContext(false)
		);

		StepVerifier.create(opaClient.decide(input))
				.assertNext(decision -> assertThat(decision.allow())
						.as("el PDP inalcanzable nunca debe traducirse en allow=true")
						.isFalse())
				.verifyComplete();
	}

	@Test
	void denyClosedFactoryProducesNoObligations() {
		AuthzDecision decision = AuthzDecision.denyClosed();

		assertThat(decision.allow()).isFalse();
		assertThat(decision.obligations().maskFields()).isEmpty();
	}
}
