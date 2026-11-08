package com.tienda.plataforma.auth.dto;

public record RegistroResponse(Long usuarioId, String email, boolean requiereEnrolamientoTotp) {
}
