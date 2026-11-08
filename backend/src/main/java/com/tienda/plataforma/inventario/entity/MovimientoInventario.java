package com.tienda.plataforma.inventario.entity;

import java.time.Instant;

import com.tienda.plataforma.auth.entity.Usuario;
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

/** Entrada/salida/ajuste/reserva/liberación de un nivel. Tabla `movimientos_inventario`. */
@Entity
@Table(name = "movimientos_inventario")
@Getter
@Setter
@NoArgsConstructor
public class MovimientoInventario {

	public static final String ENTRADA = "entrada";
	public static final String SALIDA = "salida";
	public static final String AJUSTE = "ajuste";
	public static final String RESERVA = "reserva";
	public static final String LIBERACION = "liberacion";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "nivel_inventario_id", nullable = false)
	private NivelInventario nivel;

	/** entrada | salida | ajuste | reserva | liberacion (CHECK en BD). */
	@Column(nullable = false, length = 30)
	private String tipoMovimiento;

	/** Distinto de cero (CHECK en BD). */
	@Column(nullable = false)
	private Integer cantidad;

	@Column(length = 255)
	private String motivo;

	@Column(length = 100)
	private String referencia;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "usuario_id")
	private Usuario usuario;

@CreationTimestamp
	private Instant creadoEn;
}

