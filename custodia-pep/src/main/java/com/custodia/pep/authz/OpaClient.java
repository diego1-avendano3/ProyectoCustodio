package com.custodia.pep.authz;

import com.custodia.pep.authz.AuthzModels.AuthzDecision;
import com.custodia.pep.authz.AuthzModels.AuthzInput;
import com.custodia.pep.authz.AuthzModels.OpaRequest;
import com.custodia.pep.authz.AuthzModels.OpaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Cliente del Policy Decision Point (OPA). Encapsula el único punto del
 * sistema donde vive la política de fail-closed (ADR-0001): cualquier
 * error o timeout al consultar el PDP se traduce en una denegación, nunca
 * en dejar pasar la solicitud.
 */
@Component
public class OpaClient {

	private static final Logger log = LoggerFactory.getLogger(OpaClient.class);

	private final WebClient webClient;
	private final String policyPath;
	private final Duration timeout;

	public OpaClient(
			WebClient.Builder webClientBuilder,
			@Value("${custodia.opa.base-url}") String baseUrl,
			@Value("${custodia.opa.policy-path}") String policyPath,
			@Value("${custodia.opa.timeout-ms:300}") long timeoutMs
	) {
		this.webClient = webClientBuilder.baseUrl(baseUrl).build();
		this.policyPath = policyPath;
		this.timeout = Duration.ofMillis(timeoutMs);
	}

	public Mono<AuthzDecision> decide(AuthzInput input) {
		return webClient.post()
				.uri(policyPath)
				.bodyValue(new OpaRequest(input))
				.retrieve()
				.bodyToMono(OpaResponse.class)
				.timeout(timeout)
				.map(OpaResponse::result)
                .doOnNext(decision -> log.debug("Decisión del PDP para {}: allow={}", input, decision.allow()))
				.onErrorResume(ex -> {
					log.warn("PDP no disponible u operación fuera de tiempo; aplicando fail-closed (ADR-0001). Causa: {}",
							ex.toString());
					return Mono.just(AuthzDecision.denyClosed());
				});
	}
}
