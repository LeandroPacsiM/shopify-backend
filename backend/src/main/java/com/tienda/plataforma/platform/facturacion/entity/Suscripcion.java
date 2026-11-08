package com.tienda.plataforma.platform.facturacion.entity;

import java.time.Instant;

import com.tienda.plataforma.tenants.entity.Tienda;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Suscripción de la tienda al software (RF-BILL). Tabla `suscripciones`
 * (V18): una fila por tienda. El cobro Stripe llega en V19; aquí la
 * máquina de estados + enforcement (sin activa no hay checkout nuevo).
 */
@Entity
@Table(name = "suscripciones")
@Getter
@Setter
@NoArgsConstructor
public class Suscripcion {

	public static final String MENSUAL = "mensual";
	public static final String ANUAL = "anual";

	public static final String PENDIENTE = "pendiente";
	public static final String ACTIVA = "activa";
	public static final String VENCIDA = "vencida";
	public static final String CANCELADA = "cancelada";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tienda_id", nullable = false, unique = true)
	private Tienda tienda;

	@Column(nullable = false, length = 20)
	private String plan = MENSUAL;

	@Column(nullable = false, length = 20)
	private String estado = PENDIENTE;

	@Column(length = 255)
	private String stripeCustomerId;

	@Column(length = 255)
	private String stripeSubscriptionId;

	private Instant periodoFin;

	@CreationTimestamp
	private Instant creadoEn;

	@UpdateTimestamp
	private Instant actualizadoEn;

	public boolean vigente(Instant ahora) {
		return ACTIVA.equals(estado) && periodoFin != null && periodoFin.isAfter(ahora);
	}
}
