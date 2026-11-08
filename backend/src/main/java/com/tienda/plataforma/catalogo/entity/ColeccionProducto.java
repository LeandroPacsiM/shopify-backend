package com.tienda.plataforma.catalogo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Producto dentro de una colección, con orden. Tabla `coleccion_productos`. */
@Entity
@Table(name = "coleccion_productos",
		uniqueConstraints = @UniqueConstraint(name = "uq_coleccion_producto",
				columnNames = { "coleccion_id", "producto_id" }))
@Getter
@Setter
@NoArgsConstructor
public class ColeccionProducto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "coleccion_id", nullable = false)
	private Coleccion coleccion;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "producto_id", nullable = false)
	private Producto producto;

	@Column(nullable = false)
	private Integer posicion = 0;
}
