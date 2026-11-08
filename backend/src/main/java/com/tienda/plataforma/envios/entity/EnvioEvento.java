package com.tienda.plataforma.envios.entity;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Suceso de tracking del transportista. Tabla `envio_eventos`. */
@Entity
@Table(name = "envio_eventos")
@Getter
@Setter
@NoArgsConstructor
public class EnvioEvento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "envio_id", nullable = false)
	private Envio envio;

	/** Texto externo original; el mapeo a estado local lo hace el backend. */
	@Column(nullable = false, length = 50)
	private String estado;

	@Column(length = 255)
	private String descripcion;

	@Column(length = 255)
	private String ubicacion;

	@Column(nullable = false)
	private Instant ocurridoEn;

	@Column(length = 255)
	private String referenciaExterna;

@CreationTimestamp
	private Instant creadoEn;
}

