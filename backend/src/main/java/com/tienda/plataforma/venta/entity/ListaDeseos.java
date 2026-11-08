package com.tienda.plataforma.venta.entity;

import java.time.Instant;

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
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Lista de deseos de un cliente. Tabla `listas_deseos`. */
@Entity
@Table(name = "listas_deseos")
@Getter
@Setter
@NoArgsConstructor
public class ListaDeseos {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cliente_id", nullable = false)
	private Cliente cliente;

	@Column(nullable = false, length = 100)
	private String nombre = "Favoritos";

@CreationTimestamp
	private Instant creadoEn;
}

