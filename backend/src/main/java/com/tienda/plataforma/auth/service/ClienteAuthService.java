package com.tienda.plataforma.auth.service;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.entity.ClienteSesion;
import com.tienda.plataforma.auth.entity.CodigoCliente;
import com.tienda.plataforma.auth.repository.ClienteSesionRepository;
import com.tienda.plataforma.auth.repository.CodigoClienteRepository;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.shared.exception.SolicitudInvalidaException;
import com.tienda.plataforma.shared.exception.TasaExcedidaException;
import com.tienda.plataforma.shared.mail.MailService;
import com.tienda.plataforma.shared.security.HmacService;
import com.tienda.plataforma.shared.security.JwtService;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;

import jakarta.persistence.EntityManager;

/**
 * RF-AUTH-10 (objetivo B, ADR-016): passwordless CUSTOMER por código de
 * email. El perfil nace al verificar (D3), nunca al solicitar. Inerte con
 * `auth.customer.enabled=false` (404). Nunca loguea códigos.
 */
@Service
public class ClienteAuthService {

	public static final long REFRESH_DIAS = 7;
	public static final Duration LIMPIEZA_GRACIA = Duration.ofDays(7);

	private static final Logger LOG = LoggerFactory.getLogger(ClienteAuthService.class);

	private final boolean habilitado;
	private final int ttlMinutos;
	private final int intentosMax;
	private final int maxHoraCuenta;
	private final int maxHoraIp;
	private final long reenvioSegundos;
	private final ClienteRepository clientes;
	private final TiendaRepository tiendas;
	private final CodigoClienteRepository codigos;
	private final ClienteSesionRepository sesiones;
	private final MailService correo;
	private final JwtService jwtService;
	private final HmacService hmac;
	private final EntityManager entityManager;
	private final SecureRandom aleatorio = new SecureRandom();

	public ClienteAuthService(@Value("${auth.customer.enabled:false}") boolean habilitado,
			@Value("${cliente.codigo.ttl-minutos:10}") int ttlMinutos,
			@Value("${cliente.codigo.intentos-max:5}") int intentosMax,
			@Value("${cliente.codigo.max-hora-cuenta:5}") int maxHoraCuenta,
			@Value("${cliente.codigo.max-hora-ip:30}") int maxHoraIp,
			@Value("${cliente.codigo.reenvio-segundos:60}") long reenvioSegundos,
			ClienteRepository clientes, TiendaRepository tiendas,
			CodigoClienteRepository codigos, ClienteSesionRepository sesiones,
			MailService correo,
			JwtService jwtService, HmacService hmac, EntityManager entityManager) {
		this.habilitado = habilitado;
		this.ttlMinutos = ttlMinutos;
		this.intentosMax = intentosMax;
		this.maxHoraCuenta = maxHoraCuenta;
		this.maxHoraIp = maxHoraIp;
		this.reenvioSegundos = reenvioSegundos;
		this.clientes = clientes;
		this.tiendas = tiendas;
		this.codigos = codigos;
		this.sesiones = sesiones;
		this.correo = correo;
		this.jwtService = jwtService;
		this.hmac = hmac;
		this.entityManager = entityManager;
	}

	public record SesionClienteEmitida(String access, String refreshPlano, Long clienteId, Long tiendaId) {
	}

	private void exigirHabilitado() {
		if (!habilitado) {
			throw new NoEncontradoException("recurso");
		}
	}

	static String normalizar(String email) {
		return email.trim().toLowerCase();
	}

