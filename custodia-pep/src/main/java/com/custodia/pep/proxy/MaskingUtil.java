package com.custodia.pep.proxy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aplica las {@code obligations.mask_fields} que devuelve el PDP sobre la
 * respuesta del recurso protegido, antes de que salga de custodia-pep.
 *
 * <p>Esto es "control de acceso a nivel de campo" en la práctica: el mismo
 * endpoint (<code>GET /accounts/{id}/balance</code>) devuelve una vista
 * distinta del recurso según el contexto de autorización de quien pregunta,
 * sin que exista un segundo endpoint "versión reducida".</p>
 */
public final class MaskingUtil {

	private MaskingUtil() {
	}

	public static Map<String, Object> applyMasking(Map<String, Object> body, List<String> maskFields) {
		if (maskFields == null || maskFields.isEmpty() || body == null) {
			return body;
		}
		Map<String, Object> masked = new LinkedHashMap<>(body);
		for (String field : maskFields) {
			if (masked.containsKey(field)) {
				masked.put(field, maskValue(field, masked.get(field)));
			}
		}
		return masked;
	}

	private static Object maskValue(String field, Object value) {
		if (value == null) {
			return null;
		}
		String s = value.toString();
		if ("accountNumber".equals(field) && s.length() > 4) {
			return "****-****-****-" + s.substring(s.length() - 4);
		}
		return "***";
	}
}
