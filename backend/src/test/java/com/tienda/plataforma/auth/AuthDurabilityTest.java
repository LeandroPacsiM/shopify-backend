package com.tienda.plataforma.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.tienda.plataforma.auth.dto.ConfirmarTotpRequest;
import com.tienda.plataforma.auth.dto.CredencialesRequest;
import com.tienda.plataforma.auth.dto.LoginRequest;
import com.tienda.plataforma.auth.dto.TotpLoginRequest;
import com.tienda.plataforma.auth.entity.Sesion;
import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.SesionRepository;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.auth.service.AuthService;
import com.tienda.plataforma.auth.service.TotpService;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.security.CryptoService;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;

/** RF-AUTH-04/05: errores de auth persisten lockout y revocación antes de responder 401. */
@SpringBootTest
@ActiveProfiles("test")
class AuthDurabilityTest {

	@Autowired
	AuthService authService;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	SesionRepository sesiones;
	@Autowired
	PasswordEncoder passwordEncoder;
	@Autowired
	CryptoService crypto;
	@Autowired
	TotpService totp;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	TiendaUsuarioRepository tiendaUsuarios;
	@Autowired
	ClienteRepository clientes;
	@Autowired
	PlatformTransactionManager txManager;

	@Test
	void fallosDePasswordPersistenElContadorYBloqueanTrasElUmbral() {
		Usuario usuario = nuevoUsuario("lock-");

		for (int intento = 1; intento < AuthService.UMBRAL_BLOQUEO; intento++) {
			assertThrows(NoAutorizadoException.class,
					() -> authService.login(new LoginRequest(usuario.getEmail(), "password-incorrecto")));
			assertEquals(intento, recargar(usuario).getIntentosFallidos());
		}

		assertThrows(NoAutorizadoException.class,
				() -> authService.login(new LoginRequest(usuario.getEmail(), "password-incorrecto")));
		Usuario bloqueado = recargar(usuario);
		assertEquals(0, bloqueado.getIntentosFallidos());
		assertNotNull(bloqueado.getBloqueadoHasta());
		assertTrue(bloqueado.getBloqueadoHasta().isAfter(Instant.now()));

		assertThrows(NoAutorizadoException.class,
				() -> authService.login(new LoginRequest(usuario.getEmail(), "PasswordCorrecta123")));
		assertEquals(bloqueado.getBloqueadoHasta(), recargar(usuario).getBloqueadoHasta());
	}

	@Test
	void falloDeTotpPersisteElContador() {
		Usuario usuario = nuevoUsuario("totp-");
		String secreto = totp.generarSecreto();
		usuario.setTotpSecret(crypto.cifrar(secreto));
		usuario.setTotpHabilitado(true);
		usuario.setTotpConfirmadoEn(Instant.now());
		usuarios.save(usuario);
		String codigoIncorrecto = codigoQueNoValida(secreto);

		assertThrows(NoAutorizadoException.class, () -> authService.verificarTotpLogin(
				new TotpLoginRequest(usuario.getEmail(), "PasswordCorrecta123", codigoIncorrecto, null), null, null));

		assertEquals(1, recargar(usuario).getIntentosFallidos());
	}

	@Test
	void falloDePasswordAlIniciarEnrolamientoPersisteElContador() {
		Usuario usuario = nuevoUsuario("enroll-password-");

		assertThrows(NoAutorizadoException.class, () -> authService.iniciarEnrolamiento(
				new CredencialesRequest(usuario.getEmail(), "password-incorrecto")));

		assertEquals(1, recargar(usuario).getIntentosFallidos());
	}

	@Test
	void falloAlConfirmarEnrolamientoPersisteElContador() {
		Usuario usuario = nuevoUsuario("enroll-");
		String secreto = totp.generarSecreto();
		usuario.setTotpSecret(crypto.cifrar(secreto));
		usuarios.save(usuario);

		assertThrows(NoAutorizadoException.class, () -> authService.confirmarEnrolamiento(
				new ConfirmarTotpRequest(usuario.getEmail(), "PasswordCorrecta123", codigoQueNoValida(secreto))));

		assertEquals(1, recargar(usuario).getIntentosFallidos());
	}

	@Test
	void reutilizarRefreshRevocaLaFamiliaAunqueLaSolicitudFalle() throws Exception {
		Usuario usuario = nuevoUsuario("reuse-");
		UUID familia = UUID.randomUUID();
		String refreshAnterior = "refresh-anterior-" + UUID.randomUUID();
		String refreshVigente = "refresh-vigente-" + UUID.randomUUID();

		Sesion anterior = nuevaSesion(usuario, familia, refreshAnterior);
		Sesion vigente = nuevaSesion(usuario, familia, refreshVigente);
		sesiones.save(anterior);
		sesiones.save(vigente);
		anterior.setReemplazadoPor(vigente);
		sesiones.saveAndFlush(anterior);

		assertThrows(NoAutorizadoException.class,
				() -> authService.refresh(refreshAnterior, null, null));

		var filasFamilia = sesiones.findByFamiliaId(familia);
		assertEquals(2, filasFamilia.size());
		assertTrue(filasFamilia.stream().allMatch(s -> s.getRevocadoEn() != null));
		assertTrue(filasFamilia.stream().allMatch(s -> "reuso".equals(s.getMotivoRevocacion())));
	}

