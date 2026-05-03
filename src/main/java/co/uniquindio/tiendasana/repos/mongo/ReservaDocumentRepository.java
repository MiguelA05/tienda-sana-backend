package co.uniquindio.tiendasana.repos.mongo;

import co.uniquindio.tiendasana.model.mongo.ReservaDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservaDocumentRepository extends MongoRepository<ReservaDocument, String> {

    /** Reservas cuya fecha de creación cae en el rango (actividad registrada). */
    List<ReservaDocument> findByFechaCreacionBetween(LocalDateTime from, LocalDateTime to);

    /** Reservas con fecha de servicio en el rango (calendario). */
    @Query("{ 'fechaReserva': { $gte: ?0, $lte: ?1 } }")
    List<ReservaDocument> findByFechaReservaBetweenInclusive(LocalDateTime from, LocalDateTime to);

    /**
     * Reservas relevantes en el periodo: creadas en rango o con fecha de reserva en rango.
     */
    @Query("{ $or: [ { 'fechaCreacion': { $gte: ?0, $lte: ?1 } }, { 'fechaReserva': { $gte: ?0, $lte: ?1 } } ] }")
    List<ReservaDocument> findTouchingPeriod(LocalDateTime from, LocalDateTime to);
}
