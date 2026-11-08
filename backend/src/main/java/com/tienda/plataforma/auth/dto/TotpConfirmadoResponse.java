package com.tienda.plataforma.auth.dto;

import java.util.List;

public record TotpConfirmadoResponse(boolean habilitado, List<String> backupCodes) {
}
