package com.tienda.plataforma.venta.entity;

import java.time.Instant;

import com.tienda.plataforma.catalogo.entity.Producto;
import com.tienda.plataforma.tenants.entity.Cliente;
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

/** Valoración moderada (una por cliente y producto). Tabla `resenas` (V4/V6). */
@Entity
@Table(name = "resenas",
		uniqueConstraints = @UniqueConstraint(name = "uq_resena_producto_cliente",
				columnNames = { "producto_id", "cliente_id" }))
@Getter
@Setter
@NoArgsConstructor
public class Resena {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** NULL si el producto se eliminó; la reseña sobrevive (V4). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "producto_id")
	private Producto producto;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cliente_id")
	private Cliente cliente;

	@Column(nullable = false)
	private Integer calificacion;

	@Column(length = 255)
	private String titulo;

	@Column(columnDefinition = "TEXT")
	private String comentario;

	@Column(nullable = false)
	private Boolean aprobada = false;

@CreationTimestamp
	private Instant creadoEn;
}

