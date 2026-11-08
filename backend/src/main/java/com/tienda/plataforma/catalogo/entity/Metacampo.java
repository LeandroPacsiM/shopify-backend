package com.tienda.plataforma.catalogo.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Atributo extensible por clave (referencia lógica, sin FK).
 * Tabla `metacampos`; la existencia del destino se valida en backend.
 */
@Entity
@Table(name = "metacampos",
		uniqueConstraints = @UniqueConstraint(name = "uq_metacampo",
				columnNames = { "entidad", "entidad_id", "clave" }))
@Getter
@Setter
@NoArgsConstructor
public class Metacampo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String entidad;

	@Column(nullable = false)
	private Long entidadId;

	@Column(nullable = false, length = 100)
	private String clave;

	@Column(columnDefinition = "TEXT")
	private String valor;

	@Column(nullable = false, length = 30)
	private String tipo = "texto";

@CreationTimestamp
	private Instant creadoEn;
}

