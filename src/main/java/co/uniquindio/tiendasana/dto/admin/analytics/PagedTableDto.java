package co.uniquindio.tiendasana.dto.admin.analytics;

import java.util.List;

public record PagedTableDto<T>(List<T> rows, long totalElements, int page, int size, int totalPages) {
}
