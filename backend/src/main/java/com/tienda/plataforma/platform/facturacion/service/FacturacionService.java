package com.tienda.plataforma.platform.facturacion.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienda.plataforma.platform.facturacion.dto.FacturaWebhookRequest;
import com.tienda.plataforma.platform.facturacion.dto.SuscripcionResponse;
import com.tienda.plataforma.platform.facturacion.entity.Suscripcion;
import com.tienda.plataforma.platform.facturacion.entity.SuscripcionEvento;
import com.tienda.plataforma.platform.facturacion.repository.SuscripcionEventoRepository;
import com.tienda.plataforma.platform.facturacion.repository.SuscripcionRepository;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.shared.security.FirmaStripe;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.TiendaRepository;

/**
 * Facturación de plataforma (RF-BILL): la tienda paga por el software con
 * NUESTRAS llaves; lo que cobra a sus clientes va por SU pasarela (V10).
 * Sin suscripción vigente no hay checkout nuevo (tienda congelada).
 * El cobro Stripe (Subscriptions API) llega en V19; aquí el modelo, los
 * estados y el webhook que activa/renueva o congela.
 */
@Service
public class FacturacionService {

	public static final String PAGADA = "suscripcion.pagada";
	public static final String VENCIDA = "suscripcion.vencida";

	private static final Logger LOG = LoggerFactory.getLogger(FacturacionService.class);

	private final SuscripcionRepository suscripciones;
	private final SuscripcionEventoRepository eventos;
	private final TiendaRepository tiendas;
	private final String whsec;
	private final String llave;
	private final String precioMensual;
	private final String precioAnual;
	private final String baseUrl;
	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private final ObjectMapper json = new ObjectMapper();

	public FacturacionService(SuscripcionRepository suscripciones,
			SuscripcionEventoRepository eventos, TiendaRepository tiendas,
			@Value("${stripe.platform-whsec:}") String whsec,
			@Value("${stripe.platform-key:}") String llave,
			@Value("${stripe.price-mensual:}") String precioMensual,
			@Value("${stripe.price-anual:}") String precioAnual,
			@Value("${stripe.api-base:https://api.stripe.com}") String baseUrl) {
		this.suscripciones = suscripciones;
		this.eventos = eventos;
		this.tiendas = tiendas;
		this.whsec = whsec == null ? "" : whsec.trim();
		this.llave = llave == null ? "" : llave.trim();
		this.precioMensual = precioMensual == null ? "" : precioMensual.trim();
		this.precioAnual = precioAnual == null ? "" : precioAnual.trim();
		String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.baseUrl = base;
	}

	@Transactional(readOnly = true)
	public SuscripcionResponse ver(Long tiendaId) {
		Suscripcion s = suscripciones.findByTiendaId(tiendaId)
				.orElseThrow(() -> new NoEncontradoException("suscripcion"));
		return responder(s);
	}

	/** Crea o cambia el plan (queda pendiente hasta el primer pago). */
	@Transactional
	public SuscripcionResponse suscribir(Long tiendaId, String plan) {
		if (!Suscripcion.MENSUAL.equals(plan) && !Suscripcion.ANUAL.equals(plan)) {
			throw new ConflictoException("plan inválido");
		}
		Tienda tienda = tiendas.findById(tiendaId)
				.orElseThrow(() -> new NoEncontradoException("tienda"));
		Suscripcion s = suscripciones.findByTiendaId(tiendaId).orElseGet(Suscripcion::new);
		if (s.getId() != null && Suscripcion.ACTIVA.equals(s.getEstado())) {
			throw new ConflictoException("suscripción ya activa");
		}
		s.setTienda(tienda);
		s.setPlan(plan);
		s.setEstado(Suscripcion.PENDIENTE);
		suscripciones.save(s);
		if (cobroConfigurado()) {
			crearEnStripe(s);
			suscripciones.save(s);
		} else {
			LOG.warn("facturación sin configurar para tienda {}", tiendaId);
		}
		return responder(s);
	}

