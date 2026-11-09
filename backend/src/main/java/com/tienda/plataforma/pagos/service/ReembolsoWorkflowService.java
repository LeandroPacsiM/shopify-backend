package com.tienda.plataforma.pagos.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienda.plataforma.pagos.adapter.PaymentAdapter;
import com.tienda.plataforma.pagos.dto.ReembolsoResponse;
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
import com.tienda.plataforma.shared.auditoria.AuditoriaService;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.shared.security.FirmaStripe;
import com.tienda.plataforma.venta.entity.HistorialEstadoPedido;
import com.tienda.plataforma.venta.entity.Pedido;
import com.tienda.plataforma.venta.repository.HistorialEstadoPedidoRepository;
import com.tienda.plataforma.venta.repository.PedidoRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/** Reserva saldo de reembolso antes del proveedor y finaliza solo desde respuesta/webhook. */
@Service
public class ReembolsoWorkflowService {

	private static final String REFERENCIA_SOLICITUD = "reembolso-solicitud:";
	private static final String REFERENCIA_RESPUESTA = "reembolso-respuesta:";

	private final PagoRepository pagos;
	private final PagoEventoRepository eventos;
	private final ReembolsoRepository reembolsos;
	private final PasarelaPagoRepository pasarelas;
	private final TiendaPasarelaRepository tiendaPasarelas;
	private final PedidoRepository pedidos;
	private final HistorialEstadoPedidoRepository historial;
	private final EntityManager entityManager;
	private final Map<String, PaymentAdapter> adapters;
	private final AuditoriaService auditoria;
	private final ObjectMapper json = new ObjectMapper();
	private final TransactionTemplate tx;

