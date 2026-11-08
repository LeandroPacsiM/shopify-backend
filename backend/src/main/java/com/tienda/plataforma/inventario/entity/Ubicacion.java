package com.tienda.plataforma.inventario.entity;

import java.time.Instant;

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
import org.hibernate.annotations.UpdateTimestamp;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Sucursal de una tienda. Tabla `ubicaciones` + `tienda_id` (V11). */
@Entity
@Table(name = "ubicaciones")
@Getter
@Setter
@NoArgsConstructor
public class Ubicacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tienda_id", nullable = false)
	private Tienda tienda;

	@Column(nullable = false, length = 255)
	private String nombre;

	@Column(length = 255)
	private String direccion;

	@Column(length = 100)
	private String ciudad;

	@Column(length = 100)
	private String pais;

	/** Desactivar antes de eliminar; con niveles se bloquea (RESTRICT V4). */
	@Column(nullable = false)
	private Boolean activo = true;

@CreationTimestamp
	private Instant creadoEn;
}

