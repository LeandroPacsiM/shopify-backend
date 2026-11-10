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
import com.tienda.plataforma.shared.auditoria.AuditoriaService;
import com.tienda.plataforma.shared.security.CryptoService;
import com.tienda.plataforma.shared.security.StripeTestKeys;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;

/**
 * Credenciales de pasarela por tienda (RF-PAY-01). Solo el `admin` o el
 * `store_owner` de la tienda (o `super_admin`) registra o rota: el secreto
 * se cifra (AES-GCM) y jamás vuelve en una respuesta. Re-registrar rota
 * la credencial.
 */
@Service
public class TiendaPasarelaService {

	private final TiendaPasarelaRepository credenciales;
	private final PasarelaPagoRepository pasarelas;
	private final TiendaRepository tiendas;
	private final TiendaUsuarioRepository tiendaUsuarios;
	private final CryptoService crypto;
	private final AuditoriaService auditoria;

	public TiendaPasarelaService(TiendaPasarelaRepository credenciales,
			PasarelaPagoRepository pasarelas, TiendaRepository tiendas,
			TiendaUsuarioRepository tiendaUsuarios, CryptoService crypto,
			AuditoriaService auditoria) {
		this.credenciales = credenciales;
		this.pasarelas = pasarelas;
		this.tiendas = tiendas;
		this.tiendaUsuarios = tiendaUsuarios;
		this.crypto = crypto;
		this.auditoria = auditoria;
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
			// Convención owner-incluye-admin: el OWNER conserva los poderes de admin.
			if (!TiendaUsuario.ADMIN.equals(rol) && !TiendaUsuario.STORE_OWNER.equals(rol)) {
				throw new ProhibidoException("solo el admin registra pasarelas");
			}
		}
		String modo = req.modo().trim();
		if (!TiendaPasarela.TEST.equals(modo)) {
			throw new ConflictoException("solo se admite modo de prueba; modo vivo está deshabilitado");
		}
		String codigo = req.codigo().trim().toLowerCase();
		String clavePublica = req.clavePublica().trim();
		String secreto = req.secreto().trim();
		if ("stripe".equals(codigo)) {
			validarCredencialesStripeTest(clavePublica, secreto);
		}
		Tienda tienda = tiendas.findById(tiendaId)
				.orElseThrow(() -> new NoEncontradoException("tienda"));
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
		cred.setClavePublica(clavePublica);
		cred.setSecretoCifrado(crypto.cifrar(secreto));
		cred.setModo(modo);
		cred.setActivo(true);
		credenciales.save(cred);
		auditoria.registrar("tienda_pasarelas", cred.getId(), "pasarela.registrar");
		return responder(cred);
	}

	private void validarCredencialesStripeTest(String clavePublica, String secreto) {
		int separador = secreto.indexOf(';');
		String claveSecreta = separador < 0 ? secreto : secreto.substring(0, separador).trim();
		if (!StripeTestKeys.esClaveApiSecretaTest(claveSecreta)) {
			throw new ConflictoException("Stripe requiere una llave secreta de prueba");
		}
		if (!StripeTestKeys.esClavePublicaTest(clavePublica)) {
			throw new ConflictoException("Stripe requiere una llave pública de prueba");
		}
		if (separador >= 0 && !StripeTestKeys.esSecretoWebhook(secreto.substring(separador + 1))) {
			throw new ConflictoException("secreto de webhook Stripe inválido");
		}
	}

	private PasarelaResponse responder(TiendaPasarela cred) {
		return new PasarelaResponse(cred.getId(), cred.getPasarela().getCodigo(),
				cred.getPasarela().getNombre(), cred.getModo(),
				Boolean.TRUE.equals(cred.getActivo()));
	}
}
