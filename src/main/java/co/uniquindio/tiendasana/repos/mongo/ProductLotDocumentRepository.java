package co.uniquindio.tiendasana.repos.mongo;

import co.uniquindio.tiendasana.model.mongo.ProductLotDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProductLotDocumentRepository extends MongoRepository<ProductLotDocument, String> {

    List<ProductLotDocument> findAllByOrderByEntryDateDesc();

    List<ProductLotDocument> findByProductIdOrderByEntryDateDesc(String productId);

    List<ProductLotDocument> findByProductIdAndVoidedFalseOrderByEntryDateAsc(String productId);
}
