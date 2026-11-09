package com.tienda.plataforma.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.dto.ChallengeResponse;
import com.tienda.plataforma.auth.dto.ConfirmarTotpRequest;
import com.tienda.plataforma.auth.dto.CredencialesRequest;
import com.tienda.plataforma.auth.dto.LoginRequest;
import com.tienda.plataforma.auth.dto.RegistroRequest;
import com.tienda.plataforma.auth.dto.RegistroResponse;
import com.tienda.plataforma.auth.dto.TokensResponse;
import com.tienda.plataforma.auth.dto.TotpConfirmadoResponse;
import com.tienda.plataforma.auth.dto.TotpLoginRequest;
import com.tienda.plataforma.auth.entity.CodigoRecuperacion;
import com.tienda.plataforma.auth.entity.Rol;
import com.tienda.plataforma.auth.entity.Sesion;
import com.tienda.plataforma.auth.entity.TokenCuenta;
import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.entity.UsuarioRol;
import com.tienda.plataforma.auth.repository.CodigoRecuperacionRepository;
import com.tienda.plataforma.auth.repository.SesionRepository;
import com.tienda.plataforma.auth.repository.TokenCuentaRepository;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.auth.repository.UsuarioRolRepository;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.exception.ProhibidoException;
import com.tienda.plataforma.shared.auditoria.AuditoriaService;
import com.tienda.plataforma.shared.mail.MailService;
import com.tienda.plataforma.shared.security.CryptoService;
import com.tienda.plataforma.shared.security.JwtService;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;

import jakarta.persistence.EntityManager;

/** RF-AUTH-01/02/03: registro, TOTP, login, refresh rotado (Flujo 1 de 05, diagrama 04). */
@Service
public class AuthService {

	public static final int UMBRAL_BLOQUEO = 5;
	public static final long BLOQUEO_MINUTOS = 15;
	public static final long REFRESH_DIAS = 30;
	public static final Duration VERIFICACION_TTL = Duration.ofHours(24);
	public static final Duration RESET_TTL = Duration.ofMinutes(60);
	/** Gracia tras expirar antes de purgar (conserva detección de reuso). */
	public static final Duration LIMPIEZA_GRACIA = Duration.ofDays(7);

