package co.uniquindio.tiendasana.repos.mongo;

import co.uniquindio.tiendasana.model.mongo.InventoryTransactionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface InventoryTransactionDocumentRepository extends MongoRepository<InventoryTransactionDocument, String> {
    List<InventoryTransactionDocument> findByProductId(String productId);
    List<InventoryTransactionDocument> findByReferenceId(String referenceId);

    boolean existsByReferenceIdAndType(String referenceId, String type);
}
