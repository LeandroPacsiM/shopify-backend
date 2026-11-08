package com.tienda.plataforma.inventario.entity;

import java.time.Instant;

import com.tienda.plataforma.catalogo.entity.ProductoVariante;
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

/** Ficha de inventario de una variante. Tabla `articulos_inventario`. */
@Entity
@Table(name = "articulos_inventario")
@Getter
@Setter
@NoArgsConstructor
public class ArticuloInventario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "variante_id", nullable = false)
	private ProductoVariante variante;

	@Column(length = 100)
	private String sku;

	@Column(nullable = false)
	private Boolean requiereEnvio = true;

	@Column(nullable = false)
	private Boolean controlaStock = true;

@CreationTimestamp
	private Instant creadoEn;
}

