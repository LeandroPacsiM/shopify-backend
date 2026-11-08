package com.tienda.plataforma.realtime.entity;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Aviso persistente de la bandeja admin. Tabla `notificaciones` (V16).
 * El WS solo avisa en vivo; esta fila es la fuente que la bandeja lee.
 */
@Entity
@Table(name = "notificaciones")
@Getter
@Setter
@NoArgsConstructor
public class Notificacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tienda_id", nullable = false)
	@JsonIgnore
	private Tienda tienda;

	@Column(nullable = false, length = 50)
	private String tipo;

	/** Referencia lógica (p. ej. `pedido`); sin FK, como auditoría. */
	@Column(length = 100)
	private String entidad;

	private Long entidadId;

	/** JSONB sin secretos: solo ids/número/total. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private Map<String, Object> datos;

	private Instant leidaEn;

	@CreationTimestamp
	private Instant creadoEn;

	public boolean leida() {
		return leidaEn != null;
	}
}
