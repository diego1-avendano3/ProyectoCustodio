package com.custodia.pep;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import org.springframework.boot.test.context.SpringBootTestContextBootstrapper;
import org.springframework.test.context.BootstrapWith;

/**
 * Reemplaza la matriz de 5 escenarios que se validó a mano con curl durante
 * el cierre de Fase 1 (ver docs/reports/fase1-resumen.pdf) por pruebas
 * automatizadas, de punta a punta a través de la aplicación real.
 *
 * <p>Dos piezas externas, dos estrategias distintas a propósito:
 * <ul>
 *   <li><b>custodia-pdp (OPA)</b> corre en un contenedor real (Testcontainers)
 *   cargando las políticas reales de {@code policies/}. Los tres bugs de
 *   Fase 1 (policy-path, deserialización de Jackson, ServerWebExchange)
 *   vivieron exactamente en este contrato — no tiene sentido simularlo.</li>
 *   <li><b>finance-api-mock</b> se reemplaza por WireMock. Su contrato con
 *   el PEP es un GET simple que ya cubren las pruebas del propio módulo
 *   finance-api-mock; levantar un segundo contenedor Spring Boot aquí solo
 *   añadiría tiempo de build sin aportar cobertura nueva.</li>
 * </ul>
 *
 * <p>Corre en la fase {@code verify} (maven-failsafe-plugin), no en
 * {@code test} — necesita Docker. En GitHub Actions (ubuntu-latest) Docker
 * ya está disponible, así que {@code mvn -B verify} en ci.yml la ejecuta
 * sin cambios adicionales.
 */
@Testcontainers
@SpringBootTest(classes = CustodiaPepApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "PT10S")
@BootstrapWith(SpringBootTestContextBootstrapper.class)
class AuthorizationE2EIT {

	private static final String ACCOUNT_ID = "acc-1001";
	private static final String OWNER_ID = "emp-42";
	private static final String MASKED_ACCOUNT_NUMBER = "****-****-****-6677";

	/** Raíz del repo = módulo actual (custodia-pep) + un nivel arriba. */
	private static final Path POLICIES_DIR = Paths.get("").toAbsolutePath()
			.getParent().resolve("policies");

	private static final WireMockServer FINANCE_API = new WireMockServer(wireMockConfig().dynamicPort());

	static {
		FINANCE_API.start();
		FINANCE_API.stubFor(get(urlEqualTo("/accounts/" + ACCOUNT_ID + "/balance"))
				.willReturn(okJson("""
						{
						  "id": "%s",
						  "ownerId": "%s",
						  "accountNumber": "4111-2233-4455-6677",
						  "balance": 2450000.00,
						  "currency": "COP"
						}
						""".formatted(ACCOUNT_ID, OWNER_ID))));
	}

	@Container
	static final GenericContainer<?> OPA = new GenericContainer<>(DockerImageName.parse("openpolicyagent/opa:latest"))
			.withExposedPorts(8181)
			.withCopyFileToContainer(MountableFile.forHostPath(POLICIES_DIR.toString()), "/policies")
			.withCommand("run", "--server", "--addr=0.0.0.0:8181", "/policies")
			.waitingFor(Wait.forHttp("/health").forPort(8181));

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("custodia.opa.base-url",
				() -> "http://" + OPA.getHost() + ":" + OPA.getMappedPort(8181));
		registry.add("custodia.finance-api.base-url", FINANCE_API::baseUrl);
	}

	@AfterAll
	static void stopFinanceApiStub() {
		FINANCE_API.stop();
	}

	@Autowired
	WebTestClient client;

	@Test
	void ownerWithLowRisk_getsFullBalance() {
		client.get().uri("/accounts/{id}/balance", ACCOUNT_ID)
				.header("X-Employee-Id", OWNER_ID)
				.header("X-Role", "employee")
				.header("X-Risk-Score", "10")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.ownerId").isEqualTo(OWNER_ID)
				.jsonPath("$.accountNumber").isEqualTo("4111-2233-4455-6677");
	}

	@Test
	void nonOwner_isDenied() {
		client.get().uri("/accounts/{id}/balance", ACCOUNT_ID)
				.header("X-Employee-Id", "emp-1")
				.header("X-Role", "employee")
				.header("X-Risk-Score", "5")
				.exchange()
				.expectStatus().isForbidden()
				.expectBody()
				.jsonPath("$.error").isEqualTo("access_denied");
	}

	@Test
	void ownerWithHighRisk_getsBalanceWithMaskedAccountNumber() {
		client.get().uri("/accounts/{id}/balance", ACCOUNT_ID)
				.header("X-Employee-Id", OWNER_ID)
				.header("X-Role", "employee")
				.header("X-Risk-Score", "80")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.accountNumber").isEqualTo(MASKED_ACCOUNT_NUMBER);
	}

	@Test
	void fraudAnalystWithoutOpenCase_isDenied() {
		client.get().uri("/accounts/{id}/balance", ACCOUNT_ID)
				.header("X-Employee-Id", "emp-9")
				.header("X-Role", "fraud_analyst")
				.header("X-Risk-Score", "40")
				.exchange()
				.expectStatus().isForbidden()
				.expectBody()
				.jsonPath("$.error").isEqualTo("access_denied");
	}

	@Test
	void fraudAnalystWithOpenCase_getsBalanceWithMaskedAccountNumber() {
		client.get().uri(uriBuilder -> uriBuilder
						.path("/accounts/{id}/balance")
						.queryParam("caseOpen", "true")
						.build(ACCOUNT_ID))
				.header("X-Employee-Id", "emp-9")
				.header("X-Role", "fraud_analyst")
				.header("X-Risk-Score", "40")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.accountNumber").isEqualTo(MASKED_ACCOUNT_NUMBER);
	}
}
