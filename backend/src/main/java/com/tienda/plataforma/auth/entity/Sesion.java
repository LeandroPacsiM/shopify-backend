package com.tienda.plataforma.auth.entity;

import java.time.Instant;
import java.util.UUID;

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

/**
 * Sesión con refresh rotado. Tabla `sesiones` (V1 + V8).
 * Solo se guarda el hash; el refresh original solo lo tiene el cliente.
 */
@Entity
@Table(name = "sesiones")
@Getter
@Setter
@NoArgsConstructor
public class Sesion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	/** SHA-256 del refresh opaco. NULL hasta el cutover solo en transición. */
	@Column(unique = true, length = 255)
	private String tokenHash;

	/** Columna V1 en claro, en transición; se elimina en el cutover. */
	@Column(length = 255)
	private String tokenLegacy;

	/** Cadena de rotación del login. */
	private UUID familiaId;

	@Column(nullable = false)
	private Instant expiraEn;

	private Instant revocadoEn;

	/** Siguiente sesión de la cadena; NULL si es la vigente. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reemplazado_por_id")
	private Sesion reemplazadoPor;

	@Column(length = 50)
	private String motivoRevocacion;

	@Column(length = 45)
	private String ip;

	@Column(length = 255)
	private String userAgent;

	@CreationTimestamp
	private Instant creadoEn;

	public boolean vigente(Instant ahora) {
		return revocadoEn == null && expiraEn.isAfter(ahora);
	}
}
