# Modelo de amenazas (STRIDE) — Proyecto Custodia

**Fase:** 0 — Diseño
**Alcance:** flujo `cliente/agente → API Gateway → Custodia PEP → Custodia PDP → Sensitive Data Gateway → Finance API` descrito en `docs/architecture.md`.

| Categoría | Amenaza concreta en Custodia | Mitigación | Dónde vive |
|---|---|---|---|
| **S**poofing | Un cliente falsifica el `employee_id` en el request para hacerse pasar por otro empleado | Fase 1: header de desarrollo firmado por el propio PEP en el contexto local; Fase 3: identidad verificada vía JWT/OIDC validado contra el issuer, nunca confiado del cliente | `custodia-pep` / futura integración OIDC |
| **T**ampering | Alterar el payload de la política Rego en tránsito hacia OPA, o modificar la respuesta de OPA hacia el PEP | Comunicación PEP↔PDP dentro de la red interna del docker-compose / VPC; en producción, mTLS entre ambos | `docker-compose.yml`, roadmap Fase futura |
| **R**epudiation | Un actor niega haber consultado el saldo de una cuenta ajena | Cada decisión (permitir/denegar) se audita con principal, recurso, acción y resultado antes de responder al cliente | `custodia-pep/audit/AuditLogger.java` |
| **I**nformation disclosure | El número de cuenta completo se filtra a un rol que solo debería ver saldo agregado | `obligations.mask_fields` de la política se aplica siempre antes de que la respuesta salga del PEP, nunca queda a discreción del cliente | `policies/authz.rego`, `MaskingUtil` |
| **D**enial of service | Un cliente abusa del endpoint de saldo con miles de requests por segundo | Rate limiting en el borde (API Gateway, fuera del alcance de este repo en Fase 1; queda anotado para Fase 4) | Roadmap Fase 4 |
| **E**levation of privilege | Un usuario regular intenta invocar una acción reservada a `fraud_analyst` cambiando el header de rol | El rol declarado en el request es una simplificación de desarrollo (Fase 1); pasa a venir de un token firmado no falsificable en Fase 3 | `docs/adr/` (nota de alcance), Fase 3 |

## Decisión de alcance explícita para Fase 1

Fase 1 se enfoca en demostrar el **mecanismo de autorización centralizada**
(PEP + PDP evaluando una política real) contra una amenaza de
**Information disclosure / Elevation of privilege**. La amenaza de
**Spoofing** de la identidad del sujeto se mitiga completamente solo hasta
Fase 3, cuando el Token Broker y la validación OIDC reemplazan los headers
de desarrollo. Esto se documenta explícitamente aquí para que la limitación
sea una decisión visible, no un descuido.
