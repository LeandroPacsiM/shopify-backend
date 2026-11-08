package com.tienda.plataforma.envios.entity;

import java.time.Instant;

import com.tienda.plataforma.inventario.entity.Ubicacion;
import com.tienda.plataforma.venta.entity.Pedido;
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

/** Despacho de un pedido. Tabla `envios`. */
@Entity
@Table(name = "envios")
@Getter
@Setter
@NoArgsConstructor
public class Envio {

	public static final String PENDIENTE = "pendiente";
	public static final String PREPARANDO = "preparando";
	public static final String ENVIADO = "enviado";
	public static final String EN_TRANSITO = "en_transito";
	public static final String EN_REPARTO = "en_reparto";
	public static final String ENTREGADO = "entregado";
	public static final String DEVUELTO = "devuelto";
	public static final String CANCELADO = "cancelado";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pedido_id", nullable = false)
	private Pedido pedido;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "transportista_id")
	private Transportista transportista;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ubicacion_origen_id")
	private Ubicacion ubicacionOrigen;

	@Column(length = 100)
	private String numeroTracking;

	@Column(length = 500)
	private String urlTracking;

	/** Copia histórica del nombre; la relación es `transportista`. */
	@Column(name = "transportista", length = 100)
	private String transportistaNombre;

	@Column(nullable = false, length = 30)
	private String estado = PENDIENTE;

	private Instant enviadoEn;

	private Instant entregadoEn;

@CreationTimestamp
	private Instant creadoEn;
}

