package com.tienda.plataforma.envios.entity;

import com.tienda.plataforma.venta.entity.PedidoItem;
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

/** Cantidad de una línea en un envío. Tabla `envio_items` (V6). */
@Entity
@Table(name = "envio_items",
		uniqueConstraints = @UniqueConstraint(name = "uq_envio_pedido_item",
				columnNames = { "envio_id", "pedido_item_id" }))
@Getter
@Setter
@NoArgsConstructor
public class EnvioItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "envio_id", nullable = false)
	private Envio envio;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pedido_item_id", nullable = false)
	private PedidoItem pedidoItem;

	@Column(nullable = false)
	private Integer cantidad;
}
