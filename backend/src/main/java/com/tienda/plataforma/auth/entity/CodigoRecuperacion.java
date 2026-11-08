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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Backup codes 2FA de un solo uso. Tabla `usuario_codigos_recuperacion` (V2). */
@Entity
@Table(name = "usuario_codigos_recuperacion",
		uniqueConstraints = @UniqueConstraint(name = "uq_usuario_codigo",
				columnNames = { "usuario_id", "codigo_hash" }))
@Getter
@Setter
@NoArgsConstructor
public class CodigoRecuperacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	/** Hash BCrypt/Argon2; el original se muestra una sola vez. */
	@Column(nullable = false, length = 255)
	private String codigoHash;

	private Instant usadoEn;

	@CreationTimestamp
	private Instant creadoEn;

	public boolean vigente() {
		return usadoEn == null;
	}

	public void consumir(Instant ahora) {
		this.usadoEn = ahora;
	}
}
