package com.tienda.plataforma.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Firma de webhooks estilo Stripe: {@code firma = "t=..,v1=.."}, firmado {@code t + "." + cuerpo} con HMAC-SHA256 del {@code whsec}. */
public final class FirmaStripe {

	private FirmaStripe() {
	}

	public static boolean verificar(String whsec, String cuerpo, String firma) {
		if (whsec == null || whsec.isEmpty() || cuerpo == null || firma == null) {
			return false;
		}
		String t = null;
		String v1 = null;
		for (String parte : firma.split(",")) {
			if (parte.startsWith("t=")) {
				t = parte.substring(2);
			} else if (parte.startsWith("v1=")) {
				v1 = parte.substring(3);
			}
		}
		if (t == null || v1 == null) {
			return false;
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(whsec.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			String calculado = HexFormat.of().formatHex(
					mac.doFinal((t + "." + cuerpo).getBytes(StandardCharsets.UTF_8)));
			return MessageDigest.isEqual(calculado.getBytes(StandardCharsets.UTF_8),
					v1.getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			return false;
		}
	}
}