	/**
	 * Emite un código (invalida pendientes). Límites atómicos en la misma TX:
	 * fila de IP bajo lock, conteo por cuenta y ventana, intervalo de reenvío.
	 */
	@Transactional
	public void solicitar(String tiendaSlug, String email, String ip) {
		exigirHabilitado();
		Tienda tienda = tiendas.findBySlug(tiendaSlug.trim().toLowerCase())
				.orElseThrow(() -> new NoEncontradoException("tienda"));
		String mail = normalizar(email);
		Instant ahora = Instant.now();

		// Serializa emisores concurrentes para la misma cuenta.
		entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:k))")
				.setParameter("k", "cliente_codigo:" + tienda.getId() + ":" + mail)
				.getSingleResult();

		if (ip != null && !ip.isBlank()) {
			Instant ventana = ahora.truncatedTo(ChronoUnit.HOURS);
			InetAddress direccion;
			try {
				direccion = InetAddress.getByName(ip.trim());
			} catch (Exception e) {
				throw new SolicitudInvalidaException("ip inválida");
			}
			// Upsert atómico en una sentencia (Hibernate no compara
			// inet=varchar en consultas derivadas). Devuelve el contador.
			int contador = ((Number) entityManager.createNativeQuery(
					"INSERT INTO cliente_limite_ip (ip, ventana, contador)"
							+ " VALUES (CAST(:ip AS inet), :ventana, 1)"
							+ " ON CONFLICT (ip, ventana) DO UPDATE SET contador ="
							+ " cliente_limite_ip.contador + 1 RETURNING contador")
					.setParameter("ip", direccion.getHostAddress())
					.setParameter("ventana", ventana)
					.getSingleResult()).intValue();
			if (contador > maxHoraIp) {
				throw new TasaExcedidaException("límite de solicitudes excedido");
			}
		}

		List<CodigoCliente> pendientes = codigos
				.findByTiendaIdAndEmailAndUsadoEnIsNullOrderByCreadoEnDesc(tienda.getId(), mail);
		if (codigos.countByTiendaIdAndEmailAndCreadoEnAfter(tienda.getId(), mail,
				ahora.minusSeconds(3600)) >= maxHoraCuenta) {
			throw new TasaExcedidaException("límite de solicitudes excedido");
		}
		if (!pendientes.isEmpty() && pendientes.get(0).getCreadoEn()
				.isAfter(ahora.minusSeconds(reenvioSegundos))) {
			throw new TasaExcedidaException("espera antes de reenviar");
		}
		for (CodigoCliente previo : pendientes) {
			previo.consumir(ahora);
			codigos.save(previo);
		}

		String plano = generarCodigoPlano();
		CodigoCliente codigo = new CodigoCliente();
		codigo.setTienda(tienda);
		codigo.setEmail(mail);
		codigo.setCliente(clientes.findByTiendaIdAndEmail(tienda.getId(), mail).orElse(null));
		codigo.setCodigoHmac(hmac.hmac(plano));
		codigo.setExpiraEn(ahora.plusSeconds(ttlMinutos * 60L));
		codigo.setIp(ip);
		codigos.save(codigo);
		try {
			correo.enviar(mail, "Tu código de acceso",
					"<p>Usa este código para entrar (vale " + ttlMinutos + " min):</p><p><code>"
							+ plano + "</code></p>");
		} catch (Exception e) {
			LOG.warn("código customer no enviado a tienda {}", tienda.getId());
		}
	}

	/**
	 * Verifica bajo bloqueo (intentos + consumo + creación de perfil en una
	 * TX). Tras agotar intentos el código muere; pedir otro no bloquea
	 * la cuenta (D3).
	 */
	@Transactional
	public SesionClienteEmitida verificar(String tiendaSlug, String email, String codigoPlano,
			String ip, String agente) {
		exigirHabilitado();
		Tienda tienda = tiendas.findBySlug(tiendaSlug.trim().toLowerCase())
				.orElseThrow(() -> new NoEncontradoException("tienda"));
		String mail = normalizar(email);
		Instant ahora = Instant.now();

		CodigoCliente codigo = codigos
				.findTopByTiendaIdAndEmailAndUsadoEnIsNullOrderByCreadoEnDesc(
						tienda.getId(), mail)
				.orElseThrow(() -> new NoAutorizadoException("código inválido"));
		if (!codigo.vigente(ahora) || codigo.getIntentos() >= intentosMax) {
			codigo.consumir(ahora);
			codigos.save(codigo);
			throw new NoAutorizadoException("código inválido");
		}
		if (!hmac.coincide(codigoPlano.trim(), codigo.getCodigoHmac())) {
			codigo.setIntentos(codigo.getIntentos() + 1);
			if (codigo.getIntentos() >= intentosMax) {
				codigo.consumir(ahora);
			}
			codigos.save(codigo);
			throw new NoAutorizadoException("código inválido");
		}
		codigo.consumir(ahora);
		codigos.save(codigo);

		Cliente cliente = clientes.findByTiendaIdAndEmail(tienda.getId(), mail)
				.orElseGet(() -> crearPerfil(tienda, mail));
		codigo.setCliente(cliente);
		codigos.save(codigo);
		return emitirSesion(cliente, UUID.randomUUID(), ip, agente);
	}

	private Cliente crearPerfil(Tienda tienda, String mail) {
		Cliente cliente = new Cliente();
		cliente.setTienda(tienda);
		cliente.setEmail(mail);
		cliente.setAceptaMarketing(false);
		try {
			return clientes.save(cliente);
		} catch (DataIntegrityViolationException e) {
			// Carrera: otro request creó el perfil; reutilizarlo.
			return clientes.findByTiendaIdAndEmail(tienda.getId(), mail)
					.orElseThrow(() -> new ConflictoException("perfil en conflicto"));
		}
	}

	/** Reusar refresh revoca la familia; el 401 no revierte esa revocación. */
	@Transactional(noRollbackFor = NoAutorizadoException.class)
	public SesionClienteEmitida refresh(String refreshPlano, String ip, String agente) {
		exigirHabilitado();
		ClienteSesion sesion = sesiones.findByTokenHash(sha256(refreshPlano.trim()))
				.orElseThrow(() -> new NoAutorizadoException("sesión inválida"));
		if (sesion.getReemplazadoPor() != null) {
			revocarFamilia(sesion.getFamiliaId(), "reuso");
			throw new NoAutorizadoException("sesión inválida");
		}
		if (!sesion.vigente(Instant.now())) {
			throw new NoAutorizadoException("sesión inválida");
		}
		SesionClienteEmitida nueva = emitirSesion(sesion.getCliente(), sesion.getFamiliaId(),
				ip, agente);
		sesion.setRevocadoEn(Instant.now());
		sesion.setMotivoRevocacion("rotacion");
		sesiones.save(sesion);
		ClienteSesion nuevaFila = sesiones.findByTokenHash(sha256(nueva.refreshPlano()))
				.orElseThrow();
		sesion.setReemplazadoPor(nuevaFila);
		sesiones.save(sesion);
		return nueva;
	}

	@Transactional
	public void logout(String refreshPlano) {
		exigirHabilitado();
		if (refreshPlano == null) {
			return;
		}
		sesiones.findByTokenHash(sha256(refreshPlano.trim())).ifPresent(sesion -> {
			sesion.setRevocadoEn(Instant.now());
			sesion.setMotivoRevocacion("logout");
			sesiones.save(sesion);
		});
	}

	private SesionClienteEmitida emitirSesion(Cliente cliente, UUID familia, String ip,
			String agente) {
		String plano = generarRefresh();
		ClienteSesion sesion = new ClienteSesion();
		sesion.setCliente(cliente);
		sesion.setTokenHash(sha256(plano));
		sesion.setFamiliaId(familia);
		sesion.setExpiraEn(Instant.now().plusSeconds(REFRESH_DIAS * 24 * 60 * 60));
		sesion.setIp(ip);
		sesion.setUserAgent(agente);
		sesion.setCreadoEn(Instant.now());
		sesiones.save(sesion);
		return new SesionClienteEmitida(
				jwtService.accesoCliente(cliente.getId(), cliente.getTienda().getId()),
				plano, cliente.getId(), cliente.getTienda().getId());
	}

	private void revocarFamilia(UUID familia, String motivo) {
		for (ClienteSesion s : sesiones.findByFamiliaId(familia)) {
			if (s.getRevocadoEn() == null) {
				s.setRevocadoEn(Instant.now());
				s.setMotivoRevocacion(motivo);
				sesiones.save(s);
			}
		}
	}

	/** Purga códigos vencidos a diario; sesiones con la gracia de reuso (7d). */
	@Scheduled(cron = "0 20 3 * * *")
	@Transactional
	public void limpiarExpirados() {
		codigos.deleteByExpiraEnBefore(Instant.now());
		sesiones.deleteByExpiraEnBefore(Instant.now().minus(LIMPIEZA_GRACIA));
	}

	/** CSPRNG: 6 dígitos. Protegido para fijarlo en tests. */
	protected String generarCodigoPlano() {
		return String.format("%06d", aleatorio.nextInt(1_000_000));
	}

	private String generarRefresh() {
		byte[] bytes = new byte[32];
		aleatorio.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
			throw new IllegalStateException("no se pudo calcular SHA-256", e);
		}
	}
}
