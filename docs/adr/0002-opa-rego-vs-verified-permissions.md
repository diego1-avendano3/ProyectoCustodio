# ADR 0002 — Motor de políticas: OPA/Rego en el laboratorio (Amazon Verified Permissions como comparación futura)

**Estado:** Aceptado para Fase 1–4
**Fecha:** Fase 0
**Fase:** 0 — Diseño

## Contexto

El modelo de autorización necesita un motor de decisión de políticas (PDP)
separado de la lógica de negocio del servicio protegido. Dos opciones
razonables: **Open Policy Agent (OPA/Rego)**, autogestionado, o **Amazon
Verified Permissions**, administrado por AWS y basado en el lenguaje **Cedar**.

## Decisión

El laboratorio arranca con **OPA/Rego** corriendo como contenedor sidecar:

- No requiere cuenta ni costo de AWS para las Fases 0–1, así que el ciclo de
  desarrollo local es inmediato (`docker compose up`).
- Rego es el motor más usado en el ecosistema de autorización como código
  fuera de AWS, y es directamente transferible a cualquier stack.
- El patrón PEP/PDP que se construye aquí es idéntico si mañana el PDP
  cambia de implementación — el contrato entre `custodia-pep` y el motor de
  políticas es una llamada HTTP con un documento `input` y una decisión de
  vuelta.

## Alternativa evaluada

**Amazon Verified Permissions (Cedar)** queda planeada explícitamente como
comparación en el roadmap (ver `docs/architecture.md`, sección Roadmap):
migrar el mismo conjunto de políticas a Cedar permite hablar en la entrevista
con conocimiento de primera mano de ambos enfoques — Rego es más expresivo y
portable; Cedar está diseñado para ser analizable formalmente (se puede
probar que una política nunca permite cierto acceso) y elimina la carga
operativa de correr el motor.

## Consecuencias

- El límite entre PEP y PDP se mantiene estrictamente como una llamada de
  red con un contrato JSON estable (`docs/architecture.md`), para que
  cambiar el motor de políticas no obligue a tocar `custodia-pep`.
- Las políticas viven en `policies/`, versionadas independientemente del
  código de los servicios, con sus propias pruebas (`policies/authz_test.rego`).
