package com.tienda.plataforma.auth.dto;

public record ChallengeResponse(boolean requiereTotp, boolean totpEnrolado) {
}
