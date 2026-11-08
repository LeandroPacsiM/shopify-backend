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
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Dirección postal de un cliente. Tabla `direcciones`. */
@Entity
@Table(name = "direcciones")
@Getter
@Setter
@NoArgsConstructor
public class Direccion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cliente_id", nullable = false)
	private Cliente cliente;

	@Column(nullable = false, length = 255)
	private String linea1;

	@Column(length = 255)
	private String linea2;

	@Column(length = 100)
	private String ciudad;

	@Column(length = 100)
	private String departamento;

	@Column(length = 20)
	private String codigoPostal;

	@Column(length = 100)
	private String pais;

	@Column(nullable = false)
	private Boolean esPrincipal = false;

@CreationTimestamp
	private Instant creadoEn;
}

