package com.tienda.plataforma.shared.auditoria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.tienda.plataforma.auth.dto.AgregarMiembroRequest;
import com.tienda.plataforma.auth.entity.Usuario;import com.tienda.plataforma.auth.repository.RegistroAuditoriaRepository;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.auth.service.AuthService;
import com.tienda.plataforma.catalogo.dto.CrearProductoRequest;
import com.tienda.plataforma.catalogo.service.ProductoService;
import com.tienda.plataforma.pagos.dto.RegistrarPasarelaRequest;
import com.tienda.plataforma.pagos.service.TiendaPasarelaService;
import com.tienda.plataforma.shared.security.TenantContext;
import com.tienda.plataforma.venta.controller.AdminDescuentoController;
import com.tienda.plataforma.venta.dto.CrearDescuentoRequest;
import com.tienda.plataforma.pagos.entity.Pago;
import com.tienda.plataforma.pagos.entity.PasarelaPago;
import com.tienda.plataforma.pagos.entity.TiendaPasarela;
import com.tienda.plataforma.pagos.repository.PagoRepository;
import com.tienda.plataforma.pagos.repository.PasarelaPagoRepository;
import com.tienda.plataforma.pagos.repository.TiendaPasarelaRepository;
import com.tienda.plataforma.pagos.service.PagoService;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;
import com.tienda.plataforma.tenants.service.TiendaEquipoService;
import com.tienda.plataforma.venta.entity.Pedido;
import com.tienda.plataforma.venta.repository.PedidoRepository;
import com.tienda.plataforma.venta.service.AdminPedidoService;

/** RNF-01: acciones de tienda dejan fila de auditoría con actor en la misma TX. */
@SpringBootTest
@ActiveProfiles("test")
class AuditoriaTiendaTest {

	@Autowired
	AdminPedidoService adminPedidos;
	@Autowired
	PagoService pagoService;
	@Autowired
	AuthService authService;
	@Autowired
	TiendaEquipoService equipo;
	@Autowired
	ProductoService productoService;
	@Autowired
	AdminDescuentoController cupones;
	@Autowired
	TiendaPasarelaService pasarelaService;
	@Autowired
	RegistroAuditoriaRepository auditoria;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	ClienteRepository clientes;
	@Autowired
	TiendaUsuarioRepository vinculos;
	@Autowired
	PedidoRepository pedidos;
	@Autowired
	PagoRepository pagos;
	@Autowired
	PasarelaPagoRepository pasarelas;
	@Autowired
	TiendaPasarelaRepository tiendaPasarelas;

	Tienda tienda;
	Usuario admin;
	Usuario clienteUsuario;
	Pedido pendiente;
	Pedido pagado;
	Pago pago;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		tienda = new Tienda();
		tienda.setNombre("A-" + tag);
		tienda.setSlug("a-" + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);

		admin = nuevoUsuario("aa-" + tag);
		TiendaUsuario v = new TiendaUsuario();
		v.setTienda(tienda);
		v.setUsuario(admin);
		v.setRolTienda(TiendaUsuario.ADMIN);
		vinculos.save(v);

		clienteUsuario = nuevoUsuario("ac-" + tag);
		Cliente cliente = new Cliente();
		cliente.setUsuario(clienteUsuario);
		cliente.setTienda(tienda);
		cliente.setAceptaMarketing(false);
		clientes.save(cliente);

		pendiente = nuevoPedido("AU-" + tag + "-1", Pedido.PENDIENTE, cliente);
		pagado = nuevoPedido("AU-" + tag + "-2", Pedido.PAGADO, cliente);

		PasarelaPago mock = pasarelas.findByCodigo("mock").orElseGet(() -> {
			PasarelaPago p = new PasarelaPago();
			p.setNombre("Mock");
			p.setCodigo("mock");
			p.setActivo(true);
			return pasarelas.save(p);
		});
		TiendaPasarela cred = new TiendaPasarela();
		cred.setTienda(tienda);
		cred.setPasarela(mock);
		cred.setClavePublica("pk_test_mock");
		cred.setSecretoCifrado("secreto-mock-cifrado");
		cred.setModo(TiendaPasarela.TEST);
		cred.setActivo(true);
		tiendaPasarelas.save(cred);

