package com.tienda.plataforma.venta.entity;

import java.time.Instant;

import com.tienda.plataforma.catalogo.entity.Producto;
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

/** Producto en una lista (sin duplicados). Tabla `lista_deseo_items`. */
@Entity
@Table(name = "lista_deseo_items",
		uniqueConstraints = @UniqueConstraint(name = "uq_lista_deseo_producto",
				columnNames = { "lista_deseo_id", "producto_id" }))
@Getter
@Setter
@NoArgsConstructor
public class ListaDeseoItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lista_deseo_id", nullable = false)
	private ListaDeseos listaDeseos;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "producto_id", nullable = false)
	private Producto producto;

@CreationTimestamp
	private Instant creadoEn;
}