	@Test
	void resetTotpRevocaTodasLasSesionesDelUsuario() throws Exception {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		Tienda tienda = nuevaTiendaConOwner(tag);
		Usuario admin = nuevoUsuario("admin-reset-" + tag);
		TiendaUsuario vinculo = new TiendaUsuario();
		vinculo.setTienda(tienda);
		vinculo.setUsuario(admin);
		vinculo.setRolTienda(TiendaUsuario.ADMIN);
		tiendaUsuarios.save(vinculo);

		Usuario objetivo = nuevoUsuario("cliente-reset-" + tag);
		Cliente cliente = new Cliente();
		cliente.setUsuario(objetivo);
		cliente.setTienda(tienda);
		cliente.setAceptaMarketing(false);
		cliente.setEmail(cliente.getUsuario().getEmail());
		clientes.save(cliente);
		// F3: el reseteo exige membresía staff; el objetivo es operador.
		TiendaUsuario vinculoObjetivo = new TiendaUsuario();
		vinculoObjetivo.setTienda(tienda);
		vinculoObjetivo.setUsuario(objetivo);
		vinculoObjetivo.setRolTienda(TiendaUsuario.OPERADOR);
		tiendaUsuarios.save(vinculoObjetivo);
		objetivo.setTotpSecret(crypto.cifrar(totp.generarSecreto()));
		objetivo.setTotpHabilitado(true);
		objetivo.setTotpConfirmadoEn(Instant.now());
		usuarios.save(objetivo);

		UUID familia = UUID.randomUUID();
		Sesion anterior = nuevaSesion(objetivo, familia, "reset-old-" + tag);
		Sesion actual = nuevaSesion(objetivo, familia, "reset-current-" + tag);
		sesiones.save(anterior);
		sesiones.save(actual);
		anterior.setReemplazadoPor(actual);
		sesiones.saveAndFlush(anterior);

		authService.resetTotpPorAdmin(admin.getId(), objetivo.getId(), tienda.getId());

		Usuario reseteado = recargar(objetivo);
		assertFalse(reseteado.getTotpHabilitado());
		assertNull(reseteado.getTotpSecret());
		var filasUsuario = sesiones.findByUsuarioId(objetivo.getId());
		assertEquals(2, filasUsuario.size());
		assertTrue(filasUsuario.stream().allMatch(s -> s.getRevocadoEn() != null));
		assertTrue(filasUsuario.stream().allMatch(s -> "reset_totp".equals(s.getMotivoRevocacion())));
		assertThrows(NoAutorizadoException.class,
				() -> authService.refresh("reset-current-" + tag, null, null));
	}

	@Test
	void refreshNoRotaUnaSesionSiTotpFueDeshabilitadoPorOtroCamino() throws Exception {
		Usuario usuario = nuevoUsuario("refresh-totp-off-");
		UUID familia = UUID.randomUUID();
		String refresh = "refresh-totp-off-" + UUID.randomUUID();
		sesiones.save(nuevaSesion(usuario, familia, refresh));

		assertThrows(NoAutorizadoException.class, () -> authService.refresh(refresh, null, null));

		Sesion invalidada = sesiones.findByTokenHash(hash(refresh)).orElseThrow();
		assertNotNull(invalidada.getRevocadoEn());
		assertEquals("totp_deshabilitado", invalidada.getMotivoRevocacion());
	}

	private Tienda nuevaTienda(String tag) {
		Tienda tienda = new Tienda();
		tienda.setNombre("AUTH-" + tag);
		tienda.setSlug("auth-" + tag);
		tienda.setActiva(true);
		return tienda;
	}

	/** V22: tienda + owner en una sola TX (el trigger dispara al commit). */
	private Tienda nuevaTiendaConOwner(String tag) {
		Tienda tienda = nuevaTienda(tag);
		Usuario dueno = nuevoUsuario("owner-" + tag);
		TiendaUsuario v = new TiendaUsuario();
		v.setTienda(tienda);
		v.setUsuario(dueno);
		v.setRolTienda(TiendaUsuario.STORE_OWNER);
		new TransactionTemplate(txManager).executeWithoutResult(s -> {
			tiendas.save(tienda);
			tiendaUsuarios.save(v);
		});
		return tienda;
	}

	private Usuario nuevoUsuario(String prefijo) {
		Usuario usuario = new Usuario();
		usuario.setEmail(prefijo + UUID.randomUUID() + "@example.com");
		usuario.setPasswordHash(passwordEncoder.encode("PasswordCorrecta123"));
		usuario.setActivo(true);
		usuario.setEmailVerificado(true);
		usuario.setTotpHabilitado(false);
		usuario.setIntentosFallidos(0);
		return usuarios.save(usuario);
	}

	private Usuario recargar(Usuario usuario) {
		return usuarios.findById(usuario.getId()).orElseThrow();
	}

	private String codigoQueNoValida(String secreto) {
		int intento = 0;
		String codigo = "000000";
		while (totp.verificar(secreto, codigo)) {
			codigo = String.format("%06d", ++intento);
		}
		return codigo;
	}

	private Sesion nuevaSesion(Usuario usuario, UUID familia, String refreshPlano) throws Exception {
		Sesion sesion = new Sesion();
		sesion.setUsuario(usuario);
		sesion.setTokenHash(hash(refreshPlano));
		sesion.setFamiliaId(familia);
		sesion.setExpiraEn(Instant.now().plusSeconds(30L * 24 * 60 * 60));
		return sesion;
	}

	private String hash(String plano) throws Exception {
		byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(plano.getBytes(StandardCharsets.UTF_8));
		return HexFormat.of().formatHex(digest);
	}
}
