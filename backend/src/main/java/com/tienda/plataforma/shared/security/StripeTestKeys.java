package com.tienda.plataforma.shared.security;

/** Validación fail-closed de las credenciales de Stripe permitidas en modo test. */
public final class StripeTestKeys {

	private static final String SECRET_TEST = "sk_test_";
	private static final String RESTRICTED_SECRET_TEST = "rk_test_";
	private static final String PUBLIC_TEST = "pk_test_";
	private static final String WEBHOOK_SECRET = "whsec_";

	private StripeTestKeys() {
	}

	public static boolean esClaveApiSecretaTest(String clave) {
		return tieneSufijo(clave, SECRET_TEST) || tieneSufijo(clave, RESTRICTED_SECRET_TEST);
	}

	public static boolean esClavePublicaTest(String clave) {
		return tieneSufijo(clave, PUBLIC_TEST);
	}

	public static boolean esSecretoWebhook(String secreto) {
		return tieneSufijo(secreto, WEBHOOK_SECRET);
	}

	private static boolean tieneSufijo(String valor, String prefijo) {
		if (valor == null) {
			return false;
		}
		String limpio = valor.trim();
		return limpio.startsWith(prefijo) && limpio.length() > prefijo.length();
	}
}
