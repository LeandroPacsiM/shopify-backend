package com.tienda.plataforma.inventario.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.catalogo.entity.ProductoVariante;
import com.tienda.plataforma.catalogo.repository.ProductoVarianteRepository;
import com.tienda.plataforma.inventario.dto.CrearUbicacionRequest;
import com.tienda.plataforma.inventario.dto.MovimientoStockRequest;
import com.tienda.plataforma.inventario.dto.NivelResponse;
import com.tienda.plataforma.inventario.dto.UbicacionResponse;
import com.tienda.plataforma.inventario.entity.ArticuloInventario;
import com.tienda.plataforma.inventario.entity.MovimientoInventario;
import com.tienda.plataforma.inventario.entity.NivelInventario;
import com.tienda.plataforma.inventario.entity.Ubicacion;
import com.tienda.plataforma.inventario.repository.ArticuloInventarioRepository;
import com.tienda.plataforma.inventario.repository.MovimientoInventarioRepository;
import com.tienda.plataforma.inventario.repository.NivelInventarioRepository;
import com.tienda.plataforma.inventario.repository.UbicacionRepository;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.tenants.repository.TiendaRepository;

/**
 * RF-ADMIN-02: stock por ubicación con bloqueo. Entradas, salidas y ajustes
 * mueven `disponible`; `reservado` solo lo toca el flujo de pedidos
 * (reserva al confirmar, salida al despachar, liberación al cancelar).
 */
@Service
public class InventarioAdminService {

	private final UbicacionRepository ubicaciones;
	private final ArticuloInventarioRepository articulos;
	private final NivelInventarioRepository niveles;
	private final MovimientoInventarioRepository movimientos;
	private final ProductoVarianteRepository variantes;
	private final TiendaRepository tiendas;

	public InventarioAdminService(UbicacionRepository ubicaciones,
			ArticuloInventarioRepository articulos, NivelInventarioRepository niveles,
			MovimientoInventarioRepository movimientos, ProductoVarianteRepository variantes,
			TiendaRepository tiendas) {
		this.ubicaciones = ubicaciones;
		this.articulos = articulos;
		this.niveles = niveles;
		this.movimientos = movimientos;
		this.variantes = variantes;
		this.tiendas = tiendas;
	}

	@Transactional(readOnly = true)
	public List<UbicacionResponse> ubicaciones(Long tiendaId) {
		return ubicaciones.findByTiendaId(tiendaId).stream()
				.map(u -> new UbicacionResponse(u.getId(), u.getNombre(), u.getActivo()))
				.toList();
	}

	@Transactional
	public UbicacionResponse crearUbicacion(Long tiendaId, CrearUbicacionRequest req) {
		Ubicacion ubicacion = new Ubicacion();
		ubicacion.setTienda(tiendas.findById(tiendaId)
				.orElseThrow(() -> new NoEncontradoException("tienda")));
		ubicacion.setNombre(req.nombre().trim());
		ubicacion.setDireccion(vacioANulo(req.direccion()));
		ubicacion.setCiudad(vacioANulo(req.ciudad()));
		ubicacion.setPais(vacioANulo(req.pais()));
		ubicacion.setActivo(true);
		ubicaciones.save(ubicacion);
		return new UbicacionResponse(ubicacion.getId(), ubicacion.getNombre(), ubicacion.getActivo());
	}

	@Transactional(readOnly = true)
	public List<NivelResponse> niveles(Long tiendaId, Long varianteId) {
		return ubicaciones.findByTiendaId(tiendaId).stream()
				.flatMap(u -> niveles.findByUbicacionId(u.getId()).stream())
				.filter(n -> varianteId == null
						|| n.getArticulo().getVariante().getId().equals(varianteId))
				.map(n -> new NivelResponse(n.getId(), n.getArticulo().getVariante().getId(),
						n.getUbicacion().getId(), n.getDisponible(), n.getReservado()))
				.toList();
	}

	@Transactional
	public NivelResponse mover(Long tiendaId, MovimientoStockRequest req) {
		String tipo = req.tipo().trim().toLowerCase(Locale.ROOT);
		if (!MovimientoInventario.ENTRADA.equals(tipo)
				&& !MovimientoInventario.SALIDA.equals(tipo)
				&& !MovimientoInventario.AJUSTE.equals(tipo)) {
			throw new ConflictoException("tipo inválido (reserva/liberacion son del flujo de pedidos)");
		}
		if (req.cantidad() == null || req.cantidad() == 0) {
			throw new ConflictoException("cantidad inválida");
		}
		if ((MovimientoInventario.ENTRADA.equals(tipo) || MovimientoInventario.SALIDA.equals(tipo))
				&& req.cantidad() < 0) {
			throw new ConflictoException("entrada/salida con cantidad positiva");
		}
		Ubicacion ubicacion = ubicaciones.findById(req.ubicacionId())
				.orElseThrow(() -> new NoEncontradoException("ubicación"));
		if (!ubicacion.getTienda().getId().equals(tiendaId)) {
			throw new NoEncontradoException("ubicación");
		}
		if (!Boolean.TRUE.equals(ubicacion.getActivo())) {
			throw new ConflictoException("ubicación desactivada");
		}
		ProductoVariante variante = variantes.findById(req.varianteId())
				.orElseThrow(() -> new NoEncontradoException("variante"));
		if (!variante.getProducto().getTienda().getId().equals(tiendaId)) {
			throw new NoEncontradoException("variante");
		}
		ArticuloInventario articulo = articulos.findByVarianteId(variante.getId()).orElse(null);
		if (articulo == null) {
			if (!MovimientoInventario.ENTRADA.equals(tipo)) {
				throw new NoEncontradoException("artículo");
			}
			articulo = new ArticuloInventario();
			articulo.setVariante(variante);
			articulo.setSku(variante.getSku());
			articulo.setRequiereEnvio(true);
			articulo.setControlaStock(true);
			articulos.save(articulo);
		}
		NivelInventario previo = niveles
				.findByArticuloIdAndUbicacionId(articulo.getId(), ubicacion.getId()).orElse(null);
		if (previo == null) {
			if (!MovimientoInventario.ENTRADA.equals(tipo)) {
				throw new ConflictoException("sin stock para mover");
			}
			previo = new NivelInventario();
			previo.setArticulo(articulo);
			previo.setUbicacion(ubicacion);
			previo.setDisponible(0);
			previo.setReservado(0);
			niveles.save(previo);
		}
		NivelInventario nivel = niveles.findConBloqueoById(previo.getId())
				.orElseThrow(() -> new NoEncontradoException("nivel"));
		int nuevoDisponible = MovimientoInventario.SALIDA.equals(tipo)
				? nivel.getDisponible() - req.cantidad()
				: nivel.getDisponible() + req.cantidad();
		if (nuevoDisponible < 0) {
			throw new ConflictoException("sin stock para mover");
		}
		nivel.setDisponible(nuevoDisponible);
		nivel.setActualizadoEn(Instant.now());
		niveles.save(nivel);

		MovimientoInventario movimiento = new MovimientoInventario();
		movimiento.setNivel(nivel);
		movimiento.setTipoMovimiento(tipo);
		movimiento.setCantidad(req.cantidad());
		movimiento.setMotivo(vacioANulo(req.motivo()));
		movimiento.setCreadoEn(Instant.now());
		movimientos.save(movimiento);
		return new NivelResponse(nivel.getId(), variante.getId(), ubicacion.getId(),
				nivel.getDisponible(), nivel.getReservado());
	}

	private String vacioANulo(String valor) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		return valor.trim();
	}
}
