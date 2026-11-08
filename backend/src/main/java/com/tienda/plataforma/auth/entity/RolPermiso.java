package com.tienda.plataforma.auth.entity;

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
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Permiso dentro de un rol. Tabla `rol_permisos`. */
@Entity
@Table(name = "rol_permisos",
		uniqueConstraints = @UniqueConstraint(name = "uq_rol_permiso",
				columnNames = { "rol_id", "permiso_id" }))
@Getter
@Setter
@NoArgsConstructor
public class RolPermiso {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "rol_id", nullable = false)
	private Rol rol;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "permiso_id", nullable = false)
	private Permiso permiso;
}
