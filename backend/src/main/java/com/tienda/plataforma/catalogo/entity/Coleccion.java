package com.tienda.plataforma.catalogo.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Agrupación editorial de productos. Tabla `colecciones`. */
@Entity
@Table(name = "colecciones")
@Getter
@Setter
@NoArgsConstructor
public class Coleccion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 255)
	private String titulo;

	@Column(nullable = false, unique = true, length = 255)
	private String slug;

	@Column(columnDefinition = "TEXT")
	private String descripcion;

	@Column(nullable = false)
	private Boolean activo = true;

@CreationTimestamp
	private Instant creadoEn;
}

