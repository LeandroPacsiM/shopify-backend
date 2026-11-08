package com.tienda.plataforma.venta.entity;

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

/** Cambio de estado con actor. Tabla `historial_estados_pedido`. */
@Entity
@Table(name = "historial_estados_pedido")
@Getter
@Setter
@NoArgsConstructor
public class HistorialEstadoPedido {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pedido_id", nullable = false)
	private Pedido pedido;

	@Column(length = 30)
	private String estadoAnterior;

	@Column(nullable = false, length = 30)
	private String estadoNuevo;

	@Column(length = 255)
	private String comentario;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "usuario_id")
	private Usuario usuario;

@CreationTimestamp
	private Instant creadoEn;
}

