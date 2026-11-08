package com.tienda.plataforma.pagos.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
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
import com.tienda.plataforma.pagos.entity.Reembolso;
import com.tienda.plataforma.pagos.entity.TiendaPasarela;
import com.tienda.plataforma.pagos.repository.PagoEventoRepository;
import com.tienda.plataforma.pagos.repository.PagoRepository;
import com.tienda.plataforma.pagos.repository.PasarelaPagoRepository;
import com.tienda.plataforma.pagos.repository.ReembolsoRepository;
import com.tienda.plataforma.pagos.repository.TiendaPasarelaRepository;
import com.tienda.plataforma.realtime.NotificacionService;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.venta.entity.HistorialEstadoPedido;
import com.tienda.plataforma.venta.entity.Pedido;
import com.tienda.plataforma.venta.repository.HistorialEstadoPedidoRepository;
import com.tienda.plataforma.venta.repository.PedidoRepository;

/**
 * RF-PAY: intentos, webhooks idempotentes y reembolsos con saldo (diagrama 06, V3).
 * La moneda del pago/reembolso debe igualar la del pedido (validado aquí).
 */
@Service
public class PagoService {

	private final PagoRepository pagos;
	private final PagoEventoRepository eventos;
	private final ReembolsoRepository reembolsos;
	private final PasarelaPagoRepository pasarelas;
	private final TiendaPasarelaRepository tiendaPasarelas;
	private final PedidoRepository pedidos;
	private final HistorialEstadoPedidoRepository historial;
	private final NotificacionService notificaciones;
	private final Map<String, PaymentAdapter> adapters;
	private final ObjectMapper json = new ObjectMapper();

	public PagoService(PagoRepository pagos, PagoEventoRepository eventos, ReembolsoRepository reembolsos,
			PasarelaPagoRepository pasarelas, TiendaPasarelaRepository tiendaPasarelas,
			PedidoRepository pedidos, HistorialEstadoPedidoRepository historial,
			NotificacionService notificaciones, List<PaymentAdapter> adapters) {
		this.pagos = pagos;
		this.eventos = eventos;
		this.reembolsos = reembolsos;
		this.pasarelas = pasarelas;
		this.tiendaPasarelas = tiendaPasarelas;
		this.pedidos = pedidos;
		this.historial = historial;
		this.notificaciones = notificaciones;
		this.adapters = adapters.stream().collect(Collectors.toMap(PaymentAdapter::codigo, Function.identity()));
	}

	@Transactional
	public PagoResponse crearIntento(Long usuarioId, Long pedidoId, String codigoPasarela, String idempotencia) {
		Pedido pedido = pedidoDe(usuarioId, pedidoId);
		if (Pedido.CANCELADO.equals(pedido.getEstado()) || Pedido.ENTREGADO.equals(pedido.getEstado())) {
			throw new ConflictoException("pedido no cobrable");
		}
		BigDecimal restante = restante(pedido);
		if (restante.signum() <= 0) {
			throw new ConflictoException("pedido ya cubierto");
		}
		TiendaPasarela creds = credenciales(pedido, codigoPasarela);
		PaymentAdapter adapter = adapters.get(creds.getPasarela().getCodigo());
		if (adapter == null) {
			throw new ConflictoException("pasarela sin adapter");
		}
		var cargo = adapter.autorizar(creds, restante, pedido.getMoneda(), idempotencia);

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
		evento.setReferenciaExterna("intento:" + idempotencia);
		evento.setCreadoEn(Instant.now());
		eventos.save(evento);
		return new PagoResponse(pago.getId(), pago.getEstado(), pago.getMonto(), pago.getTransaccionExternaId());
	}

	/** Webhook: firma + dedup por event-id + transición válida, todo en TX. */
	@Transactional
	public void webhook(String codigoPasarela, WebhookPagoRequest req) {
		PasarelaPago pasarela = pasarelas.findByCodigo(codigoPasarela)
				.orElseThrow(() -> new NoAutorizadoException("pasarela"));
		Pago pago = pagos.findByPasarelaIdAndTransaccionExternaId(pasarela.getId(), req.transaccionId())
				.orElseThrow(() -> new NoEncontradoException("pago"));
		TiendaPasarela creds = tiendaPasarelas
				.findByTiendaIdAndPasarelaId(pago.getPedido().getTienda().getId(), pasarela.getId())
				.orElseThrow(() -> new NoAutorizadoException("pasarela"));
		PaymentAdapter adapter = adapters.get(pasarela.getCodigo());
		if (adapter == null || !adapter.verificaFirma(creds, req.cuerpo(), req.firma())) {
			throw new NoAutorizadoException("firma inválida");
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
			Pedido pedido = pago.getPedido();
			if (restante(pedido).signum() <= 0 && Pedido.PENDIENTE.equals(pedido.getEstado())) {
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
				confirmarReembolso(objeto.path("id").asText(),
						objeto.path("amount").asLong(-1), eventId, firma, cuerpo,
						"refund.failed".equals(tipo));
			}
			default -> {
				return;
			}
		}
	}

