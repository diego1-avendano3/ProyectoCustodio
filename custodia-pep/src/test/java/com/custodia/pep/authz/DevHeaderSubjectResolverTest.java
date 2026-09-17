package com.custodia.pep.authz;

import com.custodia.pep.authz.AuthzModels.Subject;
import com.custodia.pep.authz.DevHeaderSubjectResolver.MissingSubjectHeaderException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DevHeaderSubjectResolverTest {

    private final DevHeaderSubjectResolver resolver = new DevHeaderSubjectResolver();

    @Test
    void resolvesSubjectFromHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(DevHeaderSubjectResolver.HEADER_EMPLOYEE_ID, "emp-42");
        headers.set(DevHeaderSubjectResolver.HEADER_ROLE, "fraud_analyst");
        headers.set(DevHeaderSubjectResolver.HEADER_RISK_SCORE, "40");

        Subject subject = resolver.resolve(headers);

        assertThat(subject).isEqualTo(new Subject("emp-42", "fraud_analyst", 40));
    }

    @Test
    void defaultsRoleToEmployeeWhenAbsent() {
        // El rol ausente sigue teniendo un default seguro: "employee" es el
        // rol de menor privilegio del sistema, no una elevacion. A
        // diferencia de risk_score (ver rejectsMissingRiskScoreHeader), un
        // valor por defecto aqui no abre una via de acceso adicional.
        HttpHeaders headers = new HttpHeaders();
        headers.set(DevHeaderSubjectResolver.HEADER_EMPLOYEE_ID, "emp-1");
        headers.set(DevHeaderSubjectResolver.HEADER_RISK_SCORE, "10");

        Subject subject = resolver.resolve(headers);

        assertThat(subject).isEqualTo(new Subject("emp-1", "employee", 10));
    }

    @Test
    void rejectsRequestsWithoutEmployeeIdHeader() {
        HttpHeaders headers = new HttpHeaders();

        assertThatThrownBy(() -> resolver.resolve(headers))
                .isInstanceOf(MissingSubjectHeaderException.class)
                .hasMessageContaining(DevHeaderSubjectResolver.HEADER_EMPLOYEE_ID);
    }

    @Test
    void rejectsMissingRiskScoreHeader() {
        // SEC-003 (requirements.md): un risk_score ausente ya no se asume
        // como "riesgo cero" (el valor MAS permisivo posible). Se trata
        // igual que cualquier otro header obligatorio ausente: fail-closed.
        HttpHeaders headers = new HttpHeaders();
        headers.set(DevHeaderSubjectResolver.HEADER_EMPLOYEE_ID, "emp-1");
        headers.set(DevHeaderSubjectResolver.HEADER_ROLE, "employee");

        assertThatThrownBy(() -> resolver.resolve(headers))
                .isInstanceOf(MissingSubjectHeaderException.class)
                .hasMessageContaining(DevHeaderSubjectResolver.HEADER_RISK_SCORE);
    }

    @Test
    void rejectsNonNumericRiskScore() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(DevHeaderSubjectResolver.HEADER_EMPLOYEE_ID, "emp-1");
        headers.set(DevHeaderSubjectResolver.HEADER_RISK_SCORE, "muy-alto");

        assertThatThrownBy(() -> resolver.resolve(headers))
                .isInstanceOf(MissingSubjectHeaderException.class);
    }
}