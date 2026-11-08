package com.tienda.plataforma.venta.entity;

import java.math.BigDecimal;

import com.tienda.plataforma.catalogo.entity.ProductoVariante;
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

/** Línea del carrito: una variante, una vez. Tabla `carrito_items`. */
@Entity
@Table(name = "carrito_items",
		uniqueConstraints = @UniqueConstraint(name = "uq_carrito_variante",
				columnNames = { "carrito_id", "variante_id" }))
@Getter
@Setter
@NoArgsConstructor
public class CarritoItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "carrito_id", nullable = false)
	private Carrito carrito;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "variante_id", nullable = false)
	private ProductoVariante variante;

	@Column(nullable = false)
	private Integer cantidad;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal precioUnitario;
}
