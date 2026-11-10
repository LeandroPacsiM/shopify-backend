package com.tienda.plataforma.pagos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.pagos.entity.Pago;
import com.tienda.plataforma.pagos.entity.PasarelaPago;
import com.tienda.plataforma.pagos.entity.Reembolso;
import com.tienda.plataforma.pagos.entity.TiendaPasarela;
import com.tienda.plataforma.pagos.repository.PagoRepository;
import com.tienda.plataforma.pagos.repository.PasarelaPagoRepository;
import com.tienda.plataforma.pagos.repository.ReembolsoRepository;
import com.tienda.plataforma.pagos.repository.TiendaPasarelaRepository;
import com.tienda.plataforma.pagos.service.PagoService;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.venta.entity.Pedido;
import com.tienda.plataforma.venta.repository.PedidoRepository;

/** PAY-01: locks, saldo reservado e idempotencia ante solicitudes paralelas. */
@SpringBootTest
@ActiveProfiles("test")
class PagoCarrerasTest {

	@Autowired
	PagoService pagoService;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	ClienteRepository clientes;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	PedidoRepository pedidos;
	@Autowired
	PagoRepository pagos;
	@Autowired
	ReembolsoRepository reembolsos;
	@Autowired
	PasarelaPagoRepository pasarelas;
	@Autowired
	TiendaPasarelaRepository tiendaPasarelas;

	Tienda tienda;
	Usuario usuario;
	Pedido pedido;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		tienda = new Tienda();
		tienda.setNombre("PC-" + tag);
		tienda.setSlug("pc-" + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);

		usuario = new Usuario();
		usuario.setEmail("pc-" + tag + "@example.com");
		usuario.setPasswordHash("test-only");
		usuario.setActivo(true);
		usuario.setEmailVerificado(true);
		usuario.setIntentosFallidos(0);
		usuarios.save(usuario);

		Cliente cliente = new Cliente();
		cliente.setUsuario(usuario);
		cliente.setTienda(tienda);
		cliente.setAceptaMarketing(false);
		cliente.setEmail(cliente.getUsuario().getEmail());
		clientes.save(cliente);

		pedido = new Pedido();
		pedido.setTienda(tienda);
		pedido.setCliente(cliente);
		pedido.setNumeroPedido("PC-" + tag);
		pedido.setEstado(Pedido.PENDIENTE);
		pedido.setMoneda("PEN");
		pedido.setSubtotal(new BigDecimal("100.00"));
		pedido.setImpuestoTotal(BigDecimal.ZERO);
		pedido.setEnvioTotal(BigDecimal.ZERO);
		pedido.setDescuentoTotal(BigDecimal.ZERO);
		pedido.setTotal(new BigDecimal("100.00"));
		pedidos.save(pedido);

