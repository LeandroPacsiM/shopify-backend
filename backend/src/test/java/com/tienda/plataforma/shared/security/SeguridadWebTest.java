package com.tienda.plataforma.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** RNF-SEG-02: no-store/noindex en checkout+admin; CORS solo al SPA. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeguridadWebTest {

	@Autowired
	MockMvc mvc;

	@Test
	void adminNoCacheaNiIndexa() throws Exception {
		mvc.perform(get("/api/admin/pedidos"))
				.andExpect(status().isForbidden())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
	}

	@Test
	void checkoutNoCachea() throws Exception {
		mvc.perform(post("/api/checkout/orden")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(header().string("Cache-Control", "no-store"));
	}

	@Test
	void authSinEsasCabeceras() throws Exception {
		mvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"x@y.zz\",\"password\":\"Secreta123\"}"))
				.andExpect(header().doesNotExist("X-Robots-Tag"));
	}

	@Test
	void corsSoloAlSpa() throws Exception {
		mvc.perform(options("/api/admin/pedidos")
				.header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

		mvc.perform(options("/api/admin/pedidos")
				.header("Origin", "https://evil.example.com")
				.header("Access-Control-Request-Method", "GET"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@Test
	void authComparteCorsDelSpa() throws Exception {
		mvc.perform(options("/auth/login")
				.header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "Content-Type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

		mvc.perform(options("/auth/refresh")
				.header("Origin", "https://evil.example.com")
				.header("Access-Control-Request-Method", "POST"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@Test
	void preflightPermiteCarritoYTienda() throws Exception {
		mvc.perform(options("/api/storefront/demo/carrito")
				.header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "X-Carrito-Token, X-Tienda-Slug, Content-Type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
	}

	@Test
	void logoutExpiraLaCookie() throws Exception {
		mvc.perform(post("/auth/logout"))
				.andExpect(status().isNoContent())
				.andExpect(header().exists("Set-Cookie"))
				.andExpect(header().string("Set-Cookie",
						org.hamcrest.Matchers.containsString("Max-Age=0")));
	}
}
