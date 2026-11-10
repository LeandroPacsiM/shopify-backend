package com.tienda.plataforma.pagos.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienda.plataforma.pagos.adapter.PaymentAdapter;
import com.tienda.plataforma.pagos.dto.PagoResponse;
import com.tienda.plataforma.pagos.dto.ReembolsoResponse;
import com.tienda.plataforma.pagos.dto.WebhookPagoRequest;
import com.tienda.plataforma.pagos.entity.Pago;
import com.tienda.plataforma.pagos.entity.PagoEvento;
import com.tienda.plataforma.pagos.entity.PasarelaPago;
import com.tienda.plataforma.pagos.entity.TiendaPasarela;
import com.tienda.plataforma.pagos.repository.PagoEventoRepository;
import com.tienda.plataforma.pagos.repository.PagoRepository;
import com.tienda.plataforma.pagos.repository.PasarelaPagoRepository;
import com.tienda.plataforma.pagos.repository.TiendaPasarelaRepository;
import com.tienda.plataforma.realtime.NotificacionService;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.venta.entity.HistorialEstadoPedido;
import com.tienda.plataforma.venta.entity.Pedido;
import com.tienda.plataforma.venta.repository.HistorialEstadoPedidoRepository;
import com.tienda.plataforma.venta.repository.PedidoRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/**
 * RF-PAY: intentos, webhooks idempotentes y reembolsos con saldo (diagrama 06, V3).
 * La moneda del pago/reembolso debe igualar la del pedido (validado aquí).
 */
@Service
public class PagoService {

	private final PagoRepository pagos;
	private final PagoEventoRepository eventos;
	private final PasarelaPagoRepository pasarelas;
	private final TiendaPasarelaRepository tiendaPasarelas;
	private final PedidoRepository pedidos;
	private final HistorialEstadoPedidoRepository historial;
	private final NotificacionService notificaciones;
	private final ReembolsoWorkflowService reembolsoWorkflow;
	private final EntityManager entityManager;
	private final Map<String, PaymentAdapter> adapters;
	private final ObjectMapper json = new ObjectMapper();

	public PagoService(PagoRepository pagos, PagoEventoRepository eventos,
			PasarelaPagoRepository pasarelas, TiendaPasarelaRepository tiendaPasarelas,
			PedidoRepository pedidos, HistorialEstadoPedidoRepository historial,
			NotificacionService notificaciones, ReembolsoWorkflowService reembolsoWorkflow,
			EntityManager entityManager, List<PaymentAdapter> adapters) {
		this.pagos = pagos;
		this.eventos = eventos;
		this.pasarelas = pasarelas;
		this.tiendaPasarelas = tiendaPasarelas;
		this.pedidos = pedidos;
		this.historial = historial;
		this.notificaciones = notificaciones;
		this.reembolsoWorkflow = reembolsoWorkflow;
		this.entityManager = entityManager;
		this.adapters = adapters.stream().collect(Collectors.toMap(PaymentAdapter::codigo, Function.identity()));
	}

