package com.tienda.plataforma.auth.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cuenta de acceso. Tabla `usuarios` (V1 + columnas 2FA de V2). */
@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 255)
	private String email;

	@Column(nullable = false, length = 255)
	private String passwordHash;

	@Column(length = 100)
	private String nombre;

	@Column(length = 100)
	private String apellido;

	@Column(nullable = false)
	private Boolean activo = true;

	@Column(nullable = false)
	private Boolean emailVerificado = false;

	/** Secreto TOTP cifrado (llave en entorno). NULL hasta enrolar. */
	@Column(length = 512)
	private String totpSecret;

	@Column(nullable = false)
	private Boolean totpHabilitado = false;

	private Instant totpConfirmadoEn;

	@Column(nullable = false)
	private Integer intentosFallidos = 0;

	private Instant bloqueadoHasta;

	private Instant ultimoAcceso;

	@CreationTimestamp
	private Instant creadoEn;

	@UpdateTimestamp
	private Instant actualizadoEn;
}
