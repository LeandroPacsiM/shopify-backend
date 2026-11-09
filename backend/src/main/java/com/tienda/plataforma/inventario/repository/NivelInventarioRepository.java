package com.tienda.plataforma.inventario.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.tienda.plataforma.inventario.entity.NivelInventario;

import jakarta.persistence.LockModeType;

public interface NivelInventarioRepository extends JpaRepository<NivelInventario, Long> {

	List<NivelInventario> findByArticuloId(Long articuloId);

	List<NivelInventario> findByUbicacionId(Long ubicacionId);

	Optional<NivelInventario> findByArticuloIdAndUbicacionId(Long articuloId, Long ubicacionId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<NivelInventario> findConBloqueoById(Long id);

	/**
	 * Reserva atómica: solo descuenta si hay vendible. Devuelve 1 si reservó,
	 * 0 si no había stock (el perdedor de la carrera lo ve aquí).
	 */
	@Modifying
	@Query("update NivelInventario n set n.reservado = n.reservado + :cantidad, n.actualizadoEn = CURRENT_TIMESTAMP where n.id = :id and n.disponible - n.reservado >= :cantidad")
	int reservarSiHayStock(Long id, int cantidad);
}
