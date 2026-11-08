package com.tienda.plataforma.catalogo.entity;

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

/** Valor de opción que compone una variante. Tabla `variante_opcion_valores`. */
@Entity
@Table(name = "variante_opcion_valores",
		uniqueConstraints = @UniqueConstraint(name = "uq_variante_opcion_valor",
				columnNames = { "variante_id", "opcion_valor_id" }))
@Getter
@Setter
@NoArgsConstructor
public class VarianteOpcionValor {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "variante_id", nullable = false)
	private ProductoVariante variante;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "opcion_valor_id", nullable = false)
	private ProductoOpcionValor opcionValor;
}