	private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);

	private static final String ALFABETO_CODIGOS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	private final UsuarioRepository usuarios;
	private final UsuarioRolRepository usuarioRoles;
	private final ClienteRepository clientes;
	private final TiendaRepository tiendas;
	private final TiendaUsuarioRepository tiendaUsuarios;
	private final CodigoRecuperacionRepository codigos;
	private final SesionRepository sesiones;
	private final TokenCuentaRepository tokens;
	private final MailService correo;
	private final PasswordEncoder passwordEncoder;
	private final TotpService totpService;
	private final CryptoService cryptoService;
	private final JwtService jwtService;
	private final EntityManager entityManager;
	private final AuditoriaService auditoria;
	private final SecureRandom aleatorio = new SecureRandom();

	public AuthService(UsuarioRepository usuarios, UsuarioRolRepository usuarioRoles,
			ClienteRepository clientes, TiendaRepository tiendas,
			TiendaUsuarioRepository tiendaUsuarios,
			CodigoRecuperacionRepository codigos, SesionRepository sesiones,
			TokenCuentaRepository tokens, MailService correo,
			PasswordEncoder passwordEncoder, TotpService totpService,
			CryptoService cryptoService, JwtService jwtService, EntityManager entityManager,
			AuditoriaService auditoria) {
		this.usuarios = usuarios;
		this.usuarioRoles = usuarioRoles;
		this.clientes = clientes;
		this.tiendas = tiendas;
		this.tiendaUsuarios = tiendaUsuarios;
		this.codigos = codigos;
		this.sesiones = sesiones;
		this.tokens = tokens;
		this.correo = correo;
		this.passwordEncoder = passwordEncoder;
		this.totpService = totpService;
		this.cryptoService = cryptoService;
		this.jwtService = jwtService;
		this.entityManager = entityManager;
		this.auditoria = auditoria;
	}

	@Transactional
	public RegistroResponse registrar(RegistroRequest req) {
		String email = req.email().trim().toLowerCase();
		if (usuarios.existsByEmail(email)) {
			throw new ConflictoException("email en uso");
		}
		Tienda tienda = tiendas.findBySlug(req.tiendaSlug())
				.orElseThrow(() -> new NoEncontradoException("tienda"));

		Usuario usuario = new Usuario();
		usuario.setEmail(email);
		usuario.setPasswordHash(passwordEncoder.encode(req.password()));
		usuario.setNombre(req.nombre());
		usuario.setApellido(req.apellido());
		usuario.setActivo(true);
		usuario.setEmailVerificado(false);
		usuario.setTotpHabilitado(false);
		usuario.setIntentosFallidos(0);
		usuarios.save(usuario);

		Cliente cliente = new Cliente();
		cliente.setUsuario(usuario);
		cliente.setTienda(tienda);
		cliente.setNombre(req.nombre());
		cliente.setApellido(req.apellido());
		cliente.setAceptaMarketing(false);
		clientes.save(cliente);

		Rol rolCliente = entityManager
				.createQuery("select r from Rol r where r.nombre = :n", Rol.class)
				.setParameter("n", "cliente")
				.getSingleResult();
		UsuarioRol asignacion = new UsuarioRol();
		asignacion.setUsuario(usuario);
		asignacion.setRol(rolCliente);
		usuarioRoles.save(asignacion);

		emitirToken(usuario, TokenCuenta.VERIFICACION, VERIFICACION_TTL,
				"Verifica tu email",
				"Usa este código para verificar tu email (vale 24h):");

		return new RegistroResponse(usuario.getId(), email, true);
	}

	/** El 401 es esperado; no revertir el contador de intentos fallidos. */
	@Transactional(noRollbackFor = NoAutorizadoException.class)
	public String iniciarEnrolamiento(CredencialesRequest req) {
		Usuario usuario = accesoPorPassword(req.email(), req.password());
		if (Boolean.TRUE.equals(usuario.getTotpHabilitado())) {
			throw new ConflictoException("2FA ya habilitado");
		}
		if (usuario.getTotpSecret() == null) {
			usuario.setTotpSecret(cryptoService.cifrar(totpService.generarSecreto()));
			usuarios.save(usuario);
		}
		String secreto = cryptoService.descifrar(usuario.getTotpSecret());
		return totpService.urlQr("plataforma-tiendas", usuario.getEmail(), secreto);
	}

	/** El 401 por password/TOTP inválido debe conservar el contador de lockout. */
	@Transactional(noRollbackFor = NoAutorizadoException.class)
	public TotpConfirmadoResponse confirmarEnrolamiento(ConfirmarTotpRequest req) {
		Usuario usuario = accesoPorPassword(req.email(), req.password());
		if (Boolean.TRUE.equals(usuario.getTotpHabilitado())) {
			throw new ConflictoException("2FA ya habilitado");
		}
		if (usuario.getTotpSecret() == null) {
			throw new ConflictoException("inicia el enrolamiento primero");
		}
		String secreto = cryptoService.descifrar(usuario.getTotpSecret());
		if (!totpService.verificar(secreto, req.codigo())) {
			registrarFallo(usuario);
			throw new NoAutorizadoException("código inválido");
		}
		usuario.setTotpHabilitado(true);
		usuario.setTotpConfirmadoEn(Instant.now());
		usuario.setIntentosFallidos(0);
		usuarios.save(usuario);

		List<String> planos = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			String codigo = generarBackupCode();
			planos.add(codigo);
			CodigoRecuperacion fila = new CodigoRecuperacion();
			fila.setUsuario(usuario);
			fila.setCodigoHash(passwordEncoder.encode(codigo));
			fila.setCreadoEn(Instant.now());
			codigos.save(fila);
		}
		return new TotpConfirmadoResponse(true, planos);
	}

	/** Paso 1: password. Persiste el contador aunque el intento responda 401. */
	@Transactional(noRollbackFor = NoAutorizadoException.class)
	public ChallengeResponse login(LoginRequest req) {
		Usuario usuario = buscarVigente(req.email());
		if (!passwordEncoder.matches(req.password(), usuario.getPasswordHash())) {
			registrarFallo(usuario);
			throw new NoAutorizadoException("credenciales inválidas");
		}
		exigirVerificado(usuario);
		if (!Boolean.TRUE.equals(usuario.getTotpHabilitado())) {
			throw new ConflictoException("debe enrolar 2FA primero");
		}
		return new ChallengeResponse(true);
	}

	/** Paso 2: TOTP o backup code; persiste fallos y emite JWT + refresh si valida. */
	@Transactional(noRollbackFor = NoAutorizadoException.class)
	public SesionEmitida verificarTotpLogin(TotpLoginRequest req, String ip, String agente) {
		Usuario usuario = buscarVigente(req.email());
		if (!passwordEncoder.matches(req.password(), usuario.getPasswordHash())) {
			registrarFallo(usuario);
			throw new NoAutorizadoException("credenciales inválidas");
		}
		exigirVerificado(usuario);
		if (!Boolean.TRUE.equals(usuario.getTotpHabilitado())) {
			throw new ConflictoException("debe enrolar 2FA primero");
		}
		boolean ok;
		if (req.backupCode() != null && !req.backupCode().isBlank()) {
			ok = consumirBackupCode(usuario, req.backupCode().trim());
		} else if (req.codigo() != null) {
			String secreto = cryptoService.descifrar(usuario.getTotpSecret());
			ok = totpService.verificar(secreto, req.codigo());
		} else {
			throw new ConflictoException("codigo o backupCode requerido");
		}
		if (!ok) {
			registrarFallo(usuario);
			throw new NoAutorizadoException("código inválido");
		}
		usuario.setIntentosFallidos(0);
		usuario.setUltimoAcceso(Instant.now());
		usuarios.save(usuario);
		return emitirSesion(usuario, UUID.randomUUID(), ip, agente);
	}

	/** Reusar refresh revoca la familia; el 401 no debe revertir esa revocación. */
	@Transactional(noRollbackFor = NoAutorizadoException.class)
	public SesionEmitida refresh(String refreshPlano, String ip, String agente) {
		Sesion sesion = sesiones.findByTokenHash(sha256(refreshPlano))
				.orElseThrow(() -> new NoAutorizadoException("sesión inválida"));
		if (sesion.getReemplazadoPor() != null) {
			revocarFamilia(sesion.getFamiliaId(), "reuso");
			throw new NoAutorizadoException("sesión inválida");
		}
		if (!sesion.vigente(Instant.now())) {
			throw new NoAutorizadoException("sesión inválida");
		}
		if (!Boolean.TRUE.equals(sesion.getUsuario().getTotpHabilitado())) {
			revocarFamilia(sesion.getFamiliaId(), "totp_deshabilitado");
			throw new NoAutorizadoException("sesión inválida");
		}
		SesionEmitida nueva = emitirSesion(sesion.getUsuario(), sesion.getFamiliaId(), ip, agente);
		sesion.setRevocadoEn(Instant.now());
		sesion.setMotivoRevocacion("rotacion");
		sesiones.save(sesion);
		// Enlaza la cadena (requiere la nueva fila persistida).
		Sesion nuevaFila = sesiones.findByTokenHash(sha256(nueva.refreshPlano())).orElseThrow();
		sesion.setReemplazadoPor(nuevaFila);
		sesiones.save(sesion);
		return nueva;
	}

	@Transactional
	public void logout(String refreshPlano) {
		sesiones.findByTokenHash(sha256(refreshPlano)).ifPresent(sesion -> {
			sesion.setRevocadoEn(Instant.now());
			sesion.setMotivoRevocacion("logout");
			sesiones.save(sesion);
		});
	}

	/**
	 * RF-AUTH-05: purga sesiones vencidas hace más de la gracia. La gracia
	 * conserva la detección de reuso (familia revocada) una semana pasada
	 * la expiración; lo vigente o recién vencido no se toca. Corre a diario.
	 */
	@Scheduled(cron = "0 0 3 * * *")
	@Transactional
	public void limpiarSesionesExpiradas() {
		sesiones.deleteByExpiraEnBefore(Instant.now().minus(LIMPIEZA_GRACIA));
	}

	/**
	 * RF-AUTH-07: solicitar verificación. Respuesta siempre genérica
	 * (no revela si el email existe). Solo emite si hay usuario activo
	 * sin verificar.
	 */
	@Transactional
	public void solicitarVerificacion(String email) {
		usuarios.findByEmail(email.trim().toLowerCase()).ifPresent(usuario -> {
			if (Boolean.TRUE.equals(usuario.getActivo())
					&& !Boolean.TRUE.equals(usuario.getEmailVerificado())) {
				emitirToken(usuario, TokenCuenta.VERIFICACION, VERIFICACION_TTL,
						"Verifica tu email",
						"Usa este código para verificar tu email (vale 24h):");
			}
		});
	}

	/** RF-AUTH-07: confirma verificación con el token del correo (un solo uso). */
	@Transactional
	public void confirmarVerificacion(String tokenPlano) {
		TokenCuenta token = tokens.findByTokenHash(sha256(tokenPlano.trim()))
				.orElseThrow(() -> new NoAutorizadoException("token inválido o expirado"));
		if (!TokenCuenta.VERIFICACION.equals(token.getTipo()) || !token.vigente(Instant.now())) {
			throw new NoAutorizadoException("token inválido o expirado");
		}
		Usuario usuario = token.getUsuario();
		usuario.setEmailVerificado(true);
		usuarios.save(usuario);
		token.consumir(Instant.now());
		tokens.save(token);
	}

	/**
	 * RF-AUTH-07: solicitar reset. Siempre genérico (no revela si el
	 * email existe ni si está bloqueado).
	 */
	@Transactional
	public void solicitarReset(String email) {
		usuarios.findByEmail(email.trim().toLowerCase()).ifPresent(usuario -> {
			if (Boolean.TRUE.equals(usuario.getActivo())) {
				emitirToken(usuario, TokenCuenta.RESET, RESET_TTL,
						"Recupera tu contraseña",
						"Usa este código para fijar una contraseña nueva (vale 60min):");
			}
		});
	}

	/**
	 * RF-AUTH-07: fija la contraseña nueva con el token del correo.
	 * Revoca todas las sesiones (el posible atacante queda fuera) y
	 * desbloquea la cuenta.
	 */
	@Transactional
	public void confirmarReset(String tokenPlano, String passwordNuevo) {
		TokenCuenta token = tokens.findByTokenHash(sha256(tokenPlano.trim()))
				.orElseThrow(() -> new NoAutorizadoException("token inválido o expirado"));
		if (!TokenCuenta.RESET.equals(token.getTipo()) || !token.vigente(Instant.now())) {
			throw new NoAutorizadoException("token inválido o expirado");
		}
		Usuario usuario = token.getUsuario();
		usuario.setPasswordHash(passwordEncoder.encode(passwordNuevo));
		usuario.setIntentosFallidos(0);
		usuario.setBloqueadoHasta(null);
		usuarios.save(usuario);
		for (Sesion s : sesiones.findByUsuarioId(usuario.getId())) {
			if (s.getRevocadoEn() == null) {
				s.setRevocadoEn(Instant.now());
				s.setMotivoRevocacion("reset_password");
				sesiones.save(s);
			}
		}
		token.consumir(Instant.now());
		tokens.save(token);
	}

	/**
	 * Emite el token del tipo dado (invalida pendientes del mismo tipo)
	 * y lo envía por correo. El fallo de correo no revierte la operación:
	 * el usuario puede re-solicitar. Nunca loguea el token.
	 */
	private void emitirToken(Usuario usuario, String tipo, Duration ttl, String asunto, String intro) {
		Instant ahora = Instant.now();
		for (TokenCuenta previo : tokens.findByUsuarioIdAndTipoAndUsadoEnIsNull(usuario.getId(), tipo)) {
			previo.consumir(ahora);
			tokens.save(previo);
		}
		String plano = generarRefresh();
		TokenCuenta token = new TokenCuenta();
		token.setUsuario(usuario);
		token.setTipo(tipo);
		token.setTokenHash(sha256(plano));
		token.setExpiraEn(ahora.plus(ttl));
		tokens.save(token);
		try {
			correo.enviar(usuario.getEmail(), asunto,
					"<p>" + intro + "</p><p><code>" + plano + "</code></p>");
		} catch (Exception e) {
			LOG.warn("correo {} no enviado a usuario {}", tipo, usuario.getId());
		}
	}

	/**
	 * RF-AUTH-06: solo el admin de la tienda puede resetear el 2FA de un usuario
	 * vinculado a ella. Anula secreto y backup codes.
	 */
	@Transactional
	public void resetTotpPorAdmin(Long actorId, Long objetivoId, Long tiendaId) {
		boolean actorEsAdmin = tiendaId != null && actorId != null
				&& tiendaUsuarios.findByTiendaIdAndUsuarioId(tiendaId, actorId)
						.map(v -> TiendaUsuario.ADMIN.equals(v.getRolTienda())).orElse(false);
		if (!actorEsAdmin) {
			throw new ProhibidoException("solo el admin de la tienda puede resetear 2FA");
		}
		Usuario objetivo = usuarios.findById(objetivoId)
				.orElseThrow(() -> new NoEncontradoException("usuario"));
		boolean vinculado = tiendaUsuarios.findByTiendaIdAndUsuarioId(tiendaId, objetivoId).isPresent()
				|| clientes.findByUsuarioId(objetivoId)
						.map(c -> c.getTienda().getId().equals(tiendaId)).orElse(false);
		if (!vinculado) {
			throw new NoEncontradoException("usuario");
		}
		objetivo.setTotpSecret(null);
		objetivo.setTotpHabilitado(false);
		objetivo.setTotpConfirmadoEn(null);
		objetivo.setIntentosFallidos(0);
		objetivo.setBloqueadoHasta(null);
		usuarios.save(objetivo);
		codigos.findByUsuarioId(objetivoId).forEach(fila -> {
			fila.consumir(Instant.now());
			codigos.save(fila);
		});
		revocarSesionesUsuario(objetivoId, "reset_totp");
		auditoria.registrar("usuarios", objetivoId, "usuario.totp_reset");
	}

	private SesionEmitida emitirSesion(Usuario usuario, UUID familia, String ip, String agente) {
		String plano = generarRefresh();
		Sesion sesion = new Sesion();
		sesion.setUsuario(usuario);
		sesion.setTokenHash(sha256(plano));
		sesion.setFamiliaId(familia);
		sesion.setExpiraEn(Instant.now().plusSeconds(REFRESH_DIAS * 24 * 60 * 60));
		sesion.setIp(ip);
		sesion.setUserAgent(agente);
		sesion.setCreadoEn(Instant.now());
		sesiones.save(sesion);
		List<String> roles = usuarioRoles.nombresDeRoles(usuario.getId());
		return new SesionEmitida(jwtService.acceso(usuario.getId(), roles), plano, roles);
	}

	private void revocarFamilia(UUID familia, String motivo) {
		for (Sesion s : sesiones.findByFamiliaId(familia)) {
			if (s.getRevocadoEn() == null) {
				s.setRevocadoEn(Instant.now());
				s.setMotivoRevocacion(motivo);
				sesiones.save(s);
			}
		}
	}

	private void revocarSesionesUsuario(Long usuarioId, String motivo) {
		Instant ahora = Instant.now();
		for (Sesion s : sesiones.findByUsuarioId(usuarioId)) {
			if (s.getRevocadoEn() == null) {
				s.setRevocadoEn(ahora);
				s.setMotivoRevocacion(motivo);
				sesiones.save(s);
			}
		}
	}

	private boolean consumirBackupCode(Usuario usuario, String codigo) {
		for (CodigoRecuperacion fila : codigos.findByUsuarioId(usuario.getId())) {
			if (fila.vigente() && passwordEncoder.matches(codigo, fila.getCodigoHash())) {
				fila.consumir(Instant.now());
				codigos.save(fila);
				return true;
			}
		}
		return false;
	}

	private Usuario buscarVigente(String email) {
		Usuario usuario = usuarios.findByEmail(email.trim().toLowerCase())
				.orElseThrow(() -> new NoAutorizadoException("credenciales inválidas"));
		if (!Boolean.TRUE.equals(usuario.getActivo())
				|| (usuario.getBloqueadoHasta() != null && usuario.getBloqueadoHasta().isAfter(Instant.now()))) {
			throw new NoAutorizadoException("credenciales inválidas");
		}
		return usuario;
	}

	private Usuario accesoPorPassword(String email, String password) {
		Usuario usuario = buscarVigente(email);
		if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
			registrarFallo(usuario);
			throw new NoAutorizadoException("credenciales inválidas");
		}
		return usuario;
	}

	/** RF-AUTH-07: sin email verificado no hay sesión (el enrolamiento sí puede antes). */
	private void exigirVerificado(Usuario usuario) {
		if (!Boolean.TRUE.equals(usuario.getEmailVerificado())) {
			throw new ConflictoException("debe verificar su email");
		}
	}

	private void registrarFallo(Usuario usuario) {
		int fallos = (usuario.getIntentosFallidos() == null ? 0 : usuario.getIntentosFallidos()) + 1;
		usuario.setIntentosFallidos(fallos);
		if (fallos >= UMBRAL_BLOQUEO) {
			usuario.setBloqueadoHasta(Instant.now().plusSeconds(BLOQUEO_MINUTOS * 60));
			usuario.setIntentosFallidos(0);
		}
		usuarios.save(usuario);
	}

	private String generarRefresh() {
		byte[] bytes = new byte[32];
		aleatorio.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String generarBackupCode() {
		StringBuilder sb = new StringBuilder(8);
		for (int i = 0; i < 8; i++) {
			sb.append(ALFABETO_CODIGOS.charAt(aleatorio.nextInt(ALFABETO_CODIGOS.length())));
		}
		return sb.toString();
	}

	private String sha256(String texto) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(texto.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : hash) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			throw new IllegalStateException("sha256", e);
		}
	}

	/** Access + refresh recién emitidos (el refresh va a cookie HttpOnly). */
	public record SesionEmitida(String accessToken, String refreshPlano, List<String> roles) {
		public TokensResponse tokens() {
			return new TokensResponse(accessToken, roles);
		}
	}
}
