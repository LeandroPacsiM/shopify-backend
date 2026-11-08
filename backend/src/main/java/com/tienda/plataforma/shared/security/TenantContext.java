package com.tienda.plataforma.shared.security;

import com.tienda.plataforma.tenants.entity.Tienda;

/** Tienda resuelta para el request actual. La fija TenantFilter; se limpia siempre. */
public final class TenantContext {

	private static final ThreadLocal<Tienda> ACTUAL = new ThreadLocal<>();

	private TenantContext() {
	}

	public static void fijar(Tienda tienda) {
		ACTUAL.set(tienda);
	}

	public static Tienda actual() {
		return ACTUAL.get();
	}

	public static void limpiar() {
		ACTUAL.remove();
	}
}