		PasarelaPago mock = pasarelas.findByCodigo("mock").orElseGet(() -> {
			PasarelaPago nueva = new PasarelaPago();
			nueva.setNombre("Mock");
			nueva.setCodigo("mock");
			nueva.setActivo(true);
			return pasarelas.save(nueva);
		});
		TiendaPasarela credencial = new TiendaPasarela();
		credencial.setTienda(tienda);
		credencial.setPasarela(mock);
		credencial.setClavePublica("pk_test_mock");
		credencial.setSecretoCifrado("secreto-mock-cifrado");
		credencial.setModo(TiendaPasarela.TEST);
		credencial.setActivo(true);
		tiendaPasarelas.save(credencial);
	}

	@Test
	void dosIntentosParalelosNoComprometenMasQueElTotal() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Callable<Boolean>> llamadas = List.of(
					() -> crearIntento("charge-race-a"),
					() -> crearIntento("charge-race-b"));
			List<Future<Boolean>> resultados = pool.invokeAll(llamadas);
		int exitosos = 0;
		for (Future<Boolean> resultado : resultados) {
			if (resultado.get()) {
				exitosos++;
			}
		}
		assertEquals(1, exitosos);
		} finally {
			pool.shutdownNow();
		}

		List<Pago> intentos = pagos.findByPedidoId(pedido.getId());
		assertEquals(1, intentos.size());
		assertEquals(new BigDecimal("100.00"), intentos.get(0).getMonto());
		assertEquals(Pago.PROCESANDO, intentos.get(0).getEstado());
	}

	@Test
	void mismoIdempotencyKeyDePagoDevuelveElIntentoExistente() {
		var primero = pagoService.crearIntento(usuario.getId(), pedido.getId(), "mock", "charge-retry-1");
		var repetido = pagoService.crearIntento(usuario.getId(), pedido.getId(), "mock", "charge-retry-1");

		assertEquals(primero.id(), repetido.id());
		assertEquals(1, pagos.findByPedidoId(pedido.getId()).size());
	}

	@Test
	void dosReembolsosParalelosNoReservanElMismoSaldo() throws Exception {
		Pago pago = nuevoPagoAprobado();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Callable<Boolean>> llamadas = List.of(
					() -> crearReembolso(pago.getId(), new BigDecimal("100.00"), "refund-race-a"),
					() -> crearReembolso(pago.getId(), new BigDecimal("100.00"), "refund-race-b"));
			List<Future<Boolean>> resultados = pool.invokeAll(llamadas);
		int exitosos = 0;
		for (Future<Boolean> resultado : resultados) {
			if (resultado.get()) {
				exitosos++;
			}
		}
		assertEquals(1, exitosos);
		} finally {
			pool.shutdownNow();
		}

		List<Reembolso> reservas = reembolsos.findByPagoId(pago.getId());
		assertEquals(1, reservas.size());
		assertEquals(Reembolso.PROCESANDO, reservas.get(0).getEstado());
		assertEquals(Pago.APROBADO, pagos.findById(pago.getId()).orElseThrow().getEstado());
		assertEquals(Pedido.PAGADO, pedidos.findById(pedido.getId()).orElseThrow().getEstado());
	}

	@Test
	void reembolsoIdempotenteReservaSaldoPendienteYNoFinalizaPagoAntesDelWebhook() {
		Pago pago = nuevoPagoAprobado();
		var primero = pagoService.reembolsar(tienda.getId(), pago.getId(),
				new BigDecimal("30.00"), "devolución parcial", "refund-retry-1");
		var repetido = pagoService.reembolsar(tienda.getId(), pago.getId(),
				new BigDecimal("30.00"), "devolución parcial", "refund-retry-1");

		assertEquals(primero.id(), repetido.id());
		assertEquals(1, reembolsos.findByPagoId(pago.getId()).size());
		assertEquals(Pago.APROBADO, pagos.findById(pago.getId()).orElseThrow().getEstado());
		assertEquals(Pedido.PAGADO, pedidos.findById(pedido.getId()).orElseThrow().getEstado());

		pagoService.reembolsar(tienda.getId(), pago.getId(),
				new BigDecimal("70.00"), "segunda parte", "refund-retry-2");
		assertEquals(2, reembolsos.findByPagoId(pago.getId()).size());
		assertThrows(ConflictoException.class, () -> pagoService.reembolsar(tienda.getId(), pago.getId(),
				new BigDecimal("0.01"), "sin saldo", "refund-retry-3"));
	}

	private boolean crearIntento(String idempotencia) {
		try {
			pagoService.crearIntento(usuario.getId(), pedido.getId(), "mock", idempotencia);
			return true;
		} catch (ConflictoException e) {
			return false;
		}
	}

	private boolean crearReembolso(Long pagoId, BigDecimal monto, String idempotencia) {
		try {
			pagoService.reembolsar(tienda.getId(), pagoId, monto, "carrera concurrente", idempotencia);
			return true;
		} catch (ConflictoException e) {
			return false;
		}
	}

	private Pago nuevoPagoAprobado() {
		pedido.setEstado(Pedido.PAGADO);
		pedidos.save(pedido);
		PasarelaPago pasarela = pasarelas.findByCodigo("mock").orElseThrow();
		Pago pago = new Pago();
		pago.setPedido(pedido);
		pago.setPasarela(pasarela);
		pago.setEstado(Pago.APROBADO);
		pago.setMonto(new BigDecimal("100.00"));
		pago.setMoneda("PEN");
		pago.setTransaccionExternaId("mock-paid-" + UUID.randomUUID());
		return pagos.save(pago);
	}
}
