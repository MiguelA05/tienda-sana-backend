package co.uniquindio.tiendasana.repos.mongo;

import co.uniquindio.tiendasana.model.mongo.VentaProductoDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface VentaProductoDocumentRepository extends MongoRepository<VentaProductoDocument, String> {

    @Query("{ 'fecha': { $gte: ?0, $lte: ?1 } }")
    List<VentaProductoDocument> findByFechaBetweenInclusive(LocalDateTime from, LocalDateTime to);
}
