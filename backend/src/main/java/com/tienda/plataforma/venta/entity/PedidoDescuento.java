package com.tienda.plataforma.venta.entity;

import java.math.BigDecimal;

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

/** Descuento aplicado a un pedido. Tabla `pedido_descuentos` (V6). */
@Entity
@Table(name = "pedido_descuentos",
		uniqueConstraints = @UniqueConstraint(name = "uq_pedido_descuento",
				columnNames = { "pedido_id", "descuento_id" }))
@Getter
@Setter
@NoArgsConstructor
public class PedidoDescuento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pedido_id", nullable = false)
	private Pedido pedido;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "descuento_id", nullable = false)
	private Descuento descuento;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal montoAplicado;
}
