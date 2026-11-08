package com.tienda.plataforma.pagos.adapter;

import java.math.BigDecimal;

import com.tienda.plataforma.pagos.entity.TiendaPasarela;

/** Contrato por proveedor (RF-PAY-01). Mock primero, Stripe test después. */
public interface PaymentAdapter {

	/** Código de `pasarelas_pago.codigo` que atiende. */
	String codigo();

	CargoResultado autorizar(TiendaPasarela credenciales, BigDecimal monto, String moneda, String idempotencia);

	ReembolsoResultado reembolsar(TiendaPasarela credenciales, String transaccionId, BigDecimal monto);

	boolean verificaFirma(TiendaPasarela credenciales, String cuerpo, String firma);

	record CargoResultado(String transaccionExternaId, String estadoInicial, String metodo) {
	}

	record ReembolsoResultado(String transaccionExternaId, String estadoInicial) {
	}
}
