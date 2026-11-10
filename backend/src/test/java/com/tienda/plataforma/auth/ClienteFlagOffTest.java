package com.tienda.plataforma.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Flag off explícito: el ámbito CUSTOMER es inerte (404). */
@SpringBootTest(properties = "auth.customer.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClienteFlagOffTest {

	@Autowired
	MockMvc mvc;

	@Test
	void endpointsCustomerInertesConFlagOff() throws Exception {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		mvc.perform(post("/auth/cliente/solicitar")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"tiendaSlug\":\"x\",\"email\":\"a-" + tag + "@example.com\"}"))
				.andExpect(status().isNotFound());
		mvc.perform(post("/auth/cliente/verificar")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"tiendaSlug\":\"x\",\"email\":\"a-" + tag
						+ "@example.com\",\"codigo\":\"123456\"}"))
				.andExpect(status().isNotFound());
	}
}
