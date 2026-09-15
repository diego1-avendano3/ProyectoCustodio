package com.custodia.pep.proxy;

import com.custodia.pep.audit.AuditLogger;
import com.custodia.pep.authz.AuthzModels.AuthzContext;
import com.custodia.pep.authz.AuthzModels.AuthzInput;
import com.custodia.pep.authz.AuthzModels.Resource;
import com.custodia.pep.authz.AuthzModels.Subject;
import com.custodia.pep.authz.DevHeaderSubjectResolver;
import com.custodia.pep.authz.DevHeaderSubjectResolver.MissingSubjectHeaderException;
import com.custodia.pep.authz.OpaClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Único punto de entrada a los datos de cuentas. Implementa el flujo
 * descrito en docs/architecture.md: resuelve el sujeto → busca el recurso →
 * consulta al PDP → aplica obligations → responde. Nunca se le entrega al
 * cliente un dato que no pasó por una decisión explícita del PDP (ADR-0001,
 * fail-closed, cubre también los errores no controlados de este método).
 */
@RestController
@RequestMapping("/accounts")
public class AccountProxyController {

	private static final ParameterizedTypeReference<Map<String, Object>> ACCOUNT_BODY =
			new ParameterizedTypeReference<>() {
			};

	private final WebClient financeApiWebClient;
	private final OpaClient opaClient;
	private final DevHeaderSubjectResolver subjectResolver;
	private final AuditLogger auditLogger;

	public AccountProxyController(
			WebClient financeApiWebClient,
			OpaClient opaClient,
			DevHeaderSubjectResolver subjectResolver,
			AuditLogger auditLogger
	) {
		this.financeApiWebClient = financeApiWebClient;
		this.opaClient = opaClient;
		this.subjectResolver = subjectResolver;
		this.auditLogger = auditLogger;
	}

	@GetMapping("/{id}/balance")
	public Mono<ResponseEntity<Object>> getBalance(
			@PathVariable String id,
			HttpHeaders headers,
			@RequestParam(name = "caseOpen", required = false, defaultValue = "false") boolean caseOpen
	) {
		Subject subject;
		try {
			subject = subjectResolver.resolve(headers);
		} catch (MissingSubjectHeaderException ex) {
			return Mono.just(ResponseEntity.badRequest().body(Map.of("error", ex.getMessage())));
		}

		// Se consulta el recurso antes de decidir porque el dueño (necesario
		// para la política) vive en el propio recurso; el dato nunca sale de
		// aquí hacia el cliente si el PDP no lo autoriza explícitamente.
		return fetchAccount(id)
				.flatMap(account -> authorizeAndRespond(subject, account, caseOpen))
				.switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
	}

	private Mono<Map<String, Object>> fetchAccount(String id) {
		return financeApiWebClient.get()
				.uri("/accounts/{id}/balance", id)
				.exchangeToMono(response -> {
					if (response.statusCode().equals(HttpStatus.NOT_FOUND)) {
						return Mono.empty();
					}
					return response.bodyToMono(ACCOUNT_BODY);
				});
	}

	private Mono<ResponseEntity<Object>> authorizeAndRespond(Subject subject, Map<String, Object> account, boolean caseOpen) {
		String ownerId = String.valueOf(account.get("ownerId"));
		AuthzInput input = new AuthzInput("view_balance", subject, new Resource(ownerId), new AuthzContext(caseOpen));

		return opaClient.decide(input).map(decision -> {
			auditLogger.logDecision(input, decision);

			if (!decision.allow()) {
				return ResponseEntity.status(HttpStatus.FORBIDDEN)
						.body((Object) Map.of("error", "access_denied"));
			}

			Map<String, Object> responseBody = MaskingUtil.applyMasking(account, decision.obligations().maskFields());
			return ResponseEntity.ok((Object) responseBody);
		});
	}
}