		pago = new Pago();
		pago.setPedido(pagado);
		pago.setPasarela(mock);
		pago.setEstado(Pago.APROBADO);
		pago.setMonto(new BigDecimal("100.00"));
		pago.setMoneda("PEN");
		pago.setTransaccionExternaId("mock-audit-" + tag);
		pagos.save(pago);

		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(admin.getId(), null,
						List.of(new SimpleGrantedAuthority("ROLE_admin"))));
	}

	@AfterEach
	void limpiarContexto() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void cancelarPedidoQuedaAuditadoConActor() {
		adminPedidos.cancelar(tienda.getId(), pendiente.getId());

		var filas = auditoria.findByEntidadAndEntidadId("pedidos", pendiente.getId());
		assertEquals(1, filas.size());
		assertEquals("pedido.cancelar", filas.get(0).getAccion());
		assertEquals(admin.getId(), filas.get(0).getUsuario().getId());
	}

	@Test
	void reembolsoQuedaAuditado() {
		var resp = pagoService.reembolsar(tienda.getId(), pago.getId(),
				new BigDecimal("30.00"), "devolución", "audit-rf-1");

		var filas = auditoria.findByEntidadAndEntidadId("reembolsos", resp.id());
		assertTrue(filas.stream().anyMatch(f -> "reembolso.solicitar".equals(f.getAccion())));
		assertTrue(filas.stream().anyMatch(f -> "reembolso.proveedor".equals(f.getAccion())));
		assertTrue(filas.stream()
				.allMatch(f -> admin.getId().equals(f.getUsuario().getId())));
	}

	@Test
	void resetTotpYAgregarMiembroQuedanAuditados() {		authService.resetTotpPorAdmin(admin.getId(), clienteUsuario.getId(), tienda.getId());
		var filasReset = auditoria.findByEntidadAndEntidadId("usuarios", clienteUsuario.getId());
		assertEquals(1, filasReset.size());
		assertEquals("usuario.totp_reset", filasReset.get(0).getAccion());
		assertEquals(admin.getId(), filasReset.get(0).getUsuario().getId());

		Usuario invitado = nuevoUsuario("ai-" + UUID.randomUUID().toString().substring(0, 8));
		equipo.agregar(tienda.getId(),
				new AgregarMiembroRequest(invitado.getEmail(), "operador"));
		Long vinculoId = vinculos
				.findByTiendaIdAndUsuarioId(tienda.getId(), invitado.getId()).orElseThrow().getId();
		var filasEquipo = auditoria.findByEntidadAndEntidadId("tienda_usuarios", vinculoId);
		assertEquals(1, filasEquipo.size());
		assertEquals("tienda.agregar_miembro", filasEquipo.get(0).getAccion());
		assertEquals(admin.getId(), filasEquipo.get(0).getUsuario().getId());
	}

	@Test
	void catalogoCuponYPasarelaQuedanAuditados() {
		var producto = productoService.crear(tienda.getId(),
				new CrearProductoRequest("Audit " + UUID.randomUUID(), "aup-"
						+ UUID.randomUUID().toString().substring(0, 8), null));
		productoService.publicar(tienda.getId(), producto.id());
		var filasProducto = auditoria.findByEntidadAndEntidadId("productos", producto.id());
		assertTrue(filasProducto.stream().anyMatch(f -> "producto.crear".equals(f.getAccion())));
		assertTrue(filasProducto.stream().anyMatch(f -> "producto.publicar".equals(f.getAccion())));
		assertTrue(filasProducto.stream()
				.allMatch(f -> admin.getId().equals(f.getUsuario().getId())));

		TenantContext.fijar(tienda);
		try {
			var cupon = cupones.crear(new CrearDescuentoRequest("AUDIT-" + UUID.randomUUID()
					.toString().substring(0, 8), "porcentaje", new BigDecimal("10.00"),
					null, null, null, null));
			var filasCupon = auditoria.findByEntidadAndEntidadId("descuentos", cupon.getId());
			assertEquals(1, filasCupon.size());
			assertEquals("descuento.crear", filasCupon.get(0).getAccion());
		} finally {
			TenantContext.limpiar();
		}

		pasarelaService.registrar(admin.getId(), tienda.getId(), false,
				new RegistrarPasarelaRequest("mock", "pk_test_audit", "secreto-audit", "test"));
		assertTrue(auditoria.findAll().stream().anyMatch(f -> "tienda_pasarelas".equals(f.getEntidad())
				&& "pasarela.registrar".equals(f.getAccion())
				&& admin.getId().equals(f.getUsuario().getId())));
	}

	private Pedido nuevoPedido(String numero, String estado, Cliente cliente) {
		Pedido p = new Pedido();
		p.setTienda(tienda);
		p.setCliente(cliente);
		p.setNumeroPedido(numero);
		p.setEstado(estado);
		p.setMoneda("PEN");
		p.setSubtotal(new BigDecimal("100.00"));
		p.setImpuestoTotal(BigDecimal.ZERO);
		p.setEnvioTotal(BigDecimal.ZERO);
		p.setDescuentoTotal(BigDecimal.ZERO);
		p.setTotal(new BigDecimal("100.00"));
		return pedidos.save(p);
	}

	private Usuario nuevoUsuario(String tag) {
		Usuario u = new Usuario();
		u.setEmail(tag + "@example.com");
		u.setPasswordHash("x");
		u.setActivo(true);
		u.setEmailVerificado(true);
		u.setIntentosFallidos(0);
		return usuarios.save(u);
	}
}
