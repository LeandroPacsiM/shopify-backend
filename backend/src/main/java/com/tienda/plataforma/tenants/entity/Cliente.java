package com.tienda.plataforma.tenants.entity;

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

/** Perfil CUSTOMER de una tienda, sin vínculo con `usuarios` (V23, ADR-016). Tabla `clientes`. */
@Entity
@Table(name = "clientes", uniqueConstraints = @UniqueConstraint(columnNames = { "tienda_id", "email" }))
@Getter
@Setter
@NoArgsConstructor
public class Cliente {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tienda_id", nullable = false)
	private Tienda tienda;

	/** Email normalizado (lower+trim). UNIQUE por tienda desde V20. */
	@Column(nullable = false, length = 255)
	private String email;

	@Column(length = 100)
	private String nombre;

	@Column(length = 100)
	private String apellido;

	@Column(length = 30)
	private String telefono;

	@Column(nullable = false)
	private Boolean aceptaMarketing = false;

	@CreationTimestamp
	private Instant creadoEn;
}
