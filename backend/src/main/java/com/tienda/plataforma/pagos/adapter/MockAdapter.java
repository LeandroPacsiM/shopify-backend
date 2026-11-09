package com.tienda.plataforma.pagos.adapter;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.tienda.plataforma.pagos.entity.Pago;
import com.tienda.plataforma.pagos.entity.Reembolso;
import com.tienda.plataforma.pagos.entity.TiendaPasarela;

/**
 * Mock para desarrollo y demo (modo test). Aprueba todo; el rechazo se
 * simula por webhook. En modo vivo se niega a operar.
 */
@Component
public class MockAdapter implements PaymentAdapter {

	@Override
	public String codigo() {
		return "mock";
	}

	@Override
	public CargoResultado autorizar(TiendaPasarela credenciales, BigDecimal monto, String moneda, String idempotencia) {
		if (!TiendaPasarela.TEST.equals(credenciales.getModo())) {
			throw new IllegalStateException("mock solo en modo test");
		}
		UUID transaccion = UUID.nameUUIDFromBytes(("mock-payment:" + idempotencia)
				.getBytes(StandardCharsets.UTF_8));
		return new CargoResultado("mock-tx-" + transaccion, Pago.PROCESANDO, "mock");
	}

	@Override
	public ReembolsoResultado reembolsar(TiendaPasarela credenciales, String transaccionId,
			BigDecimal monto, String idempotencia) {
		if (!TiendaPasarela.TEST.equals(credenciales.getModo())) {
			throw new IllegalStateException("mock solo en modo test");
		}
		UUID refundId = UUID.nameUUIDFromBytes(("mock-refund:" + idempotencia)
				.getBytes(StandardCharsets.UTF_8));
		return new ReembolsoResultado("mock-rf-" + refundId, Reembolso.PROCESANDO);
	}

	@Override
	public boolean verificaFirma(TiendaPasarela credenciales, String cuerpo, String firma) {
		return TiendaPasarela.TEST.equals(credenciales.getModo());
	}
}
