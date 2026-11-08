package com.tienda.plataforma.auth.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Registro de auditoría (timeline). Tabla `registros_auditoria`.
 * Lo escribe el backend en la misma transacción que el cambio.
 */
@Entity
@Table(name = "registros_auditoria")
@Getter
@Setter
@NoArgsConstructor
public class RegistroAuditoria {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "usuario_id")
	private Usuario usuario;

	/** Nombre lógico de la entidad; sin FK (referencia polimórfica). */
	@Column(nullable = false, length = 100)
	private String entidad;

	private Long entidadId;

	@Column(nullable = false, length = 50)
	private String accion;

	/** JSONB mínimo, sin secretos ni PII innecesaria. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private String datosAnteriores;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private String datosNuevos;

	@Column(length = 45)
	private String ip;

@CreationTimestamp
	private Instant creadoEn;
}

