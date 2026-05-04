package co.uniquindio.tiendasana.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "inventory_transactions")
public class InventoryTransactionDocument {

    @Id
    private String id;

    private String productId;

    private String type; // ENTRY | SALE | ADJUSTMENT

    private int quantity;

    private String referenceId; // optional: lot id, order id, etc.

    private LocalDateTime createdAt;

    private String createdBy;

    private String reason;
}
