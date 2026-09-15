# ADR 0001 — Postura ante falla del PDP: fail-closed

**Estado:** Aceptado
**Fecha:** Fase 0
**Fase:** 0 — Diseño

## Contexto

Custodia PEP consulta a Custodia PDP (OPA) en cada solicitud antes de servir
un recurso de la Finance API. Si el PDP no responde (timeout, caída, error
de red), el PEP debe decidir qué hacer con la solicitud entrante: dejarla
pasar (*fail-open*) o rechazarla (*fail-closed*).

## Decisión

Custodia PEP opera en **fail-closed**: si la llamada al PDP falla o excede el
timeout configurado, la solicitud se deniega (`403`) y se registra como
incidente de disponibilidad del PDP, nunca como autorización implícita.

## Alternativas consideradas

- **Fail-open**: prioriza disponibilidad — el sistema sigue sirviendo tráfico
  aunque el PDP esté caído. Se descartó porque, tratándose de datos
  financieros, una ventana de acceso sin control de autorización es
  inaceptable aunque sea breve.
- **Fail-open con cache de la última decisión conocida**: reduce el impacto
  de una caída breve del PDP sin exponer recursos nuevos, pero introduce
  complejidad de invalidación de caché que no se justifica en el alcance de
  este laboratorio (queda anotado como extensión futura).

## Consecuencias

- Una caída del PDP se convierte en una caída parcial del sistema (los
  endpoints protegidos dejan de responder). Esto es deliberado y debe
  comunicarse como trade-off explícito: **confidencialidad sobre
  disponibilidad** en un sistema que protege datos financieros.
- El PDP debe diseñarse para alta disponibilidad (réplicas, health checks)
  precisamente porque una falla suya ahora tiene consecuencia directa en
  disponibilidad del sistema protegido.
- El timeout de la llamada al PDP debe ser corto y explícito (ver
  `custodia.opa.timeout-ms` en `application.yml` de `custodia-pep`) para que
  una denegación por timeout sea rápida y no bloquee al cliente.
