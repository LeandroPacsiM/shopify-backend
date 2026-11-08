package com.tienda.plataforma.pagos.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.pagos.dto.PasarelaResponse;
import com.tienda.plataforma.pagos.dto.RegistrarPasarelaRequest;
import com.tienda.plataforma.pagos.entity.PasarelaPago;
import com.tienda.plataforma.pagos.entity.TiendaPasarela;
import com.tienda.plataforma.pagos.repository.PasarelaPagoRepository;
import com.tienda.plataforma.pagos.repository.TiendaPasarelaRepository;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.shared.exception.ProhibidoException;
import com.tienda.plataforma.shared.security.CryptoService;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;

/**
 * Credenciales de pasarela por tienda (RF-PAY-01). Solo el `admin` de la
 * tienda (o `super_admin`) registra o rota: el secreto se cifra (AES-GCM)
 * y jamás vuelve en una respuesta. Re-registrar rota la credencial.
 */
@Service
public class TiendaPasarelaService {

	private final TiendaPasarelaRepository credenciales;
	private final PasarelaPagoRepository pasarelas;
	private final TiendaRepository tiendas;
	private final TiendaUsuarioRepository tiendaUsuarios;
	private final CryptoService crypto;

	public TiendaPasarelaService(TiendaPasarelaRepository credenciales,
			PasarelaPagoRepository pasarelas, TiendaRepository tiendas,
			TiendaUsuarioRepository tiendaUsuarios, CryptoService crypto) {
		this.credenciales = credenciales;
		this.pasarelas = pasarelas;
		this.tiendas = tiendas;
		this.tiendaUsuarios = tiendaUsuarios;
		this.crypto = crypto;
	}

	@Transactional(readOnly = true)
	public List<PasarelaResponse> listar(Long tiendaId) {
		return credenciales.findByTiendaIdAndActivoTrue(tiendaId).stream()
				.map(this::responder).toList();
	}

	@Transactional
	public PasarelaResponse registrar(Long usuarioId, Long tiendaId, boolean esSuper,
			RegistrarPasarelaRequest req) {
		if (!esSuper) {
			String rol = tiendaUsuarios.findByTiendaIdAndUsuarioId(tiendaId, usuarioId)
					.map(TiendaUsuario::getRolTienda).orElse(null);
			if (!TiendaUsuario.ADMIN.equals(rol)) {
				throw new ProhibidoException("solo el admin registra pasarelas");
			}
		}
		if (!TiendaPasarela.TEST.equals(req.modo()) && !TiendaPasarela.VIVO.equals(req.modo())) {
			throw new ConflictoException("modo inválido");
		}
		Tienda tienda = tiendas.findById(tiendaId)
				.orElseThrow(() -> new NoEncontradoException("tienda"));
		String codigo = req.codigo().trim().toLowerCase();
		PasarelaPago pasarela = pasarelas.findByCodigo(codigo).orElseGet(() -> {
			PasarelaPago nueva = new PasarelaPago();
			nueva.setNombre(Character.toUpperCase(codigo.charAt(0)) + codigo.substring(1));
			nueva.setCodigo(codigo);
			nueva.setActivo(true);
			return pasarelas.save(nueva);
		});
		TiendaPasarela cred = credenciales.findByTiendaIdAndPasarelaId(tiendaId, pasarela.getId())
				.orElseGet(TiendaPasarela::new);
		cred.setTienda(tienda);
		cred.setPasarela(pasarela);
		cred.setClavePublica(req.clavePublica().trim());
		cred.setSecretoCifrado(crypto.cifrar(req.secreto()));
		cred.setModo(req.modo());
		cred.setActivo(true);
		credenciales.save(cred);
		return responder(cred);
	}

	private PasarelaResponse responder(TiendaPasarela cred) {
		return new PasarelaResponse(cred.getId(), cred.getPasarela().getCodigo(),
				cred.getPasarela().getNombre(), cred.getModo(),
				Boolean.TRUE.equals(cred.getActivo()));
	}
}
