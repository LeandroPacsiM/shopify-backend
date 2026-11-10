package com.tienda.plataforma.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.TiendaRepository;

/** Límite por cuenta: 5/h (reenvío desactivado por props en esta clase). */
@SpringBootTest(properties = { "auth.customer.enabled=true",
		"cliente.codigo.reenvio-segundos=0", "cliente.codigo.max-hora-cuenta=5",
		"cliente.codigo.max-hora-ip=1000" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClienteRateLimitTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	TiendaRepository tiendas;

	String slug;
	String email;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		slug = "rl-" + tag;
		email = "ratelimit-" + tag + "@example.com";
		Tienda tienda = new Tienda();
		tienda.setNombre("RL " + tag);
		tienda.setSlug(slug);
		tienda.setActiva(true);
		tiendas.save(tienda);
	}

	@Test
	void sextaSolicitudEnLaHoraSeRechaza() throws Exception {
		String cuerpo = "{\"tiendaSlug\":\"" + slug + "\",\"email\":\"" + email + "\"}";
		for (int i = 0; i < 5; i++) {
			mvc.perform(post("/auth/cliente/solicitar")
					.contentType(MediaType.APPLICATION_JSON)
					.content(cuerpo))
					.andExpect(status().isNoContent());
		}
		mvc.perform(post("/auth/cliente/solicitar")
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo))
				.andExpect(status().isTooManyRequests());
	}
}
