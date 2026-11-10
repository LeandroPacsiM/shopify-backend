package com.tienda.plataforma.auth.entity;

import java.net.InetAddress;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contador de solicitudes de código por IP y ventana horaria.
 * Tabla `cliente_limite_ip` (V21). La IP se mapea como `InetAddress`
 * (Hibernate la lleva a `INET`; ver doc oficial). El incremento es un
 * upsert nativo atómico en `ClienteAuthService`, no `save()`.
 */
@Entity
@Table(name = "cliente_limite_ip")
@IdClass(LimiteIpId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LimiteIp {

	@Id
	private InetAddress ip;

	@Id
	private Instant ventana;

	@Column(nullable = false)
	private Integer contador = 1;
}
