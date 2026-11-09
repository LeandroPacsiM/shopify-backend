package com.tienda.plataforma.venta.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.tienda.plataforma.venta.entity.Pedido;

import jakarta.persistence.LockModeType;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

	List<Pedido> findByTiendaId(Long tiendaId);

	Page<Pedido> findByTiendaId(Long tiendaId, Pageable paginacion);

	Page<Pedido> findByTiendaIdAndEstado(Long tiendaId, String estado, Pageable paginacion);

	List<Pedido> findByClienteId(Long clienteId);

	Optional<Pedido> findByCheckoutSesionId(Long checkoutSesionId);

	Optional<Pedido> findByNumeroPedido(String numeroPedido);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Pedido> findConBloqueoById(Long id);
}
