# Proyecto Custodia

Laboratorio personal de autorización centralizada y protección de datos
sensibles — construido para practicar, con código real, las
responsabilidades del cargo de **Senior Software Engineer, Office Security**
(Mercado Libre).

- Especificación completa (arquitectura, todas las fases, matriz de
  trazabilidad al cargo): **[Proyecto Custodia — especificación](https://claude.ai/artifact/6nk6MMYdN4rnc4XM9PQQH5)**
- Manual de estudio que acompaña este laboratorio: **[Manual de Seguridad Java](https://claude.ai/artifact/NaFEWmgUZkcJHBGDAZQppp)**

## Estado actual: Fase 0 + Fase 1

- ✅ **Fase 0 — Diseño:** ver `docs/adr/`, `docs/threat-model.md`, `docs/architecture.md`.
- ✅ **Fase 1 — Núcleo de autorización:** `custodia-pep` (PEP) consulta a
  `custodia-pdp` (OPA, políticas en `policies/`) antes de servir datos de
  `finance-api-mock` (recurso protegido de referencia), aplicando
  enmascarado de campos según la decisión del PDP.
- ⬜ Fase 2 — Sensitive Data Gateway dedicado + KMS
- ⬜ Fase 3 — Token Broker para el agente de IA + OIDC real
- ⬜ Fase 4 — Pruebas de abuso automatizadas + shadow mode
- ⬜ Fase 5 — Observabilidad y runbook

## Estructura

```
policies/           Políticas Rego del PDP (Custodia PDP / OPA) + sus pruebas
docs/                ADRs, modelo de amenazas, arquitectura
finance-api-mock/    Recurso protegido de referencia (Spring Boot)
custodia-pep/        Policy Enforcement Point (Spring Boot WebFlux)
docker-compose.yml   Levanta PDP + finance-api-mock + custodia-pep juntos
```

## Cómo correrlo

```bash
docker compose up --build

curl -H "X-Employee-Id: emp-42" -H "X-Role: employee" -H "X-Risk-Score: 10" \
     http://localhost:8080/accounts/acc-1001/balance
# -> 200, saldo completo (dueño, riesgo bajo)

curl -H "X-Employee-Id: emp-1" -H "X-Role: employee" -H "X-Risk-Score: 10" \
     http://localhost:8080/accounts/acc-1001/balance
# -> 403, emp-1 no es dueño de acc-1001

curl -H "X-Employee-Id: emp-9" -H "X-Role: fraud_analyst" -H "X-Risk-Score: 40" \
     "http://localhost:8080/accounts/acc-1001/balance?caseOpen=true"
# -> 200, accountNumber enmascarado (****-****-****-6677)
```

## Cómo correr las pruebas

```bash
# Políticas (no requiere Java ni Maven)
opa test policies/ -v

# Servicios Java
mvn verify
```

> **Nota sobre esta primera entrega:** las políticas Rego (`policies/`) se
> verificaron localmente con `opa test` (7/7 casos pasan). El código Java se
> escribió y revisó con el mismo cuidado, pero no pudo compilarse en el
> entorno donde se generó por una restricción de red hacia Maven Central —
> la verificación real de `mvn verify` queda a cargo del workflow de CI
> (`.github/workflows/ci.yml`) en el primer push/PR a este repo.

## Decisiones de diseño relevantes

- **Fail-closed ante falla del PDP** — [ADR-0001](docs/adr/0001-fail-closed-vs-fail-open.md)
- **OPA/Rego ahora, Amazon Verified Permissions como comparación futura** — [ADR-0002](docs/adr/0002-opa-rego-vs-verified-permissions.md)
- **Headers de desarrollo en vez de JWT real en Fase 1** — ver
  `docs/architecture.md` § "Nota de alcance" y `docs/threat-model.md`
  (amenaza de Spoofing, mitigación completa prevista para Fase 3)
