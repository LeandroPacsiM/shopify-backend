package com.tienda.plataforma.venta.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.tienda.plataforma.tenants.entity.Cliente;
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

/** Compra confirmada. Tabla `pedidos` + archivado (V4) + cliente/tienda obligatorios (V5/V11). */
@Entity
@Table(name = "pedidos")
@Getter
@Setter
@NoArgsConstructor
public class Pedido {

	public static final String PENDIENTE = "pendiente";
	public static final String PAGADO = "pagado";
	public static final String EN_PREPARACION = "en_preparacion";
	public static final String ENVIADO = "enviado";
	public static final String ENTREGADO = "entregado";
	public static final String CANCELADO = "cancelado";
	public static final String REEMBOLSADO = "reembolsado";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tienda_id", nullable = false)
	private Tienda tienda;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cliente_id", nullable = false)
	private Cliente cliente;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "checkout_sesion_id", unique = true)
	private CheckoutSesion checkoutSesion;

	@Column(nullable = false, unique = true, length = 30)
	private String numeroPedido;

	@Column(nullable = false, length = 30)
	private String estado = PENDIENTE;

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

	/** Snapshot del contacto dejado en el checkout (V17, nullable). */
	@Column(length = 255)
	private String emailContacto;

	@Column(length = 50)
	private String telefonoContacto;

	@Column(length = 1000)
	private String notasCliente;

	/** Archivado lógico (V4): ocultar sin borrar. */
	@Column(nullable = false)
	private Boolean archivado = false;

	private Instant archivadoEn;

	private Instant realizadoEn;

@CreationTimestamp
	private Instant creadoEn;

@UpdateTimestamp
	private Instant actualizadoEn;
}

