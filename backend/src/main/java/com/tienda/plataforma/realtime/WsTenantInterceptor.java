package com.tienda.plataforma.realtime;

import java.util.List;
import java.util.Map;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;

/**
 * Solo salas /topic/tienda/{id} y solo miembros (admin/operador de la tienda).
 * super_admin NO entra a salas de tiendas (RF-TENANT-03).
 */
@Component
public class WsTenantInterceptor implements ChannelInterceptor {

	private final TiendaRepository tiendas;
	private final TiendaUsuarioRepository tiendaUsuarios;
	private final ClienteRepository clientes;

	public WsTenantInterceptor(TiendaRepository tiendas, TiendaUsuarioRepository tiendaUsuarios,
			ClienteRepository clientes) {
		this.tiendas = tiendas;
		this.tiendaUsuarios = tiendaUsuarios;
		this.clientes = clientes;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor header = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (header == null || header.getCommand() != StompCommand.SUBSCRIBE) {
			return message;
		}
		String destino = header.getDestination();
		if (destino == null || !destino.startsWith("/topic/tienda/")) {
			return null;
		}
		Map<String, Object> sesion = header.getSessionAttributes();
		if (sesion == null || !(sesion.get("usuarioId") instanceof Long usuarioId)) {
			return null;
		}
		List<String> roles = (List<String>) sesion.getOrDefault("roles", List.of());
		if (roles.contains("super_admin")) {
			return null;
		}
		Long tiendaId;
		try {
			tiendaId = Long.valueOf(destino.substring("/topic/tienda/".length()));
		} catch (NumberFormatException e) {
			return null;
		}
		boolean miembro = tiendaUsuarios.findByTiendaIdAndUsuarioId(tiendaId, usuarioId).isPresent();
		return miembro ? message : null;
	}
}
