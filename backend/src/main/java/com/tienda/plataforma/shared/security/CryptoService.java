package com.tienda.plataforma.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** AES-GCM para secretos en BD (TOTP, credenciales de pasarela). Llave solo en entorno. */
@Service
public class CryptoService {

	private static final int GCM_TAG_BITS = 128;
	private static final int IV_BYTES = 12;

	private final SecretKeySpec llave;
	private final SecureRandom aleatorio = new SecureRandom();

	public CryptoService(@Value("${crypto.master-key}") String base64) {
		byte[] raw = Base64.getDecoder().decode(base64);
		if (raw.length != 32) {
			throw new IllegalStateException("crypto.master-key debe ser Base64 de 32 bytes");
		}
		this.llave = new SecretKeySpec(raw, "AES");
	}

	public String cifrar(String plano) {
		try {
			byte[] iv = new byte[IV_BYTES];
			aleatorio.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, llave, new GCMParameterSpec(GCM_TAG_BITS, iv));
			byte[] cifrado = cipher.doFinal(plano.getBytes(StandardCharsets.UTF_8));
			byte[] junto = new byte[IV_BYTES + cifrado.length];
			System.arraycopy(iv, 0, junto, 0, IV_BYTES);
			System.arraycopy(cifrado, 0, junto, IV_BYTES, cifrado.length);
			return Base64.getEncoder().encodeToString(junto);
		} catch (Exception e) {
			throw new IllegalStateException("cifrado", e);
		}
	}

	public String descifrar(String texto) {
		try {
			byte[] junto = Base64.getDecoder().decode(texto);
			byte[] iv = new byte[IV_BYTES];
			System.arraycopy(junto, 0, iv, 0, IV_BYTES);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, llave, new GCMParameterSpec(GCM_TAG_BITS, iv));
			byte[] plano = cipher.doFinal(junto, IV_BYTES, junto.length - IV_BYTES);
			return new String(plano, StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new IllegalStateException("descifrado", e);
		}
	}
}
