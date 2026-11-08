package com.tienda.plataforma.pagos.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.tienda.plataforma.venta.entity.Pedido;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Devolución contra un pago aprobado. Tabla `reembolsos` + idempotencia (V3). */
@Entity
@Table(name = "reembolsos",
		uniqueConstraints = @UniqueConstraint(name = "uq_reembolso_pago_transaccion",
				columnNames = { "pago_id", "transaccion_externa_id" }))
@Getter
@Setter
@NoArgsConstructor
public class Reembolso {

	public static final String PENDIENTE = "pendiente";
	public static final String PROCESANDO = "procesando";
	public static final String APROBADO = "aprobado";
	public static final String RECHAZADO = "rechazado";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pedido_id", nullable = false)
	private Pedido pedido;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pago_id", nullable = false)
	private Pago pago;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal monto;

	@Column(nullable = false, length = 3) @JdbcTypeCode(SqlTypes.CHAR)
	private String moneda = "PEN";

	@Column(length = 255)
	private String motivo;

	@Column(nullable = false, length = 30)
	private String estado = PENDIENTE;

	@Column(length = 255)
	private String transaccionExternaId;

@CreationTimestamp
	private Instant creadoEn;
}

