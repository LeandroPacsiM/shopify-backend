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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Línea con snapshot histórico. Tabla `pedido_items`. */
@Entity
@Table(name = "pedido_items")
@Getter
@Setter
@NoArgsConstructor
public class PedidoItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pedido_id", nullable = false)
	private Pedido pedido;

	/** NULL si la variante se eliminó; el snapshot sobrevive. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "variante_id")
	private ProductoVariante variante;

	@Column(nullable = false, length = 255)
	private String productoTitulo;

	@Column(length = 255)
	private String varianteTitulo;

	@Column(length = 100)
	private String sku;

	@Column(nullable = false)
	private Integer cantidad;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal precioUnitario;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal precioTotal;
}
