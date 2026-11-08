package com.tienda.plataforma.catalogo.entity;

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

/** Imagen de producto, opcionalmente ligada a una variante. Tabla `producto_imagenes`. */
@Entity
@Table(name = "producto_imagenes")
@Getter
@Setter
@NoArgsConstructor
public class ProductoImagen {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "producto_id", nullable = false)
	private Producto producto;

	/** NULL si es general; la pertenencia al mismo producto se valida en backend. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "variante_id")
	private ProductoVariante variante;

	@Column(nullable = false, length = 500)
	private String url;

	@Column(length = 255)
	private String textoAlt;

	@Column(nullable = false)
	private Integer posicion = 0;

@CreationTimestamp
	private Instant creadoEn;
}

