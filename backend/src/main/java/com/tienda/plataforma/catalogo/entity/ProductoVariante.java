package com.tienda.plataforma.catalogo.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Versión comprable de un producto. Tabla `producto_variantes`. */
@Entity
@Table(name = "producto_variantes")
@Getter
@Setter
@NoArgsConstructor
public class ProductoVariante {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "producto_id", nullable = false)
	private Producto producto;

	/** Único cuando no es nulo. */
	@Column(unique = true, length = 100)
	private String sku;

	@Column(length = 100)
	private String codigoBarras;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal precio;

	@Column(precision = 12, scale = 2)
	private BigDecimal precioComparativo;

	private Integer pesoGramos;

	@Column(nullable = false)
	private Boolean activo = true;

@CreationTimestamp
	private Instant creadoEn;

	public boolean comprable() {
		return Boolean.TRUE.equals(activo) && producto != null && producto.comprable();
	}
}