	public ReembolsoWorkflowService(PagoRepository pagos, PagoEventoRepository eventos,
			ReembolsoRepository reembolsos, PasarelaPagoRepository pasarelas,
			TiendaPasarelaRepository tiendaPasarelas, PedidoRepository pedidos,
			HistorialEstadoPedidoRepository historial, EntityManager entityManager,
			List<PaymentAdapter> adapters, PlatformTransactionManager transactionManager,
			AuditoriaService auditoria) {
		this.pagos = pagos;
		this.eventos = eventos;
		this.reembolsos = reembolsos;
		this.pasarelas = pasarelas;
		this.tiendaPasarelas = tiendaPasarelas;
		this.pedidos = pedidos;
		this.historial = historial;
		this.entityManager = entityManager;
		this.adapters = adapters.stream().collect(Collectors.toMap(PaymentAdapter::codigo, Function.identity()));
		this.auditoria = auditoria;
		this.tx = new TransactionTemplate(transactionManager);
		this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/**
	 * Persiste la reserva del saldo antes de llamar al proveedor. Si se pierde
	 * la respuesta, el cliente puede reintentar con la misma clave idempotente.
	 */
	public ReembolsoResponse reembolsar(Long tiendaId, Long pagoId, BigDecimal monto,
			String motivo, String claveCliente) {
		OperacionReembolso operacion = tx.execute(status -> reservar(tiendaId, pagoId, monto, motivo, claveCliente));
		if (operacion == null) {
			throw new IllegalStateException("no se pudo reservar el reembolso");
		}
		if (!operacion.debeLlamarProveedor()) {
			return respuesta(operacion.reembolso());
		}

		PaymentAdapter adapter = adapters.get(operacion.codigoPasarela());
		if (adapter == null) {
			throw new ConflictoException("pasarela sin adapter");
		}
		PaymentAdapter.ReembolsoResultado resultado = adapter.reembolsar(
				operacion.credenciales(), operacion.transaccionPago(), operacion.reembolso().getMonto(),
				operacion.claveProveedor());
		ReembolsoResponse respuesta = tx.execute(status -> finalizar(operacion.reembolso().getId(), resultado));
		if (respuesta == null) {
			throw new IllegalStateException("no se pudo guardar el resultado del reembolso");
		}
		return respuesta;
	}

	private OperacionReembolso reservar(Long tiendaId, Long pagoId, BigDecimal monto,
			String motivo, String claveCliente) {
		BigDecimal montoSeguro = monto == null ? null : monto.setScale(2, RoundingMode.HALF_UP);
		if (montoSeguro == null || montoSeguro.signum() <= 0) {
			throw new ConflictoException("monto de reembolso inválido");
		}
		String clave = validarClave(claveCliente);
		String motivoSeguro = motivo == null ? null : motivo.trim();
		if (motivoSeguro != null && motivoSeguro.length() > 255) {
			throw new ConflictoException("motivo de reembolso demasiado largo");
		}

		Pago preliminar = pagos.findById(pagoId).orElseThrow(() -> new NoEncontradoException("pago"));
		Long pedidoId = preliminar.getPedido().getId();
		Pedido pedido = pedidos.findConBloqueoById(pedidoId)
				.orElseThrow(() -> new NoEncontradoException("pago"));
		entityManager.refresh(pedido, LockModeType.PESSIMISTIC_WRITE);
		Pago pago = pagos.findConBloqueoById(pagoId)
				.orElseThrow(() -> new NoEncontradoException("pago"));
		entityManager.refresh(pago, LockModeType.PESSIMISTIC_WRITE);
		if (!pedido.getTienda().getId().equals(tiendaId)
				|| !pago.getPedido().getId().equals(pedido.getId())) {
			throw new NoEncontradoException("pago");
		}

		String codigoPasarela = pago.getPasarela().getCodigo();
		String referencia = REFERENCIA_SOLICITUD + clave;
		var solicitudAnterior = eventos.findByPagoIdAndReferenciaExterna(pagoId, referencia);
		if (solicitudAnterior.isPresent()) {
			Long reembolsoId = reembolsoIdDesde(solicitudAnterior.get());
			Reembolso existente = reembolsos.findConBloqueoById(reembolsoId)
					.orElseThrow(() -> new ConflictoException("solicitud de reembolso inconsistente"));
			if (!existente.getPago().getId().equals(pagoId)
					|| existente.getMonto().compareTo(montoSeguro) != 0
					|| !Objects.equals(existente.getMotivo(), motivoSeguro)) {
				throw new ConflictoException("Idempotency-Key ya se usó con otra solicitud");
			}
			if (!Reembolso.PENDIENTE.equals(existente.getEstado())) {
				return new OperacionReembolso(existente, pago.getTransaccionExternaId(),
						codigoPasarela, null, null, false);
			}
			TiendaPasarela cred = credenciales(pedido, codigoPasarela);
			return new OperacionReembolso(existente, pago.getTransaccionExternaId(), codigoPasarela, cred,
					claveProveedor(pagoId, clave), true);
		}

		if (!Pago.APROBADO.equals(pago.getEstado())) {
			throw new ConflictoException("pago no aprobado");
		}
		if (pago.getTransaccionExternaId() == null || pago.getTransaccionExternaId().isBlank()) {
			throw new ConflictoException("pago sin referencia externa");
		}
		if (!pago.getMoneda().equals(pedido.getMoneda())) {
			throw new ConflictoException("moneda inconsistente");
		}
		PaymentAdapter adapter = adapters.get(codigoPasarela);
		if (adapter == null) {
			throw new ConflictoException("pasarela sin adapter");
		}
		TiendaPasarela cred = credenciales(pedido, codigoPasarela);
		BigDecimal reservado = reembolsos.findByPagoId(pagoId).stream()
				.filter(r -> !Reembolso.RECHAZADO.equals(r.getEstado()))
				.map(Reembolso::getMonto)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal saldo = pago.getMonto().subtract(reservado);
		if (montoSeguro.compareTo(saldo) > 0) {
			throw new ConflictoException("excede el saldo reembolsable");
		}

		Reembolso reembolso = new Reembolso();
		reembolso.setPedido(pedido);
		reembolso.setPago(pago);
		reembolso.setMonto(montoSeguro);
		reembolso.setMoneda(pago.getMoneda());
		reembolso.setMotivo(motivoSeguro);
		reembolso.setEstado(Reembolso.PENDIENTE);
		reembolsos.saveAndFlush(reembolso);

		PagoEvento solicitud = new PagoEvento();
		solicitud.setPago(pago);
		solicitud.setTipoEvento("reembolso.solicitud");
		solicitud.setEstadoAnterior(null);
		solicitud.setEstadoNuevo(Reembolso.PENDIENTE);
		solicitud.setReferenciaExterna(referencia);
		solicitud.setPayload("{\"reembolsoId\":" + reembolso.getId() + "}");
		solicitud.setCreadoEn(Instant.now());
		eventos.save(solicitud);
		auditoria.registrar("reembolsos", reembolso.getId(), "reembolso.solicitar");

		return new OperacionReembolso(reembolso, pago.getTransaccionExternaId(), codigoPasarela, cred,
				claveProveedor(pagoId, clave), true);
	}

	private ReembolsoResponse finalizar(Long reembolsoId, PaymentAdapter.ReembolsoResultado resultado) {
		if (resultado == null || resultado.estadoInicial() == null) {
			throw new ConflictoException("respuesta de reembolso inválida");
		}
		Reembolso preliminar = reembolsos.findById(reembolsoId)
				.orElseThrow(() -> new NoEncontradoException("reembolso"));
		Long pedidoId = preliminar.getPedido().getId();
		Long pagoId = preliminar.getPago().getId();
		Pedido pedido = pedidos.findConBloqueoById(pedidoId)
				.orElseThrow(() -> new NoEncontradoException("pedido"));
		entityManager.refresh(pedido, LockModeType.PESSIMISTIC_WRITE);
		Pago pago = pagos.findConBloqueoById(pagoId)
				.orElseThrow(() -> new NoEncontradoException("pago"));
		entityManager.refresh(pago, LockModeType.PESSIMISTIC_WRITE);
		Reembolso reembolso = reembolsos.findConBloqueoById(reembolsoId)
				.orElseThrow(() -> new NoEncontradoException("reembolso"));
		entityManager.refresh(reembolso, LockModeType.PESSIMISTIC_WRITE);
		if (!Reembolso.PENDIENTE.equals(reembolso.getEstado())) {
			return respuesta(reembolso);
		}

		String estado = resultado.estadoInicial();
		if (!List.of(Reembolso.PENDIENTE, Reembolso.PROCESANDO,
				Reembolso.APROBADO, Reembolso.RECHAZADO).contains(estado)) {
			throw new ConflictoException("estado inicial de reembolso inválido");
		}
		if ((Reembolso.PROCESANDO.equals(estado) || Reembolso.APROBADO.equals(estado))
				&& (resultado.transaccionExternaId() == null || resultado.transaccionExternaId().isBlank())) {
			throw new ConflictoException("proveedor no devolvió la referencia del reembolso");
		}
		reembolso.setEstado(estado);
		reembolso.setTransaccionExternaId(resultado.transaccionExternaId());
		reembolsos.save(reembolso);
		auditoria.registrar("reembolsos", reembolsoId, "reembolso.proveedor");
		if (!Reembolso.PENDIENTE.equals(estado)) {
			registrarEvento(pago, "reembolso.proveedor", Reembolso.PENDIENTE, estado,
					REFERENCIA_RESPUESTA + reembolsoId);
		}
		if (Reembolso.APROBADO.equals(estado)) {
			reconciliar(pago, pedido);
		}
		return respuesta(reembolso);
	}

	/** Verifica y aplica el resultado final que notificó Stripe. */
	@Transactional
	public void confirmarDesdeStripe(String transaccionReembolso, long centimos, String eventId,
			String firma, String cuerpo, boolean fallido) {
		Reembolso preliminar = reembolsos.findByTransaccionExternaId(transaccionReembolso)
				.orElseThrow(() -> new NoEncontradoException("reembolso"));
		Pago pagoPreliminar = preliminar.getPago();
		String codigo = pagoPreliminar.getPasarela().getCodigo();
		TiendaPasarela cred = credenciales(pagoPreliminar.getPedido(), codigo);
		PaymentAdapter adapter = adapters.get(codigo);
		if (adapter == null || !adapter.verificaFirma(cred, cuerpo, firma)) {
			throw new NoAutorizadoException("firma inválida");
		}

		Long pedidoId = pagoPreliminar.getPedido().getId();
		Long pagoId = pagoPreliminar.getId();
		Long reembolsoId = preliminar.getId();
		Pedido pedido = pedidos.findConBloqueoById(pedidoId)
				.orElseThrow(() -> new NoEncontradoException("pedido"));
		entityManager.refresh(pedido, LockModeType.PESSIMISTIC_WRITE);
		Pago pago = pagos.findConBloqueoById(pagoId)
				.orElseThrow(() -> new NoEncontradoException("pago"));
		entityManager.refresh(pago, LockModeType.PESSIMISTIC_WRITE);
		Reembolso reembolso = reembolsos.findConBloqueoById(reembolsoId)
				.orElseThrow(() -> new NoEncontradoException("reembolso"));
		entityManager.refresh(reembolso, LockModeType.PESSIMISTIC_WRITE);

		if (!Objects.equals(transaccionReembolso, reembolso.getTransaccionExternaId())
				|| !reembolso.getPago().getId().equals(pago.getId())
				|| !reembolso.getPedido().getId().equals(pedido.getId())) {
			throw new NoEncontradoException("reembolso");
		}
		if (eventos.findByPagoIdAndReferenciaExterna(pagoId, eventId).isPresent()) {
			return;
		}
		if (centimos < 0 || centimos != centimosDe(reembolso.getMonto())) {
			throw new ConflictoException("monto inconsistente");
		}

		String anterior = reembolso.getEstado();
		if (Reembolso.PENDIENTE.equals(anterior) || Reembolso.PROCESANDO.equals(anterior)) {
			reembolso.setEstado(fallido ? Reembolso.RECHAZADO : Reembolso.APROBADO);
			reembolsos.save(reembolso);
			auditoria.registrar("reembolsos", reembolsoId, "reembolso.webhook");
		}
		registrarEvento(pago, "webhook.reembolso." + reembolso.getEstado(), anterior,
				reembolso.getEstado(), eventId);
		if (Reembolso.APROBADO.equals(reembolso.getEstado())) {
			reconciliar(pago, pedido);
		}
	}

	private void reconciliar(Pago pago, Pedido pedido) {
		BigDecimal aprobadoDelPago = reembolsos.findByPagoId(pago.getId()).stream()
				.filter(r -> Reembolso.APROBADO.equals(r.getEstado()))
				.map(Reembolso::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
		if (aprobadoDelPago.compareTo(pago.getMonto()) >= 0
				&& !Pago.REEMBOLSADO.equals(pago.getEstado())) {
			pago.setEstado(Pago.REEMBOLSADO);
			pagos.save(pago);
		}

		BigDecimal aprobadoDelPedido = reembolsos.findByPedidoId(pedido.getId()).stream()
				.filter(r -> Reembolso.APROBADO.equals(r.getEstado()))
				.map(Reembolso::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
		if (aprobadoDelPedido.compareTo(pedido.getTotal()) >= 0
				&& !Pedido.REEMBOLSADO.equals(pedido.getEstado())) {
			String anterior = pedido.getEstado();
			pedido.setEstado(Pedido.REEMBOLSADO);
			pedido.setActualizadoEn(Instant.now());
			pedidos.save(pedido);
			historial(pedido, anterior, Pedido.REEMBOLSADO, "Reembolso total confirmado");
		}
	}

	private TiendaPasarela credenciales(Pedido pedido, String codigo) {
		PasarelaPago pasarela = pasarelas.findByCodigo(codigo)
				.filter(PasarelaPago::getActivo)
				.orElseThrow(() -> new NoEncontradoException("pasarela"));
		return tiendaPasarelas.findByTiendaIdAndPasarelaId(pedido.getTienda().getId(), pasarela.getId())
				.filter(TiendaPasarela::getActivo)
				.orElseThrow(() -> new ConflictoException("pasarela no configurada en la tienda"));
	}

	private String validarClave(String clave) {
		if (clave == null || !clave.trim().matches("[A-Za-z0-9._~-]{1,200}")) {
			throw new ConflictoException("Idempotency-Key inválido");
		}
		return clave.trim();
	}

	private String claveProveedor(Long pagoId, String clave) {
		return "reembolso-" + pagoId + "-" + clave;
	}

	private Long reembolsoIdDesde(PagoEvento evento) {
		try {
			JsonNode id = json.readTree(evento.getPayload()).path("reembolsoId");
			if (!id.isIntegralNumber() || !id.canConvertToLong()) {
				throw new IllegalArgumentException("id de reembolso inválido");
			}
			return id.asLong();
		} catch (Exception e) {
			throw new ConflictoException("solicitud de reembolso inconsistente");
		}
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
		HistorialEstadoPedido entrada = new HistorialEstadoPedido();
		entrada.setPedido(pedido);
		entrada.setEstadoAnterior(anterior);
		entrada.setEstadoNuevo(nuevo);
		entrada.setComentario(comentario);
		entrada.setCreadoEn(Instant.now());
		historial.save(entrada);
	}

	private long centimosDe(BigDecimal monto) {
		return monto.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
	}

	private ReembolsoResponse respuesta(Reembolso reembolso) {
		return new ReembolsoResponse(reembolso.getId(), reembolso.getEstado(),
				reembolso.getMonto(), reembolso.getTransaccionExternaId());
	}

	private record OperacionReembolso(Reembolso reembolso, String transaccionPago,
			String codigoPasarela, TiendaPasarela credenciales, String claveProveedor,
			boolean debeLlamarProveedor) {
	}
}
