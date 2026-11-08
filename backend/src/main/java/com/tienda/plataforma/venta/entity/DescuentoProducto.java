package com.tienda.plataforma.venta.entity;

import com.tienda.plataforma.catalogo.entity.Coleccion;
import com.tienda.plataforma.catalogo.entity.Producto;
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

/** Alcance de descuento a productos. Tabla `descuento_productos`. */
@Entity
@Table(name = "descuento_productos",
		uniqueConstraints = @UniqueConstraint(name = "uq_descuento_producto",
				columnNames = { "descuento_id", "producto_id" }))
@Getter
@Setter
@NoArgsConstructor
public class DescuentoProducto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "descuento_id", nullable = false)
	private Descuento descuento;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "producto_id", nullable = false)
	private Producto producto;
}
