package com.tienda.plataforma.auth.service;

import java.security.SecureRandom;

import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;

/**
 * Envoltorio TOTP (RF-AUTH-02/03). El secreto lo genera el servicio
 * (20 bytes Base32); la lib solo verifica y arma el QR.
 */
@Service
public class TotpService {

	private final GoogleAuthenticator google = new GoogleAuthenticator();
	private final SecureRandom aleatorio = new SecureRandom();
	private final Base32 base32 = new Base32();

	public String generarSecreto() {
		byte[] bytes = new byte[20];
		aleatorio.nextBytes(bytes);
		return base32.encodeToString(bytes);
	}

	public String urlQr(String emisor, String cuenta, String secreto) {
		GoogleAuthenticatorKey clave = new GoogleAuthenticatorKey.Builder(secreto).build();
		return GoogleAuthenticatorQRGenerator.getOtpAuthTotpURL(emisor, cuenta, clave);
	}

	public boolean verificar(String secreto, int codigo) {
		return google.authorize(secreto, codigo);
	}
}
