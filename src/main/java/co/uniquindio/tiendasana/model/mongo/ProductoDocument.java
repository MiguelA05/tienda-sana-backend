package co.uniquindio.tiendasana.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Catálogo de productos persistido en MongoDB.
 * El stock ({@code stockQuantity}) es caché derivado de la suma de {@code inventory_transactions}
 * para el producto; la fuente de verdad es el ledger de inventario.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "products")
public class ProductoDocument {

    @Id
    private String id;

    @Indexed
    private String nombre;

    private String descripcion;
    private String categoria;
    private String imagen;
    private double precioUnitario;

    /** Inventario agregado (sincronizado con la suma de lotes al crear/editar lotes). */
    @Builder.Default
    private int stockQuantity = 0;

    /** false: producto dado de baja en catálogo admin (no visible al cliente). */
    @Builder.Default
    @Indexed
    private boolean active = true;

    /**
     * Marca comercial de agotado: el cliente no debe ver el producto aunque haya stock,
     * salvo que se desactive explícitamente desde admin.
     */
    @Builder.Default
    private boolean outOfStock = false;

    @Builder.Default
    private int calificacionPromedio = 0;
}
