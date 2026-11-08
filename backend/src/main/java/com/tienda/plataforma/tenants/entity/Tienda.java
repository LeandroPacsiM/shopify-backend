package com.tienda.plataforma.tenants.entity;

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

/** Tenant de la plataforma multi-tienda. Tabla `tiendas` (V10). */
@Entity
@Table(name = "tiendas")
@Getter
@Setter
@NoArgsConstructor
public class Tienda {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 255)
	private String nombre;

	@Column(nullable = false, unique = true, length = 100)
	private String slug;

	@Column(length = 255)
	private String descripcion;

	/** La baja es desactivar, nunca DELETE con recursos (RESTRICT V11). */
	@Column(nullable = false)
	private Boolean activa = true;

	@CreationTimestamp
	private Instant creadoEn;

	@UpdateTimestamp
	private Instant actualizadoEn;
}
