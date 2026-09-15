# Arquitectura — Proyecto Custodia

**Fase:** 0 — Diseño (actualizado al cierre de Fase 1)

## Visión completa

La arquitectura completa del laboratorio (Custodia PEP, Custodia PDP,
Sensitive Data Gateway, Token Broker para agentes de IA, Auditoría) está
documentada con diagrama en la especificación original:

> https://claude.ai/artifact/6nk6MMYdN4rnc4XM9PQQH5 — "Proyecto Custodia"

Este archivo describe el subconjunto que efectivamente vive en este
repositorio a partir de Fase 1, y se irá ampliando fase a fase.

## Alcance construido en Fase 1

```
Cliente (dev)
   │  GET /accounts/{id}/balance
   │  headers: X-Employee-Id, X-Role, X-Risk-Score   (simulan el sujeto; ver nota abajo)
   ▼
custodia-pep  (Spring Boot WebFlux, :8080)
   │  1) arma el documento `input` (subject, resource, action, context)
   │  2) POST al PDP con ese input
   ▼
OPA — Custodia PDP (:8181)
   │  evalúa policies/authz.rego
   │  responde { allow, obligations }
   ▼
custodia-pep
   │  si allow=false  → 403, se audita la denegación, no se llama a Finance API
   │  si allow=true   → aplica obligations.mask_fields sobre la respuesta
   ▼
finance-api-mock  (Spring Boot, :8081)
   representa el recurso protegido (cuentas/saldos) — stand-in local del
   proyecto Personal Finance & Reminder Manager mientras ese proyecto no
   está en este repo
```

## Nota de alcance: identidad del sujeto en Fase 1

`custodia-pep` construye el `subject` del documento `input` a partir de
headers HTTP (`X-Employee-Id`, `X-Role`, `X-Risk-Score`) en lugar de un JWT
verificado. Es una simplificación deliberada para poder probar el
**mecanismo de autorización por política** de punta a punta sin resolver
todavía la integración OIDC completa — ver `docs/threat-model.md` para la
amenaza que esto deja abierta y `docs/adr/` para las decisiones relacionadas.
Fase 3 reemplaza estos headers por tokens emitidos por un Identity Provider
real (Keycloak local / Cognito en AWS) y valida la firma en
`custodia-pep` antes de construir el `subject`.

## Contrato PEP ↔ PDP

Request de `custodia-pep` a OPA:

```json
POST /v1/data/officesec/authz
{
  "input": {
    "action": "view_balance",
    "subject": { "employee_id": "emp-42", "role": "employee", "risk_score": 10 },
    "resource": { "owner_id": "emp-42" },
    "context": { "case_open": false }
  }
}
```

Respuesta de OPA:

```json
{
  "result": {
    "allow": true,
    "obligations": { "mask_fields": [] }
  }
}
```

Este contrato es estable independientemente de si el PDP detrás es OPA o,
más adelante, Amazon Verified Permissions (ver `docs/adr/0002-*.md`).

## Roadmap (fases siguientes, resumen — detalle completo en la especificación enlazada arriba)

- **Fase 2:** Sensitive Data Gateway dedicado + cifrado de campos con AWS KMS.
- **Fase 3:** Token Broker (RFC 8693) para el agente de IA; reemplazo de los
  headers de desarrollo por JWT/OIDC real.
- **Fase 4:** casos de abuso automatizados en CI, *shadow mode* de políticas.
- **Fase 5:** observabilidad (OpenTelemetry) y runbook de incidentes.
