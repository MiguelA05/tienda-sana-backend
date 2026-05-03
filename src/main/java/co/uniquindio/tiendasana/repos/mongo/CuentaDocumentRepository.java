package co.uniquindio.tiendasana.repos.mongo;

import co.uniquindio.tiendasana.model.mongo.CuentaDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CuentaDocumentRepository extends MongoRepository<CuentaDocument, String> {

    @Query("{ 'email': ?0 }")
    List<CuentaDocument> findByEmail(String email);
}