	@Transactional
	public SuscripcionResponse cancelar(Long tiendaId) {
		Suscripcion s = suscripciones.findByTiendaId(tiendaId)
				.orElseThrow(() -> new NoEncontradoException("suscripcion"));
		s.setEstado(Suscripcion.CANCELADA);
		suscripciones.save(s);
		return responder(s);
	}

	/**
	 * Webhook de plataforma: firma con nuestro whsec + dedup por event-id.
	 * Pagada → activa + periodo según plan + descongela; vencida → congela.
	 */
	@Transactional
	public void webhook(FacturaWebhookRequest req) {
		if (!FirmaStripe.verificar(whsec, req.cuerpo(), req.firma())) {
			throw new NoAutorizadoException("firma inválida");
		}
		if (eventos.existsByReferenciaExterna(req.eventId())) {
			return;
		}
		Suscripcion s = suscripciones.findByStripeSubscriptionId(req.suscripcionId())
				.orElseThrow(() -> new NoEncontradoException("suscripcion"));
		if (PAGADA.equals(req.tipo())) {
			aplicarPago(s);
		} else if (VENCIDA.equals(req.tipo())) {
			aplicarVencida(s);
		} else {
			throw new ConflictoException("evento desconocido");
		}
		suscripciones.save(s);
		SuscripcionEvento evento = new SuscripcionEvento();
		evento.setSuscripcion(s);
		evento.setReferenciaExterna(req.eventId());
		evento.setTipo(req.tipo());
		eventos.save(evento);
	}

	/**
	 * Evento crudo de Stripe (V19): `invoice.payment_succeeded` renueva,
	 * `customer.subscription.deleted` cancela y congela; resto → 200.
	 */
	@Transactional
	public void webhookStripe(String cuerpo, String firma) {
		JsonNode raiz;
		try {
			raiz = json.readTree(cuerpo);
		} catch (Exception e) {
			throw new ConflictoException("evento inválido");
		}
		if (!FirmaStripe.verificar(whsec, cuerpo, firma)) {
			throw new NoAutorizadoException("firma inválida");
		}
		JsonNode id = raiz.get("id");
		JsonNode tipo = raiz.get("type");
		if (id == null || !id.isTextual() || tipo == null || !tipo.isTextual()) {
			throw new ConflictoException("evento inválido");
		}
		if (eventos.existsByReferenciaExterna(id.asText())) {
			return;
		}
		JsonNode objeto = raiz.path("data").path("object");
		switch (tipo.asText()) {
			case "invoice.payment_succeeded" -> {
				Suscripcion s = suscripciones
						.findByStripeSubscriptionId(objeto.path("subscription").asText())
						.orElseThrow(() -> new NoEncontradoException("suscripcion"));
				aplicarPago(s);
				guardarEvento(s, id.asText(), tipo.asText());
			}
			case "customer.subscription.deleted" -> {
				Suscripcion s = suscripciones
						.findByStripeSubscriptionId(objeto.path("id").asText())
						.orElseThrow(() -> new NoEncontradoException("suscripcion"));
				s.setEstado(Suscripcion.CANCELADA);
				s.getTienda().setActiva(false);
				tiendas.save(s.getTienda());
				suscripciones.save(s);
				guardarEvento(s, id.asText(), tipo.asText());
			}
			default -> {
				return;
			}
		}
	}

	private void aplicarPago(Suscripcion s) {
		Instant ahora = Instant.now();
		s.setEstado(Suscripcion.ACTIVA);
		s.setPeriodoFin(ahora.plusSeconds(diasSegunPlan(s) * 24 * 60 * 60));
		s.getTienda().setActiva(true);
		tiendas.save(s.getTienda());
	}

	private void aplicarVencida(Suscripcion s) {
		s.setEstado(Suscripcion.VENCIDA);
		s.getTienda().setActiva(false);
		tiendas.save(s.getTienda());
	}

	private void guardarEvento(Suscripcion s, String referencia, String tipo) {
		suscripciones.save(s);
		SuscripcionEvento evento = new SuscripcionEvento();
		evento.setSuscripcion(s);
		evento.setReferenciaExterna(referencia);
		evento.setTipo(tipo);
		eventos.save(evento);
	}

