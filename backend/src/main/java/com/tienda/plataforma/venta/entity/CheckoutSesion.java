package com.tienda.plataforma.venta.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Direccion;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Intento de checkout con totales congelados (propuesta, no verdad).
 * Tabla `checkout_sesiones` + `tienda_id` NOT NULL (V5/V11).
 */
@Entity
@Table(name = "checkout_sesiones")
@Getter
@Setter
@NoArgsConstructor
public class CheckoutSesion {

	public static final String PENDIENTE = "pendiente";
	public static final String COMPLETADO = "completado";
	public static final String EXPIRADO = "expirado";
	public static final String CANCELADO = "cancelado";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tienda_id", nullable = false)
	private Tienda tienda;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "carrito_id", nullable = false)
	private Carrito carrito;

	/** Obligatorio desde V5 (sin guest). */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cliente_id", nullable = false)
	private Cliente cliente;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "direccion_envio_id")
	private Direccion direccionEnvio;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "direccion_facturacion_id")
	private Direccion direccionFacturacion;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "metodo_envio_id")
	private MetodoEnvio metodoEnvio;

	/** Descuento validado al crear; se registra y consume al confirmar. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "descuento_id")
	private Descuento descuento;

	@Column(nullable = false, length = 3) @JdbcTypeCode(SqlTypes.CHAR)
	private String moneda = "PEN";

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal subtotal = BigDecimal.ZERO;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal impuestoTotal = BigDecimal.ZERO;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal envioTotal = BigDecimal.ZERO;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal descuentoTotal = BigDecimal.ZERO;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal total = BigDecimal.ZERO;

	@Column(nullable = false, length = 30)
	private String estado = PENDIENTE;

	private Instant expiraEn;

	/** Contacto del comprador; se copia al pedido al confirmar (V17). */
	@Column(length = 255)
	private String emailContacto;

	@Column(length = 50)
	private String telefonoContacto;

	@Column(length = 1000)
	private String notasCliente;

@CreationTimestamp
	private Instant creadoEn;
}

