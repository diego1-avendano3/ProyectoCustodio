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
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

// OJO: sin @Testcontainers -- manejamos el ciclo de vida del contenedor de OPA
// a mano (igual que WireMock), porque la extension de JUnit5 de Testcontainers
// (@Testcontainers + @Container) choca con la resolucion de @BootstrapWith de
// Spring en esta combinacion de versiones (Testcontainers 1.20.4 + Spring Boot
// 3.3.5), incluso cuando el contenedor arranca sin errores. Ver discusion en el PR.
@SpringBootTest(classes = CustodiaPepApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "PT10S")
class AuthorizationE2EIT {

    private static final String ACCOUNT_ID = "acc-1001";
    private static final String OWNER_ID = "emp-42";
    private static final String MASKED_ACCOUNT_NUMBER = "****-****-****-6677";

    private static final Path POLICIES_DIR = Paths.get("").toAbsolutePath()
            .getParent().resolve("policies");

    private static final WireMockServer FINANCE_API = new WireMockServer(wireMockConfig().dynamicPort());

    private static final GenericContainer<?> OPA = new GenericContainer<>(DockerImageName.parse("openpolicyagent/opa:latest"))
            .withExposedPorts(8181)
            .withCopyFileToContainer(MountableFile.forHostPath(POLICIES_DIR.toString()), "/policies")
            .withCommand("run", "--server", "--addr=0.0.0.0:8181", "/policies")
            .waitingFor(Wait.forHttp("/health").forPort(8181));

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
        OPA.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("custodia.opa.base-url",
                () -> "http://" + OPA.getHost() + ":" + OPA.getMappedPort(8181));
        registry.add("custodia.finance-api.base-url", FINANCE_API::baseUrl);
    }

    @AfterAll
    static void stopContainers() {
        FINANCE_API.stop();
        OPA.stop();
    }

    @Autowired
    WebTestClient client;

    @Test
    void ownerWithLowRisk_getsFullBalance() {
        client.get().uri("/accounts/" + ACCOUNT_ID + "/balance")
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
        client.get().uri("/accounts/" + ACCOUNT_ID + "/balance")
                .header("X-Employee-Id", "emp-1")
                .header("X-Role", "employee")
                .header("X-Risk-Score", "10")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.error").isEqualTo("access_denied");
    }

    @Test
    void ownerWithHighRisk_getsBalanceWithMaskedAccountNumber() {
        client.get().uri("/accounts/" + ACCOUNT_ID + "/balance")
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
        client.get().uri("/accounts/" + ACCOUNT_ID + "/balance")
                .header("X-Employee-Id", "emp-99")
                .header("X-Role", "fraud_analyst")
                .header("X-Risk-Score", "10")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void fraudAnalystWithOpenCase_getsBalanceWithMaskedAccountNumber() {
        client.get().uri("/accounts/" + ACCOUNT_ID + "/balance?caseOpen=true")
                .header("X-Employee-Id", "emp-99")
                .header("X-Role", "fraud_analyst")
                .header("X-Risk-Score", "10")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accountNumber").isEqualTo(MASKED_ACCOUNT_NUMBER);
    }

    @Test
    void ownerWithExtremeRisk_isDenied() {
        // AUTH-006: risk_score >= 90 no tiene regla de permiso en authz.rego;
        // cae en el default deny incluso para el propio dueño de la cuenta.
        client.get().uri("/accounts/" + ACCOUNT_ID + "/balance")
                .header("X-Employee-Id", OWNER_ID)
                .header("X-Role", "employee")
                .header("X-Risk-Score", "90")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void missingRiskScoreHeader_isDenied() {
        // SEC-003: si falta un header obligatorio, el PEP debe denegar
        // (fail-closed), nunca fallar con un 5xx ni conceder acceso.
        client.get().uri("/accounts/" + ACCOUNT_ID + "/balance")
                .header("X-Employee-Id", OWNER_ID)
                .header("X-Role", "employee")
                .exchange()
                .expectStatus().isForbidden();
    }
}