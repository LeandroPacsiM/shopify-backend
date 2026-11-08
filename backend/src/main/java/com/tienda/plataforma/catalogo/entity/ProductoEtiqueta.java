package com.tienda.plataforma.catalogo.entity;

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

/** Etiqueta de un producto. Tabla `producto_etiquetas`. */
@Entity
@Table(name = "producto_etiquetas",
		uniqueConstraints = @UniqueConstraint(name = "uq_producto_etiqueta",
				columnNames = { "producto_id", "etiqueta_id" }))
@Getter
@Setter
@NoArgsConstructor
public class ProductoEtiqueta {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "producto_id", nullable = false)
	private Producto producto;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "etiqueta_id", nullable = false)
	private Etiqueta etiqueta;
}
