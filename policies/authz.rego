package officesec.authz

# Custodia PDP — política de autorización para el recurso "balance" de la
# Finance API. Ver docs/architecture.md para el contrato de `input` y de la
# decisión devuelta. Escrita en sintaxis Rego v1 (OPA >= 0.59).
#
# Deny-by-default: si ninguna regla de más abajo produce una decisión, se usa
# este default (mínimo privilegio).
default decision := {"allow": false, "obligations": {"mask_fields": []}}

# Un empleado puede ver su propio saldo mientras su score de riesgo esté
# por debajo del umbral configurado.
decision := {"allow": true, "obligations": {"mask_fields": []}} if {
	input.action == "view_balance"
	input.resource.owner_id == input.subject.employee_id
	input.subject.risk_score < 70
}

# Un analista de fraude puede ver el saldo de una cuenta ajena mientras
# exista un caso abierto sobre ella — pero el número de cuenta se enmascara:
# el acceso está justificado por el caso, no por ser dueño del recurso.
# La condición `owner_id != employee_id` evita que esta regla compita con la
# de autoconsulta cuando un analista mira su propia cuenta (ambas producirían
# una decisión distinta para el mismo input, lo cual OPA rechaza en tiempo de
# evaluación por ser un conflicto de reglas completas).
decision := {"allow": true, "obligations": {"mask_fields": ["accountNumber"]}} if {
	input.subject.role == "fraud_analyst"
	input.action == "view_balance"
	input.context.case_open == true
	input.resource.owner_id != input.subject.employee_id
}

# Un empleado con score de riesgo alto solo puede ver su propio saldo con
# los campos sensibles enmascarados, nunca lectura completa.
decision := {"allow": true, "obligations": {"mask_fields": ["accountNumber"]}} if {
	input.action == "view_balance"
	input.resource.owner_id == input.subject.employee_id
	input.subject.risk_score >= 70
	input.subject.risk_score < 90
}
