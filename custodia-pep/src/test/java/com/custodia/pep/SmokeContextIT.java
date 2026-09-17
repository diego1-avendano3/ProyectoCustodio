package com.custodia.pep;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
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

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@Testcontainers
@SpringBootTest(classes = CustodiaPepApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "PT10S")
class SmokeContextIT {

    private static final Path POLICIES_DIR = Paths.get("").toAbsolutePath()
            .getParent().resolve("policies");

    private static final WireMockServer FINANCE_API = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        FINANCE_API.start();
    }

    @Container
    static final GenericContainer<?> OPA = new GenericContainer<>(DockerImageName.parse("openpolicyagent/opa:latest"))
            .withExposedPorts(8181)
            .withCopyFileToContainer(MountableFile.forHostPath(POLICIES_DIR.toString()), "/policies")
            .withCommand("run", "--server", "--addr=0.0.0.0:8181", "/policies")
            .waitingFor(Wait.forHttp("/health").forPort(8181));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("custodia.opa.base-url", () -> "http://" + OPA.getHost() + ":" + OPA.getMappedPort(8181));
        registry.add("custodia.finance-api.base-url", FINANCE_API::baseUrl);
    }

    @AfterAll
    static void stopFinanceApiStub() {
        FINANCE_API.stop();
    }

    @Autowired
    WebTestClient client;

    @Test
    void contextLoads() {
    }
}