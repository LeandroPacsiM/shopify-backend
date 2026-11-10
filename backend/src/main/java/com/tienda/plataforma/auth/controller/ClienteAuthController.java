package com.tienda.plataforma.auth.controller;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.plataforma.auth.dto.ClienteTokensResponse;
import com.tienda.plataforma.auth.dto.SolicitarCodigoRequest;
import com.tienda.plataforma.auth.dto.VerificarCodigoRequest;
import com.tienda.plataforma.auth.service.ClienteAuthService;
import com.tienda.plataforma.auth.service.ClienteAuthService.SesionClienteEmitida;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * RF-AUTH-10 (objetivo B, ADR-016): passwordless CUSTOMER. Inerte (404) con
 * `auth.customer.enabled=false`. Cookie propia `refresh_customer` para no
 * cruzarse con la de staff.
 */
@RestController
@RequestMapping("/auth/cliente")
@Tag(name = "Auth CUSTOMER", description = "Passwordless por código de email (RF-AUTH-10, ADR-016)")
public class ClienteAuthController {

	private final ClienteAuthService authService;
	private final boolean cookieSecure;

	public ClienteAuthController(ClienteAuthService authService,
			@Value("${cookie.secure:false}") boolean cookieSecure) {
		this.authService = authService;
		this.cookieSecure = cookieSecure;
	}

	@PostMapping("/solicitar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Emite código de 6 dígitos (respuesta genérica, con rate limit)")
	public void solicitar(@Valid @RequestBody SolicitarCodigoRequest req,
			HttpServletRequest request,
			@RequestHeader(value = "X-Forwarded-For", required = false) String xff) {
		authService.solicitar(req.tiendaSlug(), req.email(), ipDe(request, xff));
	}

	@PostMapping("/verificar")
	@Operation(summary = "Verifica el código; crea el perfil si no existe y emite JWT + refresh")
	public ResponseEntity<ClienteTokensResponse> verificar(
			@Valid @RequestBody VerificarCodigoRequest req, HttpServletRequest request,
			@RequestHeader(value = "X-Forwarded-For", required = false) String xff,
			@RequestHeader(value = "User-Agent", required = false) String agente) {
		return conCookie(authService.verificar(req.tiendaSlug(), req.email(), req.codigo(),
				ipDe(request, xff), agente));
	}

	@PostMapping("/refresh")
	@Operation(summary = "Rota el refresh CUSTOMER (reuso revoca la familia)")
	public ResponseEntity<ClienteTokensResponse> refresh(
			@CookieValue(value = "refresh_customer", required = false) String refresh,
			HttpServletRequest request,
			@RequestHeader(value = "X-Forwarded-For", required = false) String xff,
			@RequestHeader(value = "User-Agent", required = false) String agente) {
		if (refresh == null) {
			throw new com.tienda.plataforma.shared.exception.NoAutorizadoException("sin refresh");
		}
		return conCookie(authService.refresh(refresh, ipDe(request, xff), agente));
	}

	@PostMapping("/logout")
	@Operation(summary = "Revoca el refresh CUSTOMER y expira su cookie")
	public ResponseEntity<Void> logout(
			@CookieValue(value = "refresh_customer", required = false) String refresh) {
		if (refresh != null) {
			authService.logout(refresh);
		}
		ResponseCookie expirada = ResponseCookie.from("refresh_customer", "")
				.httpOnly(true)
				.secure(cookieSecure)
				.path("/")
				.maxAge(Duration.ZERO)
				.sameSite("Lax")
				.build();
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, expirada.toString())
				.build();
	}

	private ResponseEntity<ClienteTokensResponse> conCookie(SesionClienteEmitida emitida) {
		ResponseCookie cookie = ResponseCookie.from("refresh_customer", emitida.refreshPlano())
				.httpOnly(true)
				.secure(cookieSecure)
				.path("/")
				.maxAge(Duration.ofDays(ClienteAuthService.REFRESH_DIAS))
				.sameSite("Lax")
				.build();
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookie.toString())
				.body(new ClienteTokensResponse(emitida.access(), emitida.clienteId()));
	}

	/**
	 * IP real: primer X-Forwarded-For si hay proxy confiable, si no la remota.
	 * `server.forward-headers-strategy=native` deja que el contenedor resuelva
	 * la cadena de proxies (condición 4 de ejecución).
	 */
	static String ipDe(HttpServletRequest request, String xff) {
		if (xff != null && !xff.isBlank()) {
			return xff.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}
