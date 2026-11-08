package com.tienda.plataforma.auth.dto;

import java.util.List;

public record TokensResponse(String accessToken, List<String> roles) {
}