	@Transactional
	public PagoResponse crearIntento(Long clienteId, Long pedidoId, String codigoPasarela, String idempotencia) {
		Pedido pedido = pedidoDe(clienteId, pedidoId);
		String claveCliente = validarIdempotencia(idempotencia);
		String codigo = codigoPasarela.trim().toLowerCase(Locale.ROOT);
		if (Pedido.CANCELADO.equals(pedido.getEstado()) || Pedido.ENTREGADO.equals(pedido.getEstado())
				|| Pedido.REEMBOLSADO.equals(pedido.getEstado())) {
			throw new ConflictoException("pedido no cobrable");
		}
		var intentoPrevio = eventos.findByPedidoIdAndReferenciaExterna(pedidoId, "intento:" + claveCliente);
		if (intentoPrevio.isPresent()) {
			PagoEvento eventoPrevio = intentoPrevio.get();
			if (!"intento.creado".equals(eventoPrevio.getTipoEvento())) {
				throw new ConflictoException("Idempotency-Key ya está asociado a otro evento");
			}
			Pago anterior = eventoPrevio.getPago();
			if (!codigo.equals(anterior.getPasarela().getCodigo())) {
				throw new ConflictoException("Idempotency-Key ya se usó con otra pasarela");
			}
			return new PagoResponse(anterior.getId(), anterior.getEstado(), anterior.getMonto(),
					anterior.getTransaccionExternaId());
		}
		BigDecimal restante = restante(pedido);
		if (restante.signum() <= 0) {
			throw new ConflictoException("pedido ya cubierto");
		}
		TiendaPasarela creds = credenciales(pedido, codigo);
		PaymentAdapter adapter = adapters.get(creds.getPasarela().getCodigo());
		if (adapter == null) {
			throw new ConflictoException("pasarela sin adapter");
		}
		var cargo = adapter.autorizar(creds, restante, pedido.getMoneda(), claveProveedorIntento(pedidoId, claveCliente));

		Pago pago = new Pago();
		pago.setPedido(pedido);
		pago.setPasarela(creds.getPasarela());
		pago.setEstado(cargo.estadoInicial());
		pago.setMonto(restante);
		pago.setMoneda(pedido.getMoneda());
		pago.setTransaccionExternaId(cargo.transaccionExternaId());
		pago.setMetodo(cargo.metodo());
		pago.setCreadoEn(Instant.now());
		pagos.save(pago);

		PagoEvento evento = new PagoEvento();
		evento.setPago(pago);
		evento.setTipoEvento("intento.creado");
		evento.setEstadoAnterior(null);
		evento.setEstadoNuevo(pago.getEstado());
		evento.setReferenciaExterna("intento:" + claveCliente);
		evento.setCreadoEn(Instant.now());
		eventos.save(evento);
		return new PagoResponse(pago.getId(), pago.getEstado(), pago.getMonto(), pago.getTransaccionExternaId());
	}

	/** Webhook: firma + dedup por event-id + transición válida, todo en TX. */
	@Transactional
	public void webhook(String codigoPasarela, WebhookPagoRequest req) {
		PasarelaPago pasarela = pasarelas.findByCodigo(codigoPasarela)
				.orElseThrow(() -> new NoAutorizadoException("pasarela"));
		Pago encontrado = pagos.findByPasarelaIdAndTransaccionExternaId(pasarela.getId(), req.transaccionId())
				.orElseThrow(() -> new NoEncontradoException("pago"));
		TiendaPasarela creds = tiendaPasarelas
				.findByTiendaIdAndPasarelaId(encontrado.getPedido().getTienda().getId(), pasarela.getId())
				.orElseThrow(() -> new NoAutorizadoException("pasarela"));
		PaymentAdapter adapter = adapters.get(pasarela.getCodigo());
		if (adapter == null || !adapter.verificaFirma(creds, req.cuerpo(), req.firma())) {
			throw new NoAutorizadoException("firma inválida");
		}

		Pedido pedido = pedidos.findConBloqueoById(encontrado.getPedido().getId())
				.orElseThrow(() -> new NoEncontradoException("pedido"));
		entityManager.refresh(pedido, LockModeType.PESSIMISTIC_WRITE);
		Pago pago = pagos.findConBloqueoById(encontrado.getId())
				.orElseThrow(() -> new NoEncontradoException("pago"));
		entityManager.refresh(pago, LockModeType.PESSIMISTIC_WRITE);
		if (!pasarela.getId().equals(pago.getPasarela().getId())
				|| !req.transaccionId().equals(pago.getTransaccionExternaId())) {
			throw new NoEncontradoException("pago");
		}
		if (eventos.findByPagoIdAndReferenciaExterna(pago.getId(), req.eventId()).isPresent()) {
			return;
		}
		String nuevo = mapearEstado(req.estado());
		if (!Pago.PENDIENTE.equals(pago.getEstado()) && !Pago.PROCESANDO.equals(pago.getEstado())) {
			registrarEvento(pago, "webhook.tardio", pago.getEstado(), pago.getEstado(), req.eventId());
			return;
		}
		String anterior = pago.getEstado();
		pago.setEstado(nuevo);
		if (Pago.APROBADO.equals(nuevo)) {
			pago.setPagadoEn(Instant.now());
		}
		pagos.save(pago);
		registrarEvento(pago, "webhook." + req.estado(), anterior, nuevo, req.eventId());

		if (Pago.APROBADO.equals(nuevo)) {
			if (cobrado(pedido).compareTo(pedido.getTotal()) >= 0
					&& Pedido.PENDIENTE.equals(pedido.getEstado())) {
				pedido.setEstado(Pedido.PAGADO);
				pedido.setActualizadoEn(Instant.now());
				pedidos.save(pedido);
				historial(pedido, Pedido.PENDIENTE, Pedido.PAGADO, "Pago aprobado");
				notificaciones.notificar(pedido.getTienda().getId(), NotificacionService.PEDIDO_PAGADO,
						Map.of("pedidoId", pedido.getId(), "numero", pedido.getNumeroPedido()));
			}
		}
	}

