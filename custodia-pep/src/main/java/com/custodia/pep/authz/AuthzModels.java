package com.custodia.pep.authz;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Documento `input` que custodia-pep envía a Custodia PDP (OPA), y la forma
 * de la decisión que recibe de vuelta. El contrato JSON exacto está
 * documentado en docs/architecture.md — cualquier cambio aquí debe
 * reflejarse también en policies/authz.rego y en ese documento.
 */
public final class AuthzModels {

	private AuthzModels() {
	}

	public record Subject(
			@JsonProperty("employee_id") String employeeId,
			@JsonProperty("role") String role,
			@JsonProperty("risk_score") int riskScore
	) {
	}

	public record Resource(
			@JsonProperty("owner_id") String ownerId
	) {
	}

	public record AuthzContext(
			@JsonProperty("case_open") boolean caseOpen
	) {
	}

	public record AuthzInput(
			@JsonProperty("action") String action,
			@JsonProperty("subject") Subject subject,
			@JsonProperty("resource") Resource resource,
			@JsonProperty("context") AuthzContext context
	) {
	}

	/** Envoltorio que espera la API REST de OPA: {"input": {...}}. */
	public record OpaRequest(
			@JsonProperty("input") AuthzInput input
	) {
	}

	public record Obligations(
			@JsonProperty("mask_fields") List<String> maskFields
	) {
		public static Obligations none() {
			return new Obligations(List.of());
		}
	}

	public record AuthzDecision(
			@JsonProperty("allow") boolean allow,
			@JsonProperty("obligations") Obligations obligations
	) {
		/**
		 * Decisión aplicada cuando el PDP no responde o excede el timeout.
		 * Implementa ADR-0001 (fail-closed): nunca se interpreta la ausencia
		 * de respuesta como autorización implícita.
		 */
		public static AuthzDecision denyClosed() {
			return new AuthzDecision(false, Obligations.none());
		}
	}

	/** Envoltorio de la respuesta REST de OPA: {"result": {...}}. */
	public record OpaResponse(
			@JsonProperty("result") AuthzDecision result
	) {
	}
}
