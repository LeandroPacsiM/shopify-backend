package com.tienda.plataforma.venta.entity;

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

/** Carrito por token; anónimo hasta el checkout. Tabla `carritos`. */
@Entity
@Table(name = "carritos")
@Getter
@Setter
@NoArgsConstructor
public class Carrito {

	public static final String ACTIVO = "activo";
	public static final String CONVERTIDO = "convertido";
	public static final String ABANDONADO = "abandonado";
	public static final String EXPIRADO = "expirado";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tienda_id", nullable = false)
	private Tienda tienda;

	/** NULL en carrito anónimo; se asocia al autenticarse. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cliente_id")
	private Cliente cliente;

	@Column(nullable = false, unique = true, length = 255)
	private String token;

	@Column(nullable = false, length = 3) @JdbcTypeCode(SqlTypes.CHAR)
	private String moneda = "PEN";

	@Column(nullable = false, length = 20)
	private String estado = ACTIVO;

@CreationTimestamp
	private Instant creadoEn;

@UpdateTimestamp
	private Instant actualizadoEn;

	private Instant expiraEn;
}

