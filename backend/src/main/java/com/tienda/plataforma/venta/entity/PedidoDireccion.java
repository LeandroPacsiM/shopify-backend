package com.tienda.plataforma.venta.entity;

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

/** Snapshot de dirección (una de envío + una de facturación). Tabla `pedido_direcciones` (V6). */
@Entity
@Table(name = "pedido_direcciones",
		uniqueConstraints = @UniqueConstraint(name = "uq_pedido_direccion_tipo",
				columnNames = { "pedido_id", "tipo" }))
@Getter
@Setter
@NoArgsConstructor
public class PedidoDireccion {

	public static final String ENVIO = "envio";
	public static final String FACTURACION = "facturacion";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pedido_id", nullable = false)
	private Pedido pedido;

	@Column(nullable = false, length = 20)
	private String tipo;

	@Column(length = 150)
	private String nombreDestinatario;

	@Column(length = 255)
	private String linea1;

	@Column(length = 255)
	private String linea2;

	@Column(length = 100)
	private String ciudad;

	@Column(length = 100)
	private String departamento;

	@Column(length = 20)
	private String codigoPostal;

	@Column(length = 100)
	private String pais;

	@Column(length = 30)
	private String telefono;
}
