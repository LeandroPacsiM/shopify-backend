package com.tienda.plataforma.auth.controller;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.plataforma.auth.dto.ConfirmarTotpRequest;
import com.tienda.plataforma.auth.dto.CrearTiendaRequest;
import com.tienda.plataforma.auth.dto.CredencialesRequest;
import com.tienda.plataforma.auth.dto.EmailRequest;
import com.tienda.plataforma.auth.dto.LoginRequest;
import com.tienda.plataforma.auth.dto.ChallengeResponse;
import com.tienda.plataforma.auth.dto.RegistroRequest;
import com.tienda.plataforma.auth.dto.RegistroAdministrativoRequest;
import com.tienda.plataforma.auth.dto.RegistroResponse;
import com.tienda.plataforma.auth.dto.ResetConfirmRequest;
import com.tienda.plataforma.auth.dto.TokenRequest;
import com.tienda.plataforma.auth.dto.TotpConfirmadoResponse;
import com.tienda.plataforma.auth.dto.TotpLoginRequest;
import com.tienda.plataforma.auth.dto.TokensResponse;
import com.tienda.plataforma.auth.dto.TiendaCreadaResponse;
import com.tienda.plataforma.auth.service.AuthService;
import com.tienda.plataforma.auth.service.AuthService.SesionEmitida;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.tenants.service.TiendaOnboardingService;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Registro, TOTP 2FA, login y sesiones (RF-AUTH, diagrama 04)")
public class AuthController {

	private final AuthService authService;
	private final TiendaOnboardingService onboarding;
	private final boolean cookieSecure;

	public AuthController(AuthService authService, TiendaOnboardingService onboarding,
			@Value("${cookie.secure:false}") boolean cookieSecure) {
		this.authService = authService;
		this.onboarding = onboarding;
		this.cookieSecure = cookieSecure;
	}

	@PostMapping("/registro")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Registra usuario + cliente + rol en la tienda del slug (actual pre-B, se depreca en F3)")
	public RegistroResponse registro(@Valid @RequestBody RegistroRequest req) {
		return authService.registrar(req);
	}

	@PostMapping("/registro-administrativo")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Registra identidad staff sin permisos (RF-AUTH-09, no crea CUSTOMER ni asigna rol)")
	public RegistroResponse registroAdministrativo(
			@Valid @RequestBody RegistroAdministrativoRequest req) {
		return authService.registrarAdministrativo(req);
	}

	@PostMapping("/tiendas")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Crea la tienda del comerciante autenticado (queda admin, suscripción pendiente)")
	public TiendaCreadaResponse crearTienda(@Valid @RequestBody CrearTiendaRequest req) {
		Object principal = SecurityContextHolder.getContext().getAuthentication() == null
				? null
				: SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (!(principal instanceof Long usuarioId)) {
			throw new NoAutorizadoException("sin autenticación");
		}
		return onboarding.crear(usuarioId, req);
	}

	@PostMapping("/totp/iniciar")
	public String iniciarEnrolamiento(@Valid @RequestBody CredencialesRequest req) {
		return authService.iniciarEnrolamiento(req);
	}

	@PostMapping("/totp/confirmar")
	public TotpConfirmadoResponse confirmarEnrolamiento(@Valid @RequestBody ConfirmarTotpRequest req) {
		return authService.confirmarEnrolamiento(req);
	}

	@PostMapping("/login")
	public ChallengeResponse login(@Valid @RequestBody LoginRequest req) {
		return authService.login(req);
	}

	@PostMapping("/login/totp")
	@Operation(summary = "Verifica TOTP o backup code; emite JWT + refresh en cookie HttpOnly")
	public ResponseEntity<TokensResponse> verificarTotp(
			@Valid @RequestBody TotpLoginRequest req,
			@RequestHeader(value = "User-Agent", required = false) String agente,
			@org.springframework.web.bind.annotation.RequestHeader(value = "X-Forwarded-For", required = false) String ip) {
		SesionEmitida emitida = authService.verificarTotpLogin(req, ip, agente);
		return conCookie(emitida);
	}

	@PostMapping("/refresh")
	public ResponseEntity<TokensResponse> refresh(
			@CookieValue(value = "refresh", required = false) String refresh,
			@RequestHeader(value = "X-Forwarded-For", required = false) String ip,
			@RequestHeader(value = "User-Agent", required = false) String agente) {
		if (refresh == null) {
			throw new com.tienda.plataforma.shared.exception.NoAutorizadoException("sin refresh");
		}
		return conCookie(authService.refresh(refresh, ip, agente));
	}

	@PostMapping("/logout")
	@Operation(summary = "Revoca el refresh y expira su cookie HttpOnly")
	public ResponseEntity<Void> logout(@CookieValue(value = "refresh", required = false) String refresh) {
		if (refresh != null) {
			authService.logout(refresh);
		}
		ResponseCookie expirada = ResponseCookie.from("refresh", "")
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

	@PostMapping("/email/solicitar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Reenvía el código de verificación (respuesta genérica)")
	public void solicitarVerificacion(@Valid @RequestBody EmailRequest req) {
		authService.solicitarVerificacion(req.email());
	}

	@PostMapping("/email/confirmar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Verifica el email con el código del correo")
	public void confirmarVerificacion(@Valid @RequestBody TokenRequest req) {
		authService.confirmarVerificacion(req.token());
	}

	@PostMapping("/password/solicitar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Solicita reset de contraseña (respuesta genérica)")
	public void solicitarReset(@Valid @RequestBody EmailRequest req) {
		authService.solicitarReset(req.email());
	}

	@PostMapping("/password/confirmar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Fija la contraseña nueva con el código del correo")
	public void confirmarReset(@Valid @RequestBody ResetConfirmRequest req) {
		authService.confirmarReset(req.token(), req.password());
	}

	private ResponseEntity<TokensResponse> conCookie(SesionEmitida emitida) {
		ResponseCookie cookie = ResponseCookie.from("refresh", emitida.refreshPlano())
				.httpOnly(true)
				.secure(cookieSecure)
				.path("/")
				.maxAge(Duration.ofDays(AuthService.REFRESH_DIAS))
				.sameSite("Lax")
				.build();
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookie.toString())
				.body(emitida.tokens());
	}
}
