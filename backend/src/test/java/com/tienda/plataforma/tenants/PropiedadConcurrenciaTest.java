package com.tienda.plataforma.tenants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;
import com.tienda.plataforma.tenants.service.TiendaPropiedadService;

/**
 * Concurrencia real sobre la propiedad (condición 2 de ejecución).
 * Sin `@Transactional`: los hilos necesitan fixtures commiteados
 * (cada hilo abre su propia transacción). Fixtures persistentes
 * permitidos en `shopify_db_test`.
 */
@SpringBootTest
@ActiveProfiles("test")
class PropiedadConcurrenciaTest {

	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	TiendaUsuarioRepository vinculos;
	@Autowired
	TiendaPropiedadService propiedad;
	@Autowired
	PlatformTransactionManager txManager;

	Tienda tienda;
	Usuario owner;
	Usuario admin;
	Usuario operador;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		// V22: todo en una sola TX (tienda + owners; el trigger dispara al commit).
		new TransactionTemplate(txManager).executeWithoutResult(s -> {
			tienda = new Tienda();
			tienda.setNombre("OWC-" + tag);
			tienda.setSlug("owc-" + tag);
			tienda.setActiva(true);
			tiendas.save(tienda);
			owner = vincular("owco-" + tag, TiendaUsuario.STORE_OWNER);
			admin = vincular("owca-" + tag, TiendaUsuario.ADMIN);
			operador = vincular("owcb-" + tag, TiendaUsuario.OPERADOR);
		});
	}

	@Test
	void transferenciasParalelasDejanUnSoloOwner() throws Exception {
		CountDownLatch listos = new CountDownLatch(2);
		CountDownLatch largada = new CountDownLatch(1);
		Callable<String> a = () -> {
			listos.countDown();
			largada.await(10, TimeUnit.SECONDS);
			try {
				propiedad.transferir(tienda.getId(), owner.getId(), admin.getId());
				return "ok";
			} catch (RuntimeException e) {
				return e.getClass().getSimpleName();
			}
		};
		Callable<String> b = () -> {
			listos.countDown();
			largada.await(10, TimeUnit.SECONDS);
			try {
				propiedad.transferir(tienda.getId(), owner.getId(), operador.getId());
				return "ok";
			} catch (RuntimeException e) {
				return e.getClass().getSimpleName();
			}
		};
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<String> fa = pool.submit(a);
			Future<String> fb = pool.submit(b);
			assertTrue(listos.await(10, TimeUnit.SECONDS));
			largada.countDown();
			String ra = fa.get(30, TimeUnit.SECONDS);
			String rb = fb.get(30, TimeUnit.SECONDS);
			// Una gana, la otra falla (el owner ya cambió): nunca 0 ni 2.
			assertTrue(
					("ok".equals(ra) && !"ok".equals(rb))
							|| (!"ok".equals(ra) && "ok".equals(rb)),
					"resultados: " + ra + "/" + rb);
		} finally {
			pool.shutdownNow();
		}
		long owners = vinculos.findByTiendaId(tienda.getId()).stream()
				.filter(v -> TiendaUsuario.STORE_OWNER.equals(v.getRolTienda()))
				.count();
		assertEquals(1, owners);
	}

	private Usuario vincular(String tag, String rol) {
		Usuario u = new Usuario();
		u.setEmail(tag + "@example.com");
		u.setPasswordHash("x");
		u.setActivo(true);
		u.setEmailVerificado(true);
		u.setIntentosFallidos(0);
		usuarios.save(u);
		TiendaUsuario v = new TiendaUsuario();
		v.setTienda(tienda);
		v.setUsuario(u);
		v.setRolTienda(rol);
		vinculos.save(v);
		return u;
	}
}
