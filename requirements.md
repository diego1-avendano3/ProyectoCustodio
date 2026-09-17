# Requisitos — Proyecto Custodia

**Formato:** EARS (Easy Approach to Requirements Syntax)
**Alcance cubierto:** Fase 0 (núcleo de autorización) y Fase 1 (validación E2E)
**Estado:** Formaliza retroactivamente lo ya implementado y probado; incluye items abiertos pendientes de decisión antes de Fase 2.
**Convención de ID:** `<AREA>-<NNN>`. Áreas usadas aquí: `AUTH` (decisión de autorización), `SEC` (seguridad/fail-safe), `DATA` (formato de datos expuestos).

---

## 1. Propósito y alcance

Este documento fija, en lenguaje no ambiguo, el comportamiento esperado del
Policy Enforcement Point (`custodia-pep`) al autorizar el acceso a
`/accounts/{id}/balance`. Cada requisito tiene un identificador estable que
debe poder rastrearse a: (a) la regla Rego que lo implementa en
`policies/authz.rego`, y (b) el test automatizado que lo verifica en
`custodia-pep/src/test/java/com/custodia/pep/AuthorizationE2EIT.java`.

Este documento no reemplaza las Architecture Decision Records existentes en
`docs/`; las complementa a nivel de comportamiento observable del sistema.

---

## 2. Actores y términos

- **Empleado (`employee`)**: usuario autenticado que puede consultar el
  saldo de una cuenta.
- **Analista de fraude (`fraud_analyst`)**: rol con permiso de investigación
  sobre cuentas que no le pertenecen, únicamente durante un caso abierto.
- **`risk_score`**: puntaje numérico de riesgo asociado a la sesión/solicitud
  del empleado, entre 0 y 100.
- **`case_open`**: indicador de si existe una investigación de fraude activa
  sobre la cuenta solicitada.
- **Owner**: el empleado cuyo `employee_id` coincide con el `owner_id` de la
  cuenta.
- **Enmascarado**: el número de cuenta se devuelve con el formato
  `****-****-****-XXXX`, mostrando solo los últimos 4 dígitos.

---

## 3. Requisitos de Fase 0 — Núcleo de autorización

**AUTH-000** — El PEP shall evaluar toda solicitud a un recurso protegido
contra el PDP (OPA) antes de reenviarla al servicio protegido.

**SEC-001** — If el PDP no responde, responde con error, o responde con una
decisión que el PEP no puede interpretar, then el PEP shall denegar el
acceso (fail-closed) con status 403, sin excepciones.
> **Estado:** implementado por diseño (principio arquitectónico
> "fail-closed" documentado en ADRs previas). **No cubierto** actualmente
> por ningún test automatizado en `AuthorizationE2EIT` — ver sección 6.

**SEC-002** — El PEP shall denegar por defecto cualquier combinación de
`role`/`risk_score`/`case_open` que no coincida explícitamente con una de
las reglas de permiso definidas en `policies/authz.rego` (deny-by-default).

---

## 4. Requisitos de Fase 1 — Matriz de autorización de balance

**AUTH-001** — When un empleado solicita el saldo de una cuenta de la cual
es owner y su `risk_score` es menor a 70, el PEP shall devolver status 200
con el saldo completo, incluyendo el número de cuenta sin enmascarar.
> Verificado por: `ownerWithLowRisk_getsFullBalance`.

**AUTH-002** — When un empleado solicita el saldo de una cuenta de la cual
no es owner, el PEP shall denegar el acceso con status 403 y cuerpo
`{"error": "access_denied"}`, independientemente de su `risk_score`.
> Verificado por: `nonOwner_isDenied`.

**AUTH-003** — When un empleado solicita el saldo de una cuenta de la cual
es owner y su `risk_score` está entre 70 y 89 (ambos inclusive), el PEP
shall devolver status 200 con el saldo, con el número de cuenta enmascarado.
> Verificado por: `ownerWithHighRisk_getsBalanceWithMaskedAccountNumber`.

**AUTH-004** — When un analista de fraude solicita el saldo de una cuenta
sobre la cual no hay un caso abierto (`case_open == false` o ausente), el
PEP shall denegar el acceso con status 403.
> Verificado por: `fraudAnalystWithoutOpenCase_isDenied`.

**AUTH-005** — When un analista de fraude solicita el saldo de una cuenta
sobre la cual hay un caso abierto (`case_open == true`), el PEP shall
devolver status 200 con el saldo, con el número de cuenta enmascarado,
independientemente de su `risk_score`.
> Verificado por: `fraudAnalystWithOpenCase_getsBalanceWithMaskedAccountNumber`.

**DATA-001** — Cuando el PEP deba enmascarar un número de cuenta, el PEP
shall conservar únicamente los últimos 4 dígitos, reemplazando el resto por
el patrón `****-****-****-`.

---

## 5. Mapeo de headers y parámetros de entrada

El PEP shall interpretar las siguientes señales de la solicitud HTTP como
entrada a la decisión de autorización:

