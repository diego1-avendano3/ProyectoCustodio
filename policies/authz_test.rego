package officesec.authz

# Ejecutar con: opa test policies/ -v

test_owner_can_view_own_balance_low_risk if {
	decision.allow with input as {
		"action": "view_balance",
		"subject": {"employee_id": "emp-42", "role": "employee", "risk_score": 10},
		"resource": {"owner_id": "emp-42"},
		"context": {"case_open": false},
	}
}

test_owner_denied_very_high_risk if {
	not decision.allow with input as {
		"action": "view_balance",
		"subject": {"employee_id": "emp-42", "role": "employee", "risk_score": 95},
		"resource": {"owner_id": "emp-42"},
		"context": {"case_open": false},
	}
}

test_stranger_denied if {
	not decision.allow with input as {
		"action": "view_balance",
		"subject": {"employee_id": "emp-1", "role": "employee", "risk_score": 5},
		"resource": {"owner_id": "emp-42"},
		"context": {"case_open": false},
	}
}

test_owner_medium_risk_allowed_but_masked if {
	d := decision with input as {
		"action": "view_balance",
		"subject": {"employee_id": "emp-42", "role": "employee", "risk_score": 80},
		"resource": {"owner_id": "emp-42"},
		"context": {"case_open": false},
	}

	d.allow
	d.obligations.mask_fields[_] == "accountNumber"
}

test_fraud_analyst_with_open_case_allowed_and_masked if {
	d := decision with input as {
		"action": "view_balance",
		"subject": {"employee_id": "emp-9", "role": "fraud_analyst", "risk_score": 40},
		"resource": {"owner_id": "emp-42"},
		"context": {"case_open": true},
	}

	d.allow
	d.obligations.mask_fields[_] == "accountNumber"
}

test_fraud_analyst_without_open_case_denied if {
	not decision.allow with input as {
		"action": "view_balance",
		"subject": {"employee_id": "emp-9", "role": "fraud_analyst", "risk_score": 40},
		"resource": {"owner_id": "emp-42"},
		"context": {"case_open": false},
	}
}

test_fraud_analyst_own_account_uses_owner_rule_not_masked if {
	d := decision with input as {
		"action": "view_balance",
		"subject": {"employee_id": "emp-9", "role": "fraud_analyst", "risk_score": 10},
		"resource": {"owner_id": "emp-9"},
		"context": {"case_open": true},
	}

	d.allow
	count(d.obligations.mask_fields) == 0
}
