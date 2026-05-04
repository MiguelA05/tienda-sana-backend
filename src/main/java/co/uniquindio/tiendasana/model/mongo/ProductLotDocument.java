package co.uniquindio.tiendasana.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "product_lots")
@CompoundIndex(name = "idx_lot_product", def = "{'productId': 1}")
@CompoundIndex(name = "idx_lot_supplier", def = "{'supplierId': 1}")
public class ProductLotDocument {

    @Id
    private String id;

    @Indexed
    private String productId;

    @Indexed
    private String supplierId;

    private LocalDateTime entryDate;

    private int quantity;

    /** Si true, el lote no participa en FIFO ni en altas; queda solo para auditoría. */
    @Builder.Default
    private boolean voided = false;

    private double unitValue;
}
