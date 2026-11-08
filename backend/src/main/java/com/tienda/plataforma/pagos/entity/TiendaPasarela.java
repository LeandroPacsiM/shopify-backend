package com.tienda.plataforma.pagos.entity;

import java.time.Instant;

import com.tienda.plataforma.tenants.entity.Tienda;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Credenciales de pasarela por tienda (secretos cifrados). Tabla `tienda_pasarelas` (V10). */
@Entity
@Table(name = "tienda_pasarelas",
		uniqueConstraints = @jakarta.persistence.UniqueConstraint(name = "uq_tienda_pasarela",
				columnNames = { "tienda_id", "pasarela_pago_id" }))
@Getter
@Setter
@NoArgsConstructor
public class TiendaPasarela {

	public static final String TEST = "test";
	public static final String VIVO = "vivo";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tienda_id", nullable = false)
	private Tienda tienda;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pasarela_pago_id", nullable = false)
	private PasarelaPago pasarela;

	@Column(nullable = false, length = 255)
	private String clavePublica;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String secretoCifrado;

	@Column(nullable = false, length = 10)
	private String modo = TEST;

	@Column(nullable = false)
	private Boolean activo = true;

@CreationTimestamp
	private Instant creadoEn;

@UpdateTimestamp
	private Instant actualizadoEn;
}

