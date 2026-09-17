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

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

// Verifica SEC-001: si el PDP (OPA) no responde, el PEP debe denegar el
// acceso (fail-closed), no fallar abierto ni devolver un error ambiguo.
// A proposito NO se levanta un contenedor de OPA: se apunta
// custodia.opa.base-url a un puerto sin nada escuchando, simulando el PDP
// caido.
@SpringBootTest(classes = CustodiaPepApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "PT10S")
class PdpFailClosedIT {

    private static final String ACCOUNT_ID = "acc-1001";
    private static final String OWNER_ID = "emp-42";

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

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("custodia.opa.base-url", () -> "http://localhost:1");
        registry.add("custodia.finance-api.base-url", FINANCE_API::baseUrl);
    }

    @AfterAll
    static void stopContainers() {
        FINANCE_API.stop();
    }

    @Autowired
    WebTestClient client;

    @Test
    void pdpUnavailable_denysAccessByDefault() {
        client.get().uri("/accounts/" + ACCOUNT_ID + "/balance")
                .header("X-Employee-Id", OWNER_ID)
                .header("X-Role", "employee")
                .header("X-Risk-Score", "10")
                .exchange()
                .expectStatus().isForbidden();
    }
}