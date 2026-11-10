package com.tienda.plataforma.pagos;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.pagos.entity.Pago;
import com.tienda.plataforma.pagos.entity.PasarelaPago;
import com.tienda.plataforma.pagos.entity.TiendaPasarela;
import com.tienda.plataforma.pagos.repository.PagoEventoRepository;
import com.tienda.plataforma.pagos.repository.PagoRepository;
import com.tienda.plataforma.pagos.repository.PasarelaPagoRepository;
import com.tienda.plataforma.pagos.repository.TiendaPasarelaRepository;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;
import com.tienda.plataforma.venta.entity.Pedido;
import com.tienda.plataforma.venta.repository.PedidoRepository;

/** Webhook duplicado aplica una sola transición (suite AGENTS.md). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookDuplicadoTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	ClienteRepository clientes;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	TiendaUsuarioRepository vinculos;
	@Autowired
	PedidoRepository pedidos;
	@Autowired
	PasarelaPagoRepository pasarelas;
	@Autowired
	TiendaPasarelaRepository tiendaPasarelas;
	@Autowired
	PagoRepository pagos;
	@Autowired
	PagoEventoRepository eventos;
	@Autowired
	PlatformTransactionManager txManager;

	Long pagoId;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		Tienda tienda = new Tienda();
		tienda.setNombre("W-" + tag);
		tienda.setSlug("w-" + tag);
		tienda.setActiva(true);

		Usuario usuario = new Usuario();
		usuario.setEmail("w-" + tag + "@example.com");
		usuario.setPasswordHash("x");
		usuario.setActivo(true);
		usuario.setEmailVerificado(true);
		usuario.setTotpHabilitado(false);
		usuario.setIntentosFallidos(0);

		// V22: tienda + owner en una sola TX (el trigger dispara al commit).
		Usuario dueno = new Usuario();
		dueno.setEmail("wdo-" + tag + "@example.com");
		dueno.setPasswordHash("x");
		dueno.setActivo(true);
		dueno.setEmailVerificado(true);
		dueno.setTotpHabilitado(false);
		dueno.setIntentosFallidos(0);
		TiendaUsuario v = new TiendaUsuario();
		v.setTienda(tienda);
		v.setUsuario(dueno);
		v.setRolTienda(TiendaUsuario.STORE_OWNER);
		new TransactionTemplate(txManager).executeWithoutResult(s -> {
			tiendas.save(tienda);
			usuarios.save(usuario);
			usuarios.save(dueno);
			vinculos.save(v);
		});

		Cliente cliente = new Cliente();
		cliente.setUsuario(usuario);
		cliente.setTienda(tienda);
		cliente.setAceptaMarketing(false);
		cliente.setEmail(cliente.getUsuario().getEmail());
		clientes.save(cliente);

		Pedido pedido = new Pedido();
		pedido.setTienda(tienda);
		pedido.setCliente(cliente);
		pedido.setNumeroPedido("W-" + tag);
		pedido.setEstado(Pedido.PENDIENTE);
		pedido.setMoneda("PEN");
		pedido.setSubtotal(new BigDecimal("100.00"));
		pedido.setImpuestoTotal(BigDecimal.ZERO);
		pedido.setEnvioTotal(BigDecimal.ZERO);
		pedido.setDescuentoTotal(BigDecimal.ZERO);
		pedido.setTotal(new BigDecimal("100.00"));
		pedidos.save(pedido);

		PasarelaPago pasarela = pasarelas.findByCodigo("mock").orElseGet(() -> {
			PasarelaPago nueva = new PasarelaPago();
			nueva.setNombre("Mock");
			nueva.setCodigo("mock");
			nueva.setActivo(true);
			return pasarelas.save(nueva);
		});

		TiendaPasarela creds = new TiendaPasarela();
		creds.setTienda(tienda);
		creds.setPasarela(pasarela);
		creds.setClavePublica("pk");
		creds.setSecretoCifrado("x");
		creds.setModo(TiendaPasarela.TEST);
		creds.setActivo(true);
		tiendaPasarelas.save(creds);

		Pago pago = new Pago();
		pago.setPedido(pedido);
		pago.setPasarela(pasarela);
		pago.setEstado(Pago.PROCESANDO);
		pago.setMonto(new BigDecimal("100.00"));
		pago.setMoneda("PEN");
		pago.setTransaccionExternaId("tx-" + tag);
		pagos.save(pago);
		pagoId = pago.getId();
	}

	@Test
	void webhookDuplicadoUnaSolaTransicion() throws Exception {
		PasarelaPago pasarela = pasarelas.findByCodigo("mock").orElseThrow();
		String cuerpo = "{\"eventId\":\"evt-dup-1\",\"transaccionId\":\""
				+ pagos.findById(pagoId).orElseThrow().getTransaccionExternaId()
				+ "\",\"estado\":\"aprobado\"}";
		for (int i = 0; i < 2; i++) {
			mvc.perform(post("/api/webhooks/pagos/{codigo}", pasarela.getCodigo())
					.contentType(MediaType.APPLICATION_JSON)
					.content(cuerpo))
					.andExpect(status().isOk());
		}
		Pago pago = pagos.findById(pagoId).orElseThrow();
		assertEquals(Pago.APROBADO, pago.getEstado());
		assertEquals(1, eventos.findByPagoId(pagoId).stream()
				.filter(e -> "evt-dup-1".equals(e.getReferenciaExterna())).count());
		assertEquals(Pedido.PAGADO,
				pedidos.findById(pago.getPedido().getId()).orElseThrow().getEstado());
	}
}
