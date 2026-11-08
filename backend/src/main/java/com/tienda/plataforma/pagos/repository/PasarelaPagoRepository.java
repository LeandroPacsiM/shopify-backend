package com.tienda.plataforma.pagos.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.pagos.entity.PasarelaPago;

public interface PasarelaPagoRepository extends JpaRepository<PasarelaPago, Long> {

	Optional<PasarelaPago> findByCodigo(String codigo);

	List<PasarelaPago> findByActivoTrue();
}
