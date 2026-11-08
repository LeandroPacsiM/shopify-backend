package com.tienda.plataforma.platform.facturacion.entity;

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
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** Evento de facturación procesado (dedup por referencia). Tabla `suscripcion_eventos` (V18). */
@Entity
@Table(name = "suscripcion_eventos")
@Getter
@Setter
@NoArgsConstructor
public class SuscripcionEvento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "suscripcion_id", nullable = false)
	private Suscripcion suscripcion;

	@Column(nullable = false, unique = true, length = 255)
	private String referenciaExterna;

	@Column(nullable = false, length = 100)
	private String tipo;

	@CreationTimestamp
	private Instant creadoEn;
}