	/** Enforcement: sin suscripción vigente no hay checkout nuevo. */
	@Transactional(readOnly = true)
	public void exigirActiva(Long tiendaId) {
		boolean ok = suscripciones.findByTiendaId(tiendaId)
				.map(s -> s.vigente(Instant.now())).orElse(false)
				&& Boolean.TRUE.equals(tiendas.findById(tiendaId)
						.map(Tienda::getActiva).orElse(false));
		if (!ok) {
			throw new ConflictoException("tienda suspendida por facturación");
		}
	}

	private long diasSegunPlan(Suscripcion s) {
		return Suscripcion.ANUAL.equals(s.getPlan()) ? 365 : 30;
	}

	private boolean cobroConfigurado() {
		return !llave.isEmpty() && !precioMensual.isEmpty() && !precioAnual.isEmpty();
	}

	/** Crea customer + subscription con el price del plan (llaves de plataforma). */
	private void crearEnStripe(Suscripcion s) {
		String precio = Suscripcion.ANUAL.equals(s.getPlan()) ? precioAnual : precioMensual;
		String intervalo = Suscripcion.ANUAL.equals(s.getPlan()) ? "year" : "month";
		try {
			JsonNode price = json.readTree(get("/v1/prices/" + URLEncoder.encode(precio, StandardCharsets.UTF_8)));
			String real = price.path("recurring").path("interval").asText();
			if (!intervalo.equals(real)) {
				throw new ConflictoException("price no coincide con el plan " + s.getPlan());
			}
		} catch (ConflictoException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("stripe no disponible", e);
		}
		String customer = idDe(post("/v1/customers",
				"metadata[tienda]=" + URLEncoder.encode(s.getTienda().getSlug(), StandardCharsets.UTF_8)));
		String subscription = idDe(post("/v1/subscriptions",
				"customer=" + URLEncoder.encode(customer, StandardCharsets.UTF_8)
						+ "&items[0][price]=" + URLEncoder.encode(precio, StandardCharsets.UTF_8)
						+ "&payment_behavior=default_incomplete"
						+ "&metadata[tienda]=" + URLEncoder.encode(s.getTienda().getSlug(), StandardCharsets.UTF_8)));
		s.setStripeCustomerId(customer);
		s.setStripeSubscriptionId(subscription);
	}

	private String post(String ruta, String cuerpo) {
		try {
			HttpResponse<String> respuesta = http.send(
					HttpRequest.newBuilder()
							.uri(URI.create(baseUrl + ruta))
							.timeout(Duration.ofSeconds(15))
							.header("Authorization", "Bearer " + llave)
							.header("Content-Type", "application/x-www-form-urlencoded")
							.POST(HttpRequest.BodyPublishers.ofString(cuerpo))
							.build(),
					HttpResponse.BodyHandlers.ofString());
			if (respuesta.statusCode() < 200 || respuesta.statusCode() >= 300) {
				throw new IllegalStateException("stripe " + respuesta.statusCode() + ": " + respuesta.body());
			}
			return respuesta.body();
		} catch (IllegalStateException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("stripe no disponible", e);
		}
	}

	private String get(String ruta) {
		try {
			HttpResponse<String> respuesta = http.send(
					HttpRequest.newBuilder()
							.uri(URI.create(baseUrl + ruta))
							.timeout(Duration.ofSeconds(15))
							.header("Authorization", "Bearer " + llave)
							.GET()
							.build(),
					HttpResponse.BodyHandlers.ofString());
			if (respuesta.statusCode() < 200 || respuesta.statusCode() >= 300) {
				throw new IllegalStateException("stripe " + respuesta.statusCode());
			}
			return respuesta.body();
		} catch (IllegalStateException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("stripe no disponible", e);
		}
	}

	private String idDe(String respuesta) {
		try {
			JsonNode id = json.readTree(respuesta).get("id");
			if (id == null || !id.isTextual()) {
				throw new IllegalStateException("stripe sin id");
			}
			return id.asText();
		} catch (IllegalStateException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("stripe respuesta inválida", e);
		}
	}

	private SuscripcionResponse responder(Suscripcion s) {
		return new SuscripcionResponse(s.getPlan(), s.getEstado(), s.getPeriodoFin());
	}
}
