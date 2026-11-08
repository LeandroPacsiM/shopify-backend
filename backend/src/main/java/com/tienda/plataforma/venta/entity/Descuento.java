package com.tienda.plataforma.venta.entity;

import java.math.BigDecimal;
import java.time.Instant;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Regla de descuento por tienda. Tabla `descuentos` + `tienda_id` (V11). */
@Entity
@Table(name = "descuentos")
@Getter
@Setter
@NoArgsConstructor
public class Descuento {

	public static final String PORCENTAJE = "porcentaje";
	public static final String MONTO_FIJO = "monto_fijo";
	public static final String ENVIO_GRATIS = "envio_gratis";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tienda_id", nullable = false)
	private Tienda tienda;

	@Column(nullable = false, unique = true, length = 50)
	private String codigo;

	@Column(nullable = false, length = 30)
	private String tipo;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal valor;

	@Column(length = 255)
	private String descripcion;

	private Integer usoMaximo;

	@Column(nullable = false)
	private Integer usosActuales = 0;

	private Instant iniciaEn;

	private Instant terminaEn;

	@Column(nullable = false)
	private Boolean activo = true;

@CreationTimestamp
	private Instant creadoEn;

	public boolean vigente(Instant ahora) {
		if (!Boolean.TRUE.equals(activo)) {
			return false;
		}
		if (iniciaEn != null && ahora.isBefore(iniciaEn)) {
			return false;
		}
		if (terminaEn != null && ahora.isAfter(terminaEn)) {
			return false;
		}
		return usoMaximo == null || usosActuales < usoMaximo;
	}
}

