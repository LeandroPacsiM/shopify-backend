package com.tienda.plataforma.auth.entity;

import java.time.Instant;

import com.tienda.plataforma.tenants.entity.Cliente;
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
import org.hibernate.annotations.CreationTimestamp;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Código de 6 dígitos para passwordless CUSTOMER. Tabla `cliente_codigos`
 * (V21). Se guarda HMAC-SHA256 (secreto en entorno), nunca el plano.
 * El perfil (`cliente_id`) solo se fija al verificar (D3).
 */
@Entity
@Table(name = "cliente_codigos")
@Getter
@Setter
@NoArgsConstructor
public class CodigoCliente {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tienda_id", nullable = false)
	private Tienda tienda;

	/** Email normalizado (lower+trim). */
	@Column(nullable = false, length = 255)
	private String email;

	/** NULL hasta verificar con éxito; luego el perfil creado/recuperado. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cliente_id")
	private Cliente cliente;

	@Column(nullable = false, length = 255)
	private String codigoHmac;

	@Column(nullable = false)
	private Integer intentos = 0;

	@Column(nullable = false)
	private Instant expiraEn;

	private Instant usadoEn;

	@Column(length = 45)
	private String ip;

	@CreationTimestamp
	private Instant creadoEn;

	public boolean vigente(Instant ahora) {
		return usadoEn == null && expiraEn.isAfter(ahora);
	}

	public void consumir(Instant ahora) {
		this.usadoEn = ahora;
	}
}
