package com.tienda.plataforma.pagos;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.pagos.repository.TiendaPasarelaRepository;
import com.tienda.plataforma.shared.security.CryptoService;
import com.tienda.plataforma.shared.security.JwtService;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;

/** RF-PAY-01: solo el admin registra credenciales; el secreto va cifrado y no vuelve. Rollback al final. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PasarelaCredencialTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	JwtService jwtService;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	TiendaUsuarioRepository tiendaUsuarios;
	@Autowired
	TiendaPasarelaRepository credenciales;
	@Autowired
	CryptoService crypto;

	Tienda tienda;
	String jwtAdmin;
	String jwtOperador;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		tienda = new Tienda();
		tienda.setNombre("PC-" + tag);
		tienda.setSlug("pc-" + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);
		jwtAdmin = jwtVinculado("pca-" + tag, TiendaUsuario.ADMIN);
		jwtOperador = jwtVinculado("pco-" + tag, TiendaUsuario.OPERADOR);
	}

	@Test
	void adminRegistraYSecretoCifrado() throws Exception {
		String cuerpo = "{\"codigo\":\"stripe\",\"clavePublica\":\"pk_test_x\","
				+ "\"secreto\":\"sk_test_x;whsec_y\",\"modo\":\"test\"}";
		mvc.perform(post("/api/admin/pasarelas")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.codigo").value("stripe"))
				.andExpect(jsonPath("$.modo").value("test"))
				.andExpect(jsonPath("$.secreto").doesNotExist())
				.andExpect(jsonPath("$.secretoCifrado").doesNotExist());

		var guardada = credenciales.findByTiendaIdAndActivoTrue(tienda.getId());
		assertEquals(1, guardada.size());
		assertNotEquals("sk_test_x;whsec_y", guardada.get(0).getSecretoCifrado());
		assertEquals("sk_test_x;whsec_y",
				crypto.descifrar(guardada.get(0).getSecretoCifrado()));

		mvc.perform(get("/api/admin/pasarelas")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		// Rotar: re-registrar pisa la anterior sin duplicar.
		mvc.perform(post("/api/admin/pasarelas")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo.replace("sk_test_x", "sk_test_rotada")))
				.andExpect(status().isOk());
		assertEquals(1, credenciales.findByTiendaIdAndActivoTrue(tienda.getId()).size());
		assertEquals("sk_test_rotada;whsec_y", crypto.descifrar(
				credenciales.findByTiendaIdAndActivoTrue(tienda.getId()).get(0).getSecretoCifrado()));
	}

	@Test
	void aceptaLlaveRestringidaDePrueba() throws Exception {
		mvc.perform(post("/api/admin/pasarelas")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigo\":\"stripe\",\"clavePublica\":\"pk_test_x\","
						+ "\"secreto\":\"rk_test_x\",\"modo\":\"test\"}"))
				.andExpect(status().isOk());
	}

	@Test
	void operadorNoRegistra() throws Exception {
		mvc.perform(post("/api/admin/pasarelas")
				.header("Authorization", "Bearer " + jwtOperador)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigo\":\"stripe\",\"clavePublica\":\"pk\",\"secreto\":\"sk\",\"modo\":\"test\"}"))
				.andExpect(status().isForbidden());
		assertTrue(credenciales.findByTiendaIdAndActivoTrue(tienda.getId()).isEmpty());
	}

	@Test
	void modoInvalidoSeRechaza() throws Exception {
		mvc.perform(post("/api/admin/pasarelas")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigo\":\"stripe\",\"clavePublica\":\"pk\",\"secreto\":\"sk\",\"modo\":\"oro\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void llavesStripeLiveSeRechazanAunqueModoDigaTest() throws Exception {
		for (String cuerpo : List.of(
				"{\"codigo\":\"stripe\",\"clavePublica\":\"pk_test_x\","
						+ "\"secreto\":\"sk_live_x;whsec_y\",\"modo\":\"test\"}",
				"{\"codigo\":\"stripe\",\"clavePublica\":\"pk_live_x\","
						+ "\"secreto\":\"sk_test_x;whsec_y\",\"modo\":\"test\"}")) {
			mvc.perform(post("/api/admin/pasarelas")
					.header("Authorization", "Bearer " + jwtAdmin)
					.header("X-Tienda-Slug", tienda.getSlug())
					.contentType(MediaType.APPLICATION_JSON)
					.content(cuerpo))
					.andExpect(status().isConflict());
		}
		assertTrue(credenciales.findByTiendaIdAndActivoTrue(tienda.getId()).isEmpty());
	}

	@Test
	void modoVivoSeRechazaInclusoConLlavesDePrueba() throws Exception {
		mvc.perform(post("/api/admin/pasarelas")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigo\":\"stripe\",\"clavePublica\":\"pk_test_x\","
						+ "\"secreto\":\"sk_test_x;whsec_y\",\"modo\":\"vivo\"}"))
				.andExpect(status().isConflict());
		assertTrue(credenciales.findByTiendaIdAndActivoTrue(tienda.getId()).isEmpty());
	}

	private String jwtVinculado(String tag, String rolTienda) {
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
		v.setRolTienda(rolTienda);
		tiendaUsuarios.save(v);
		return jwtService.acceso(u.getId(), List.of("admin"));
	}
}
