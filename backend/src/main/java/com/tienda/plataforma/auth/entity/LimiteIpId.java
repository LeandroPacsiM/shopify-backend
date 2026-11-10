package com.tienda.plataforma.auth.entity;

import java.io.Serializable;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Clave de `cliente_limite_ip`: IP + inicio de ventana horaria. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LimiteIpId implements Serializable {

	private InetAddress ip;

	private Instant ventana;

	@Override
	public boolean equals(Object o) {		if (this == o) {
			return true;
		}
		if (!(o instanceof LimiteIpId otra)) {
			return false;
		}
		return Objects.equals(ip, otra.ip) && Objects.equals(ventana, otra.ventana);
	}

	@Override
	public int hashCode() {
		return Objects.hash(ip, ventana);
	}
}
