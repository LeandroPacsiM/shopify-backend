package com.tienda.plataforma.inventario.entity;

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

/** Stock de un artículo en una ubicación. Tabla `niveles_inventario`. */
@Entity
@Table(name = "niveles_inventario",
		uniqueConstraints = @UniqueConstraint(name = "uq_nivel_inventario",
				columnNames = { "articulo_inventario_id", "ubicacion_id" }))
@Getter
@Setter
@NoArgsConstructor
public class NivelInventario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "articulo_inventario_id", nullable = false)
	private ArticuloInventario articulo;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ubicacion_id", nullable = false)
	private Ubicacion ubicacion;

	@Column(nullable = false)
	private Integer disponible = 0;

	@Column(nullable = false)
	private Integer reservado = 0;

@UpdateTimestamp
	private Instant actualizadoEn;

	/** Libre para vender según la convención: disponible ya descuenta reservado. */
	public int vendible() {
		return disponible - reservado;
	}
}

