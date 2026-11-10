package com.tienda.plataforma.shared.auditoria;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * F4b (condición 3 de ejecución): `_respaldo_cliente_usuario` no tiene
 * entidad JPA ni repositorio; la app jamás la lee ni la escribe.
 * Solo existe para trazabilidad (retención 90 días, borrado en V24 futura).
 */
class RespaldoAusenteTest {

	@Test
	void ningunaEntidadNiRepositorioTocaElRespaldo() throws Exception {
		Path raiz = Paths.get("src/main/java/com/tienda/plataforma");
		try (Stream<Path> archivos = Files.walk(raiz)) {
			var ofensores = archivos
					.filter(p -> p.toString().endsWith(".java"))
					.filter(p -> {
						try {
							return Files.readString(p).contains("_respaldo_cliente_usuario");
						} catch (Exception e) {
							return false;
						}
					})
					.map(p -> p.getFileName().toString())
					.toList();
			assertTrue(ofensores.isEmpty(), "respaldo referenciado en: " + ofensores);
		}
	}
}
