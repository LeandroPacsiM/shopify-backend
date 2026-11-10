package com.tienda.plataforma.auth.entity;

import java.time.Instant;
import java.util.UUID;

import com.tienda.plataforma.tenants.entity.Cliente;
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
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sesión CUSTOMER con refresh rotado (7 días). Tabla `cliente_sesiones`
 * (V21). Réplica del patrón `sesiones`/V8, sin TOTP: el factor es el email.
 */
@Entity
@Table(name = "cliente_sesiones")
@Getter
@Setter
@NoArgsConstructor
public class ClienteSesion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cliente_id", nullable = false)
	private Cliente cliente;

	/** SHA-256 del refresh opaco. Solo lo tiene el CUSTOMER. */
	@Column(nullable = false, unique = true, length = 255)
	private String tokenHash;

	/** Cadena de rotación. */
	private UUID familiaId;

	@Column(nullable = false)
	private Instant expiraEn;

	private Instant revocadoEn;

	/** Siguiente sesión de la cadena; NULL si es la vigente. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reemplazado_por_id")
	private ClienteSesion reemplazadoPor;

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
