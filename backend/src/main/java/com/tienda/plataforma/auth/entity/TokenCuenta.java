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
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** Token de un solo uso para verificación email / reset password. Tabla `tokens_cuenta` (V15). */
@Entity
@Table(name = "tokens_cuenta")
@Getter
@Setter
@NoArgsConstructor
public class TokenCuenta {

	public static final String VERIFICACION = "verificacion_email";
	public static final String RESET = "reset_password";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	@Column(nullable = false, length = 30)
	private String tipo;

	/** SHA-256 del token opaco; el plano solo viaja por correo. */
	@Column(nullable = false, unique = true, length = 255)
	private String tokenHash;

	@Column(nullable = false)
	private Instant expiraEn;

	private Instant usadoEn;

	@CreationTimestamp
	private Instant creadoEn;

	public boolean vigente(Instant ahora) {
		return usadoEn == null && expiraEn.isAfter(ahora);
	}

	public void consumir(Instant ahora) {
		this.usadoEn = ahora;
	}
}