	private void confirmarReembolso(String reId, long centimos, String eventId,
			String firma, String cuerpo, boolean fallido) {
		Reembolso reembolso = reembolsos.findByTransaccionExternaId(reId)
				.orElseThrow(() -> new NoEncontradoException("reembolso"));
		Pago pago = reembolso.getPago();
		TiendaPasarela creds = tiendaPasarelas
				.findByTiendaIdAndPasarelaId(pago.getPedido().getTienda().getId(),
						pago.getPasarela().getId())
				.orElseThrow(() -> new NoAutorizadoException("pasarela"));
		PaymentAdapter adapter = adapters.get(pago.getPasarela().getCodigo());
		if (adapter == null || !adapter.verificaFirma(creds, cuerpo, firma)) {
			throw new NoAutorizadoException("firma inválida");
		}
		if (eventos.findByPagoIdAndReferenciaExterna(pago.getId(), eventId).isPresent()) {
			return;
		}
		if (centimos >= 0 && centimos != centimosDe(reembolso.getMonto())) {
			throw new ConflictoException("monto inconsistente");
		}
		String anterior = reembolso.getEstado();
		String nuevo = fallido ? Reembolso.RECHAZADO : Reembolso.APROBADO;
		if (Reembolso.PROCESANDO.equals(anterior) || Reembolso.PENDIENTE.equals(anterior)) {
			reembolso.setEstado(nuevo);
			reembolsos.save(reembolso);
		}
		registrarEvento(pago, "webhook.reembolso." + nuevo, anterior,
				reembolso.getEstado(), eventId);
	}

	private void verificarMonto(String codigoPasarela, String transaccionId, long centimos) {
		if (centimos < 0) {
			return;
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

	@Transactional
	public ReembolsoResponse reembolsar(Long tiendaId, Long pagoId, BigDecimal monto, String motivo) {
		Pago pago = pagos.findById(pagoId).orElseThrow(() -> new NoEncontradoException("pago"));
		Pedido pedido = pago.getPedido();
		if (!pedido.getTienda().getId().equals(tiendaId) || !pago.getPedido().getId().equals(pedido.getId())) {
			throw new NoEncontradoException("pago");
		}
		if (!Pago.APROBADO.equals(pago.getEstado())) {
			throw new ConflictoException("pago no aprobado");
		}
		if (!pago.getMoneda().equals(pedido.getMoneda())) {
			throw new ConflictoException("moneda inconsistente");
		}
		BigDecimal yaDevuelto = reembolsos.findByPagoId(pagoId).stream()
				.filter(r -> !Reembolso.RECHAZADO.equals(r.getEstado()))
				.map(Reembolso::getMonto)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal saldo = pago.getMonto().subtract(yaDevuelto);
		if (monto.compareTo(saldo) > 0) {
			throw new ConflictoException("excede el saldo reembolsable");
		}
		TiendaPasarela creds = credenciales(pedido, pago.getPasarela().getCodigo());
		PaymentAdapter adapter = adapters.get(pago.getPasarela().getCodigo());
		var devolucion = adapter.reembolsar(creds, pago.getTransaccionExternaId(), monto);

		Reembolso reembolso = new Reembolso();
		reembolso.setPedido(pedido);
		reembolso.setPago(pago);
		reembolso.setMonto(monto);
		reembolso.setMoneda(pago.getMoneda());
		reembolso.setMotivo(motivo);
		reembolso.setEstado(devolucion.estadoInicial());
		reembolso.setTransaccionExternaId(devolucion.transaccionExternaId());
		reembolso.setCreadoEn(Instant.now());
		reembolsos.save(reembolso);
		registrarEvento(pago, "reembolso.solicitado", pago.getEstado(), pago.getEstado(),
				"reembolso:" + reembolso.getId());

		if (saldo.subtract(monto).signum() == 0) {
			pago.setEstado(Pago.REEMBOLSADO);
			pagos.save(pago);
			pedido.setEstado(Pedido.REEMBOLSADO);
			pedido.setActualizadoEn(Instant.now());
			pedidos.save(pedido);
			historial(pedido, Pedido.PAGADO, Pedido.REEMBOLSADO, "Reembolso total");
		}
		return new ReembolsoResponse(reembolso.getId(), reembolso.getEstado(), reembolso.getMonto(),
				reembolso.getTransaccionExternaId());
	}

	private Pedido pedidoDe(Long usuarioId, Long pedidoId) {
		Pedido pedido = pedidos.findById(pedidoId).orElseThrow(() -> new NoEncontradoException("pedido"));
		if (!pedido.getCliente().getUsuario().getId().equals(usuarioId)) {
			throw new NoEncontradoException("pedido");
		}
		return pedido;
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
		BigDecimal aprobado = pagos.findByPedidoId(pedido.getId()).stream()
				.filter(p -> Pago.APROBADO.equals(p.getEstado()))
				.map(Pago::getMonto)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		return pedido.getTotal().subtract(aprobado);
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