	/**
	 * Traductor Stripe→envelope interno (cierra ADR-013). Recibe el evento
	 * crudo tal como lo manda Stripe (`Stripe-Signature` aparte) y lo lleva
	 * al flujo idempotente existente. Tipos desconocidos se ignoran con 200
	 * (Stripe reintenta lo no-2xx). Sin migración: reutiliza `pagos`,
	 * `reembolsos` y `pago_eventos`.
	 */
	@Transactional
	public void webhookStripe(String cuerpo, String firma) {
		JsonNode raiz = parsear(cuerpo);
		String eventId = texto(raiz, "id");
		String tipo = texto(raiz, "type");
		JsonNode objeto = raiz.path("data").path("object");
		switch (tipo) {
			case "payment_intent.succeeded", "payment_intent.payment_failed",
					"payment_intent.canceled" -> {
				String pi = objeto.path("id").asText();
				verificarMonto("stripe", pi, objeto.path("amount").asLong(-1));
				webhook("stripe", new WebhookPagoRequest(eventId, pi,
						estadoDesdeStripe(tipo), firma, cuerpo));
			}
			case "refund.succeeded", "refund.updated", "refund.failed" -> {
				if ("refund.updated".equals(tipo) && !"succeeded".equals(objeto.path("status").asText())) {
					return;
				}
				reembolsoWorkflow.confirmarDesdeStripe(objeto.path("id").asText(),
						objeto.path("amount").asLong(-1), eventId, firma, cuerpo,
						"refund.failed".equals(tipo));
			}
			default -> {
				return;
			}
		}
	}

	private void verificarMonto(String codigoPasarela, String transaccionId, long centimos) {
		if (centimos < 0) {
			throw new ConflictoException("monto de webhook inválido");
		}
		PasarelaPago pasarela = pasarelas.findByCodigo(codigoPasarela)
				.orElseThrow(() -> new NoEncontradoException("pasarela"));
		Pago pago = pagos.findByPasarelaIdAndTransaccionExternaId(pasarela.getId(), transaccionId)
				.orElseThrow(() -> new NoEncontradoException("pago"));
		if (centimos != centimosDe(pago.getMonto())) {
			throw new ConflictoException("monto inconsistente");
		}
	}

	private long centimosDe(BigDecimal monto) {
		return monto.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
	}

	private String estadoDesdeStripe(String tipo) {
		return switch (tipo) {
			case "payment_intent.succeeded" -> "aprobado";
			case "payment_intent.payment_failed" -> "rechazado";
			default -> "cancelado";
		};
	}

