package com.tienda.plataforma.venta.entity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Snapshot de impuesto por pedido/línea. Tabla `pedido_impuestos` (V9). */
@Entity
@Table(name = "pedido_impuestos",
		uniqueConstraints = @UniqueConstraint(name = "uq_pedido_impuesto",
				columnNames = { "pedido_id", "pedido_item_id", "titulo" }))
@Getter
@Setter
@NoArgsConstructor
public class PedidoImpuesto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pedido_id", nullable = false)
	private Pedido pedido;

	/** NULL = impuesto a nivel de envío/cabecera. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pedido_item_id")
	private PedidoItem pedidoItem;

	@Column(nullable = false, length = 100)
	private String titulo;

	@Column(nullable = false, precision = 7, scale = 4)
	private BigDecimal tasa;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal monto;

@CreationTimestamp
	private Instant creadoEn;
}

