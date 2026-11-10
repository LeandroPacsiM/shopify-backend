package com.tienda.plataforma.tenants.entity;

import java.time.Instant;

import com.tienda.plataforma.auth.entity.Usuario;
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

/** Rol staff de un usuario dentro de una tienda. Tabla `tienda_usuarios` (V12 + `store_owner` V21). */
@Entity
@Table(name = "tienda_usuarios",
		uniqueConstraints = @UniqueConstraint(name = "uq_tienda_usuario",
				columnNames = { "tienda_id", "usuario_id" }))
@Getter
@Setter
@NoArgsConstructor
public class TiendaUsuario {

	public static final String STORE_OWNER = "store_owner";
	public static final String ADMIN = "admin";
	public static final String OPERADOR = "operador";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tienda_id", nullable = false)
	private Tienda tienda;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	/** Solo `store_owner`/`admin`/`operador` (CHECK en BD, V21). */
	@Column(nullable = false, length = 20)
	private String rolTienda;

@CreationTimestamp
	private Instant creadoEn;
}