	private JsonNode parsear(String cuerpo) {
		try {
			return json.readTree(cuerpo);
		} catch (Exception e) {
			throw new ConflictoException("evento inválido");
		}
	}

	private String texto(JsonNode raiz, String campo) {
		JsonNode n = raiz.get(campo);
		if (n == null || !n.isTextual()) {
			throw new ConflictoException("evento inválido");
		}
		return n.asText();
	}

	public ReembolsoResponse reembolsar(Long tiendaId, Long pagoId, BigDecimal monto,
			String motivo, String idempotencia) {
		return reembolsoWorkflow.reembolsar(tiendaId, pagoId, monto, motivo, idempotencia);
	}

	private Pedido pedidoDe(Long clienteId, Long pedidoId) {
		Pedido pedido = pedidos.findConBloqueoById(pedidoId)
				.orElseThrow(() -> new NoEncontradoException("pedido"));
		entityManager.refresh(pedido, LockModeType.PESSIMISTIC_WRITE);
		if (!pedido.getCliente().getId().equals(clienteId)) {
			throw new NoEncontradoException("pedido");
		}
		return pedido;
	}

	private String validarIdempotencia(String idempotencia) {
		if (idempotencia == null || !idempotencia.trim().matches("[A-Za-z0-9._~-]{1,200}")) {
			throw new ConflictoException("idempotencia inválida");
		}
		return idempotencia.trim();
	}

	private String claveProveedorIntento(Long pedidoId, String clave) {
		return "pago-" + pedidoId + "-" + clave;
	}

	private TiendaPasarela credenciales(Pedido pedido, String codigoPasarela) {
		PasarelaPago pasarela = pasarelas.findByCodigo(codigoPasarela)
				.filter(PasarelaPago::getActivo)
				.orElseThrow(() -> new NoEncontradoException("pasarela"));
		return tiendaPasarelas.findByTiendaIdAndPasarelaId(pedido.getTienda().getId(), pasarela.getId())
				.filter(TiendaPasarela::getActivo)
				.orElseThrow(() -> new ConflictoException("pasarela no configurada en la tienda"));
	}

	private BigDecimal restante(Pedido pedido) {
		BigDecimal comprometido = pagos.findByPedidoId(pedido.getId()).stream()
				.filter(p -> Pago.APROBADO.equals(p.getEstado())
						|| Pago.PENDIENTE.equals(p.getEstado())
						|| Pago.PROCESANDO.equals(p.getEstado()))
				.map(Pago::getMonto)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		return pedido.getTotal().subtract(comprometido);
	}

	private BigDecimal cobrado(Pedido pedido) {
		return pagos.findByPedidoId(pedido.getId()).stream()
				.filter(p -> Pago.APROBADO.equals(p.getEstado()))
				.map(Pago::getMonto)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private String mapearEstado(String externo) {
		return switch (externo.toLowerCase()) {
			case "aprobado", "approved", "succeeded" -> Pago.APROBADO;
			case "rechazado", "failed", "declined" -> Pago.RECHAZADO;
			case "cancelado", "canceled", "voided" -> Pago.CANCELADO;
			default -> Pago.PROCESANDO;
		};
	}

	private void registrarEvento(Pago pago, String tipo, String anterior, String nuevo, String referencia) {
		PagoEvento evento = new PagoEvento();
		evento.setPago(pago);
		evento.setTipoEvento(tipo);
		evento.setEstadoAnterior(anterior);
		evento.setEstadoNuevo(nuevo);
		evento.setReferenciaExterna(referencia);
		evento.setCreadoEn(Instant.now());
		eventos.save(evento);
	}

	private void historial(Pedido pedido, String anterior, String nuevo, String comentario) {
		HistorialEstadoPedido h = new HistorialEstadoPedido();
		h.setPedido(pedido);
		h.setEstadoAnterior(anterior);
		h.setEstadoNuevo(nuevo);
		h.setComentario(comentario);
		h.setCreadoEn(Instant.now());
		historial.save(h);
	}
}
