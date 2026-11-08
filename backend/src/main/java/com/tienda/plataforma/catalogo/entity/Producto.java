package com.tienda.plataforma.catalogo.entity;

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

/** Producto (cabecera no comprable). Tabla `productos` + `tienda_id` (V11). */
@Entity
@Table(name = "productos")
@Getter
@Setter
@NoArgsConstructor
public class Producto {

	public static final String BORRADOR = "borrador";
	public static final String ACTIVO = "activo";
	public static final String ARCHIVADO = "archivado";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tienda_id", nullable = false)
	private Tienda tienda;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "proveedor_id")
	private Proveedor proveedor;

	@Column(nullable = false, length = 255)
	private String titulo;

	@Column(nullable = false, unique = true, length = 255)
	private String slug;

	@Column(columnDefinition = "TEXT")
	private String descripcion;

	/** borrador | activo | archivado (CHECK en BD). */
	@Column(nullable = false, length = 20)
	private String estado = BORRADOR;

	private Instant publicadoEn;

@CreationTimestamp
	private Instant creadoEn;

@UpdateTimestamp
	private Instant actualizadoEn;

	public boolean comprable() {
		return ACTIVO.equals(estado);
	}
}

