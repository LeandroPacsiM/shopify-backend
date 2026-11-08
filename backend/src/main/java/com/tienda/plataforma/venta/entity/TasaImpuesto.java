package com.tienda.plataforma.venta.entity;

import java.math.BigDecimal;

import com.tienda.plataforma.tenants.entity.Tienda;
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

/**
 * Tasa viva por tienda, solo para cotizar. El histórico va en
 * `pedido_impuestos` (V9). Tabla `tasas_impuesto` + `tienda_id` (V11).
 */
@Entity
@Table(name = "tasas_impuesto")
@Getter
@Setter
@NoArgsConstructor
public class TasaImpuesto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tienda_id", nullable = false)
	private Tienda tienda;

	@Column(nullable = false, length = 100)
	private String nombre;

	@Column(length = 100)
	private String pais;

	@Column(length = 100)
	private String departamento;

	@Column(nullable = false, precision = 5, scale = 2)
	private BigDecimal porcentaje;

	@Column(nullable = false)
	private Boolean activo = true;
}
