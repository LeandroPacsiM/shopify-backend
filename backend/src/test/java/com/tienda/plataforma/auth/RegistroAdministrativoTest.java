package com.tienda.plataforma.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.repository.UsuarioRolRepository;

/** RF-AUTH-09 (D1): identidad staff sin permisos, sin CUSTOMER, sin rol. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegistroAdministrativoTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	UsuarioRolRepository usuarioRoles;
	@Autowired
	com.tienda.plataforma.auth.repository.UsuarioRepository usuarios;

	@Test
	void creaIdentidadSinPermisosNiCustomer() throws Exception {
		String email = "staff-" + UUID.randomUUID() + "@example.com";
		mvc.perform(post("/auth/registro-administrativo")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email
						+ "\",\"password\":\"Secreto123\",\"nombre\":\"Staff\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(email));

		Long usuarioId = usuarios.findByEmail(email).orElseThrow().getId();
		assertEquals(List.of(), usuarioRoles.nombresDeRoles(usuarioId));

		// La identidad sirve para enrolar TOTP (flujo staff intacto).
		mvc.perform(post("/auth/totp/iniciar")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"Secreto123\"}"))
				.andExpect(status().isOk());

		mvc.perform(post("/auth/registro-administrativo")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"Secreto123\"}"))
				.andExpect(status().isConflict());
	}
}
