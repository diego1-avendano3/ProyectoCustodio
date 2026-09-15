package com.custodia.financeapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Recurso protegido de referencia para el laboratorio Custodia.
 *
 * <p>Deliberadamente no tiene ninguna lógica de autorización: en la
 * arquitectura del proyecto (ver docs/architecture.md), esa responsabilidad
 * vive exclusivamente en custodia-pep. En un despliegue real, la red debería
 * impedir que este servicio sea alcanzable desde fuera del PEP (VPC privada
 * / security group / service mesh).</p>
 */
@SpringBootApplication
public class FinanceApiMockApplication {

	public static void main(String[] args) {
		SpringApplication.run(FinanceApiMockApplication.class, args);
	}
}
