package com.tienda.plataforma.envios.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Proveedor logístico (catálogo global). Tabla `transportistas`. */
@Entity
@Table(name = "transportistas")
@Getter
@Setter
@NoArgsConstructor
public class Transportista {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 100)
	private String nombre;

	@Column(nullable = false, unique = true, length = 50)
	private String codigo;

	@Column(length = 500)
	private String urlTracking;

	@Column(nullable = false)
	private Boolean activo = true;
}
