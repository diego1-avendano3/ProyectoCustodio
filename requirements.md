# Requisitos — Proyecto Custodia

**Formato:** EARS (Easy Approach to Requirements Syntax)
**Alcance cubierto:** Fase 0 (núcleo de autorización), Fase 1 (validación
E2E) — cerradas — y Fase 2 (Sensitive Data Gateway + AWS KMS) — en
definición, ver sección 9.
**Estado Fase 0-1:** cerrada — todos los requisitos están implementados y
verificados por al menos un test automatizado en CI (última verificación:
2026-09-17, PRs #2, #3 y #4).
**Estado Fase 2:** requisitos recién definidos (2026-09-17), a partir de
RF2 y la sección de arquitectura de la especificación maestra
(https://claude.ai/artifact/6nk6MMYdN4rnc4XM9PQQH5). Aún sin implementar.
**Convención de ID:** `<AREA>-<NNN>`. Áreas usadas aquí: `AUTH` (decisión
de autorización), `SEC` (seguridad/fail-safe), `DATA` (formato de datos
expuestos), `GW` (Sensitive Data Gateway, Fase 2).

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
> **Estado:** implementado (`OpaClient.decide`, vía `onErrorResume` →
> `AuthzDecision.denyClosed()`) y verificado por
> `PdpFailClosedIT.pdpUnavailable_denysAccessByDefault` (apunta
> `custodia.opa.base-url` a un puerto sin nada escuchando y confirma 403),
> además del test unitario `OpaClientFailClosedTest`.

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
`X-Role`, `X-Risk-Score`) o `X-Risk-Score` no es numérico, then el PEP shall
rechazar la solicitud con status **400 Bad Request** (no 403: es una
solicitud incompleta/malformada, no una decisión de política sobre una
solicitud válida — distinción que sigue el mismo patrón que ya usaba el
código para `X-Employee-Id` faltante y para `X-Risk-Score` no numérico).
> **Estado:** implementado en `DevHeaderSubjectResolver`
> (`MissingSubjectHeaderException`, mapeada a 400) y verificado por
> `AuthorizationE2EIT.missingRiskScoreHeader_isDenied` a nivel E2E, y por
> `DevHeaderSubjectResolverTest.rejectsMissingRiskScoreHeader`,
> `rejectsRequestsWithoutEmployeeIdHeader` y `rejectsNonNumericRiskScore` a
> nivel unitario.
>
> **Hallazgo relevante (2026-09-17):** hasta este cierre de Fase 1,
> `X-Risk-Score` ausente **no** se rechazaba — se interpretaba como
> `risk_score = 0` (el valor más permisivo posible), lo que le daba a un
> owner con ese header omitido acceso al balance completo sin enmascarar
> (`200 OK`, no un error). Confirmado con este mismo test antes del fix.
> Corregido en `DevHeaderSubjectResolver.parseRiskScore` (PR #3) y
> alineado el test unitario preexistente que documentaba el default
> permisivo a propósito (PR #4). El default de `role="employee"` cuando
> el header está ausente **sí se mantiene**, porque `employee` es el rol
> de menor privilegio del sistema y no abre una vía de acceso adicional.

---

## 6. Items abiertos

**AUTH-006 — Umbral superior de `risk_score` (DECIDIDO Y VERIFICADO — 2026-09-17)**

When un empleado solicita el saldo de una cuenta de la cual es owner y su
`risk_score` es 90 o mayor, el PEP shall denegar el acceso por completo con
status 403, incluso siendo el dueño de la cuenta.

> **Decisión:** se mantiene la denegación total, en línea con un enfoque
> fail-closed estricto: a partir de riesgo extremo, ni siquiera el dueño
> puede consultar su propio saldo. No se introduce MFA ni ninguna vía
> alterna de acceso en esta fase.
>
> **Estado:** verificado por `ownerWithExtremeRisk_isDenied`. La política
> (`policies/authz.rego`) produce este resultado vía el deny-by-default de
> SEC-002 (no hay regla de permiso para `risk_score >= 90`).
>
> **Pendiente menor (no bloqueante):** agregar un comentario explícito en
> `policies/authz.rego` dejando constancia de que ese rango se deniega a
> propósito y no por omisión — es una tarea de higiene documental, el
> comportamiento ya está probado.

No quedan items abiertos que bloqueen el cierre de Fase 0-1. Ver sección 7
para la trazabilidad completa.

---

## 7. Trazabilidad (resumen)

| ID | Descripción corta | Regla Rego | Test automatizado | Estado |
|----|--------------------|------------|--------------------|--------|
| AUTH-000 | Toda solicitud pasa por el PDP | (arquitectura) | Indirecto (todos los tests) | Implementado |
| SEC-001 | Fail-closed si el PDP falla | `OpaClient.decide` (onErrorResume) | `PdpFailClosedIT`, `OpaClientFailClosedTest` | Implementado y probado |
| SEC-002 | Deny-by-default | `default decision := {"allow": false, ...}` | Indirecto (AUTH-002, AUTH-004) | Implementado y probado |
| SEC-003 | 400 si faltan/son inválidos los headers | `DevHeaderSubjectResolver` | `AuthorizationE2EIT.missingRiskScoreHeader_isDenied`, `DevHeaderSubjectResolverTest` (3 casos) | Implementado y probado |
| AUTH-001 | Owner, riesgo bajo → balance completo | Regla de auto-vista | `ownerWithLowRisk_getsFullBalance` | Implementado y probado |
| AUTH-002 | No-owner → denegado | Deny-by-default | `nonOwner_isDenied` | Implementado y probado |
| AUTH-003 | Owner, riesgo 70–89 → enmascarado | Regla de auto-vista riesgo alto | `ownerWithHighRisk_getsBalanceWithMaskedAccountNumber` | Implementado y probado |
| AUTH-004 | Fraud analyst sin caso → denegado | Deny-by-default | `fraudAnalystWithoutOpenCase_isDenied` | Implementado y probado |
| AUTH-005 | Fraud analyst con caso → enmascarado | Regla de analista de fraude | `fraudAnalystWithOpenCase_getsBalanceWithMaskedAccountNumber` | Implementado y probado |
| AUTH-006 | Owner, riesgo ≥90 → denegado por completo | Deny-by-default (falta comentario explícito, no bloqueante) | `ownerWithExtremeRisk_isDenied` | Implementado y probado |
| DATA-001 | Formato de enmascarado | Regla de auto-vista riesgo alto / analista | Verificado indirectamente (AUTH-003, AUTH-005) | Implementado y probado |

---

## 8. Cómo mantener este documento

Cualquier cambio a `policies/authz.rego` que afecte una decisión de
autorización shall venir acompañado de una actualización a este documento
(nuevo requisito, o modificación de uno existente con nota de versión) y de
un test correspondiente en `AuthorizationE2EIT`. Un PR que modifique la
lógica de autorización sin tocar este archivo debe considerarse incompleto
en revisión de código.

---

## 9. Requisitos de Fase 2 — Sensitive Data Gateway + AWS KMS

Fuente: RF2 ("Protección de datos sensibles") y la sección de Arquitectura
de la especificación maestra del proyecto. Fase 1 ya resuelve el
enmascarado de campos en la *respuesta* (`MaskingUtil`, según las
`obligations` del PDP); Fase 2 añade una capa dedicada y **cifrado en
reposo** de los campos sensibles — algo que hoy no existe.

### 9.1 Decisiones de entorno (tomadas 2026-09-17)

- **KMS:** se usa **LocalStack** en desarrollo y CI (sin costo, sin
  credenciales de AWS reales, API 1:1 compatible con KMS real — migrar a
  una cuenta de AWS real después es un cambio de endpoint, no de código).
- **Almacenamiento:** `finance-api-mock` deja de guardar datos en memoria y
  pasa a **PostgreSQL**, reutilizando el esquema conceptual de Fase 1
  (`id`, `ownerId`, `accountNumber`, `balance`, `currency`).

### 9.2 Términos nuevos

- **Sensitive Data Gateway (SDG):** único componente con permiso para leer
  o escribir en crudo los campos marcados como sensibles.
- **Envelope encryption:** patrón de cifrado donde cada valor se cifra con
  una *data key* generada localmente; esa data key, a su vez, se cifra con
  la Customer Master Key (CMK) que vive en KMS y nunca sale de KMS en
  claro. Solo el valor cifrado y la data key cifrada se guardan en
  PostgreSQL.
- **Campo sensible (Fase 2):** `accountNumber` y `balance` — coincide con
  "número de cuenta" y "monto exacto" que RF2 nombra explícitamente.

### 9.3 Requisitos

**GW-001** — El Sensitive Data Gateway shall ser el único componente con
acceso a los valores en texto plano de los campos sensibles; ni
`custodia-pep` ni el resto de `finance-api-mock` deben leer o escribir esos
campos directamente en la base de datos.

**GW-002** — When `finance-api-mock` persiste una cuenta nueva o actualiza
uno de sus campos sensibles, el Sensitive Data Gateway shall cifrar ese
campo por envelope encryption (data key generada y cifrada vía KMS) antes
de que el valor llegue a PostgreSQL.

**GW-003** — When el Sensitive Data Gateway necesita devolver un campo
sensible en texto plano (porque las `obligations` del PDP no piden
enmascararlo para ese sujeto), el Sensitive Data Gateway shall pedirle a
KMS descifrar la data key correspondiente, usarla para descifrar el campo
en memoria, y nunca persistir el valor descifrado.

**GW-004** — El Sensitive Data Gateway shall aplicar `obligations.mask_fields`
sobre el valor ya descifrado, reproduciendo exactamente el comportamiento
de enmascarado ya verificado en Fase 1 (mismo formato que `DATA-001`).

**GW-005** — `finance-api-mock` shall persistir sus datos en PostgreSQL en
vez de en memoria, con `accountNumber` y `balance` almacenados siempre en
su forma cifrada (nunca en texto plano en la base de datos).

**SEC-004** — Ningún componente del sistema shall escribir en logs el valor
en texto plano de un campo sensible, en ningún punto del ciclo de
cifrado/descifrado.
> Corresponde al RNF explícito de la especificación: "Ningún campo PII
> aparece en texto plano en logs".

**SEC-005** — If KMS no está disponible o una operación de cifrado o
descifrado falla, then el Sensitive Data Gateway shall rechazar la
operación (falla la escritura; la lectura devuelve error, nunca el valor
sin descifrar ni un valor por defecto) — mismo principio fail-closed de
ADR-0001, aplicado ahora a la capa de datos en vez de a la capa de
autorización.

### 9.4 Pendiente de definir antes de implementar

Estas son decisiones de diseño más finas que todavía no están resueltas y
que conviene fijar (con su propio mini-ADR, según RF5) antes de escribir
código:

- **Forma del componente:** ¿el Sensitive Data Gateway es un módulo Maven
  nuevo dentro del reactor (`sensitive-data-gateway`), una librería
  compartida que `finance-api-mock` importa, o un servicio HTTP
  independiente? La especificación lo dibuja como un componente propio en
  el diagrama de arquitectura; por defecto se asume **módulo Maven nuevo**
  embebido en el proceso de `finance-api-mock` (evita otro salto de red y
  otro punto de falla en el laboratorio), pero es una decisión a
  confirmar.
- **Rotación de llaves KMS:** para este laboratorio se asume la rotación
  automática anual por defecto de KMS, sin lógica adicional propia — no es
  un requisito de Fase 2, se anota aquí para no perderlo de vista si surge
  en la entrevista.
- **Migración de datos existentes:** Fase 1 no tenía base de datos
  (`finance-api-mock` servía datos en memoria/hardcodeados), así que no
  hay datos previos que migrar a PostgreSQL — el seed inicial se define de
  cero en Fase 2.
