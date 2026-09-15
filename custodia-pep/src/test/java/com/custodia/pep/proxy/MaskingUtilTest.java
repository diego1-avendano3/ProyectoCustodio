package com.custodia.pep.proxy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingUtilTest {

	@Test
	void leavesBodyUntouchedWhenNoObligations() {
		Map<String, Object> body = Map.of("accountNumber", "4111-2233-4455-6677", "balance", 100);

		Map<String, Object> result = MaskingUtil.applyMasking(body, List.of());

		assertThat(result).isEqualTo(body);
	}

	@Test
	void partiallyMasksAccountNumberKeepingLastFourDigits() {
		Map<String, Object> body = Map.of("accountNumber", "4111-2233-4455-6677", "balance", 100);

		Map<String, Object> result = MaskingUtil.applyMasking(body, List.of("accountNumber"));

		assertThat(result.get("accountNumber")).isEqualTo("****-****-****-6677");
		assertThat(result.get("balance")).isEqualTo(100);
	}

	@Test
	void fullyMasksFieldsWithoutASpecialRule() {
		Map<String, Object> body = Map.of("ownerId", "emp-42", "balance", 100);

		Map<String, Object> result = MaskingUtil.applyMasking(body, List.of("ownerId"));

		assertThat(result.get("ownerId")).isEqualTo("***");
	}

	@Test
	void ignoresFieldsThatDoNotExistInTheBody() {
		Map<String, Object> body = Map.of("balance", 100);

		Map<String, Object> result = MaskingUtil.applyMasking(body, List.of("nonExistentField"));

		assertThat(result).isEqualTo(body);
	}
}
