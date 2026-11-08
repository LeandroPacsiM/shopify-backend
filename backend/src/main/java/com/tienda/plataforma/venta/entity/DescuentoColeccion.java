package com.tienda.plataforma.venta.entity;

import com.tienda.plataforma.catalogo.entity.Coleccion;
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

/** Alcance de descuento a colecciones. Tabla `descuento_colecciones`. */
@Entity
@Table(name = "descuento_colecciones",
		uniqueConstraints = @UniqueConstraint(name = "uq_descuento_coleccion",
				columnNames = { "descuento_id", "coleccion_id" }))
@Getter
@Setter
@NoArgsConstructor
public class DescuentoColeccion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "descuento_id", nullable = false)
	private Descuento descuento;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "coleccion_id", nullable = false)
	private Coleccion coleccion;
}
