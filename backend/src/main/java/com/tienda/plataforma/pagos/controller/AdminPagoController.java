package com.tienda.plataforma.pagos.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.plataforma.pagos.dto.CrearReembolsoRequest;
import com.tienda.plataforma.pagos.dto.ReembolsoResponse;
import com.tienda.plataforma.pagos.service.PagoService;
import com.tienda.plataforma.shared.security.TenantContext;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Admin de tienda: reembolsos (RF-PAY-03, RF-ADMIN-03). */
@RestController
@RequestMapping("/api/admin/pagos")
@Tag(name = "Admin pagos", description = "Reembolsos de la tienda")
public class AdminPagoController {

	private final PagoService pagoService;

	public AdminPagoController(PagoService pagoService) {
		this.pagoService = pagoService;
	}

	@PostMapping("/{id}/reembolsos")
	@Operation(summary = "Reembolsa un pago aprobado; requiere Idempotency-Key estable por reintento")
	public ReembolsoResponse reembolsar(@PathVariable Long id,
			@RequestHeader("Idempotency-Key") String idempotencia,
			@Valid @RequestBody CrearReembolsoRequest req) {
		if (TenantContext.actual() == null) {
			throw new NoAutorizadoException("sin tienda");
		}
		return pagoService.reembolsar(TenantContext.actual().getId(), id,
				req.monto(), req.motivo(), idempotencia);
	}
}
