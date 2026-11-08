package com.tienda.plataforma.pagos.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Evento de pago/webhook con payload. Tabla `pago_eventos` + dedup (V3). */
@Entity
@Table(name = "pago_eventos",
		uniqueConstraints = @UniqueConstraint(name = "uq_pago_evento_referencia",
				columnNames = { "pago_id", "referencia_externa" }))
@Getter
@Setter
@NoArgsConstructor
public class PagoEvento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pago_id", nullable = false)
	private Pago pago;

	@Column(nullable = false, length = 50)
	private String tipoEvento;

	@Column(length = 30)
	private String estadoAnterior;

	@Column(length = 30)
	private String estadoNuevo;

	/** JSONB: se lee minimizado, jamás con secretos. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private String payload;

	/** Debe ser el event-id del proveedor; deduplica webhooks (V3). */
	@Column(length = 255)
	private String referenciaExterna;

@CreationTimestamp
	private Instant creadoEn;
}

