package co.uniquindio.tiendasana.dto.admin;

/**
 * Resultado de eliminar/anular un lote para que el cliente pueda ramificar UI.
 */
public record DeleteLotResultDTO(String code, String message) {

    public static final String DELETED = "DELETED";
    public static final String VOIDED_WITH_ADJUSTMENT = "VOIDED_WITH_ADJUSTMENT";
    public static final String VOIDED_CONSUMED_LOT = "VOIDED_CONSUMED_LOT";
    public static final String ALREADY_VOIDED = "ALREADY_VOIDED";
}
