package com.tienda.plataforma.venta.controller;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.security.TenantContext;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.venta.dto.CrearDescuentoRequest;
import com.tienda.plataforma.venta.entity.Descuento;
import com.tienda.plataforma.venta.repository.DescuentoRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Admin de tienda: cupones (RF-DISC, RF-ADMIN-03). */
@RestController
@RequestMapping("/api/admin/cupones")
@Tag(name = "Admin cupones", description = "Descuentos de la tienda")
public class AdminDescuentoController {

	private static final Set<String> TIPOS = Set.of(
			Descuento.PORCENTAJE, Descuento.MONTO_FIJO, Descuento.ENVIO_GRATIS);

	private final DescuentoRepository descuentos;
	private final TiendaRepository tiendas;

	public AdminDescuentoController(DescuentoRepository descuentos, TiendaRepository tiendas) {
		this.descuentos = descuentos;
		this.tiendas = tiendas;
	}

	@GetMapping
	@Operation(summary = "Lista cupones de la tienda")
	public List<Descuento> listar() {
		return descuentos.findByTiendaIdAndActivoTrue(tiendaId());
	}

	@PostMapping
	@Transactional
	@Operation(summary = "Crea un cupón (valida tipo, valor y vigencia)")
	public Descuento crear(@Valid @RequestBody CrearDescuentoRequest req) {
		Tienda tienda = tiendas.findById(tiendaId())
				.orElseThrow(() -> new NoAutorizadoException("sin tienda"));
		if (!TIPOS.contains(req.tipo())) {
			throw new ConflictoException("tipo inválido");
		}
		if (req.usoMaximo() != null && req.usoMaximo() <= 0) {
			throw new ConflictoException("usoMaximo debe ser positivo");
		}
		if (req.iniciaEn() != null && req.terminaEn() != null && !req.terminaEn().isAfter(req.iniciaEn())) {
			throw new ConflictoException("vigencia incoherente");
		}
		if (descuentos.findByCodigoAndTiendaId(req.codigo().trim(), tienda.getId()).isPresent()) {
			throw new ConflictoException("código en uso");
		}
		Descuento descuento = new Descuento();
		descuento.setTienda(tienda);
		descuento.setCodigo(req.codigo().trim());
		descuento.setTipo(req.tipo());
		descuento.setValor(req.valor());
		descuento.setDescripcion(req.descripcion());
		descuento.setUsoMaximo(req.usoMaximo());
		descuento.setUsosActuales(0);
		descuento.setIniciaEn(req.iniciaEn());
		descuento.setTerminaEn(req.terminaEn());
		descuento.setActivo(true);
		descuento.setCreadoEn(Instant.now());
		return descuentos.save(descuento);
	}

	private Long tiendaId() {
		if (TenantContext.actual() == null) {
			throw new NoAutorizadoException("sin tienda");
		}
		return TenantContext.actual().getId();
	}
}