| Señal HTTP                     | Campo en input del PDP     | Obligatorio |
|---------------------------------|-----------------------------|-------------|
| Header `X-Employee-Id`          | `input.subject.employee_id` | Sí          |
| Header `X-Role`                 | `input.subject.role`        | Sí          |
| Header `X-Risk-Score`           | `input.subject.risk_score`  | Sí          |
| Query param `caseOpen`          | `input.context.case_open`   | No (default `false`) |
| `{id}` de la ruta                | `input.resource.owner_id` (resuelto contra el dueño real de la cuenta) | Sí |

**SEC-003** — If falta alguno de los headers obligatorios (`X-Employee-Id`,
`X-Role`, `X-Risk-Score`) en la solicitud, then el PEP shall denegar el
acceso con status 403 (consecuencia directa de SEC-002: el input incompleto
no calza con ninguna regla de permiso).
> **No cubierto** actualmente por ningún test automatizado — ver sección 6.

---

## 6. Items abiertos (requieren decisión antes de continuar)

**AUTH-006 — Umbral superior de `risk_score` (DECIDIDO — 2026-09-17)**

When un empleado solicita el saldo de una cuenta de la cual es owner y su
`risk_score` es 90 o mayor, el PEP shall denegar el acceso por completo con
status 403, incluso siendo el dueño de la cuenta.

> **Decisión:** se mantiene la denegación total (opción recomendada), en
> línea con un enfoque fail-closed estricto: a partir de riesgo extremo, ni
> siquiera el dueño puede consultar su propio saldo. No se introduce MFA ni
> ninguna vía alterna de acceso en esta fase.
>
> **Estado de implementación:** la política actual (`policies/authz.rego`)
> ya produce este resultado de forma *implícita*, vía el deny-by-default de
> SEC-002 (nunca hubo una regla de permiso para `risk_score >= 90`). Como el
> comportamiento ahora es una decisión explícita y no un vacío accidental,
> queda pendiente como tarea de higiene: (1) agregar un comentario explícito
> en `policies/authz.rego` dejando constancia de que ese rango se deniega a
> propósito y no por omisión, y (2) agregar un sexto escenario a
> `AuthorizationE2EIT` (p. ej. `ownerWithExtremeRisk_isDenied`) que lo
> verifique, ya que hoy pasa "por accidente" y no por una prueba dedicada.

**SEC-001 / SEC-003 — Cobertura de pruebas fail-closed (PENDIENTE)**

Ambos requisitos de "fallo seguro" (PDP caído, headers faltantes) están
implementados por diseño pero **no tienen un test automatizado que los
verifique explícitamente**. Se recomienda agregar dos escenarios a
`AuthorizationE2EIT` (o una suite separada) antes de dar por robusta la
Fase 1: uno que detenga/desconecte el contenedor de OPA a mitad de la
prueba y verifique 403, y otro que omita un header obligatorio y verifique
403.

---

## 7. Trazabilidad (resumen)

| ID | Descripción corta | Regla Rego | Test automatizado | Estado |
|----|--------------------|------------|--------------------|--------|
| AUTH-000 | Toda solicitud pasa por el PDP | (arquitectura) | Indirecto (todos los tests) | Implementado |
| SEC-001 | Fail-closed si el PDP falla | (arquitectura) | — | **Falta test** |
| SEC-002 | Deny-by-default | `default allow := false` (o equivalente) | Indirecto (AUTH-002, AUTH-004) | Implementado |
| SEC-003 | Deny si faltan headers | (consecuencia de SEC-002) | — | **Falta test** |
| AUTH-001 | Owner, riesgo bajo → balance completo | Regla de auto-vista | `ownerWithLowRisk_getsFullBalance` | Implementado y probado |
| AUTH-002 | No-owner → denegado | Deny-by-default | `nonOwner_isDenied` | Implementado y probado |
| AUTH-003 | Owner, riesgo 70–89 → enmascarado | Regla de auto-vista riesgo alto | `ownerWithHighRisk_getsBalanceWithMaskedAccountNumber` | Implementado y probado |
| AUTH-004 | Fraud analyst sin caso → denegado | Deny-by-default | `fraudAnalystWithoutOpenCase_isDenied` | Implementado y probado |
| AUTH-005 | Fraud analyst con caso → enmascarado | Regla de analista de fraude | `fraudAnalystWithOpenCase_getsBalanceWithMaskedAccountNumber` | Implementado y probado |
| AUTH-006 | Owner, riesgo ≥90 → denegado por completo | Deny-by-default (implícito; falta comentario explícito) | — | Decidido; **falta test dedicado** |
| DATA-001 | Formato de enmascarado | Regla de auto-vista riesgo alto / analista | Verificado indirectamente (AUTH-003, AUTH-005) | Implementado y probado |

---

## 8. Cómo mantener este documento

Cualquier cambio a `policies/authz.rego` que afecte una decisión de
autorización shall venir acompañado de una actualización a este documento
(nuevo requisito, o modificación de uno existente con nota de versión) y de
un test correspondiente en `AuthorizationE2EIT`. Un PR que modifique la
lógica de autorización sin tocar este archivo debe considerarse incompleto
en revisión de código.
