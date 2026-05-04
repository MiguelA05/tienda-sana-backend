package co.uniquindio.tiendasana.services.admin;

import co.uniquindio.tiendasana.model.mongo.InventoryTransactionDocument;
import co.uniquindio.tiendasana.model.mongo.InventoryTransactionTypes;
import co.uniquindio.tiendasana.model.mongo.ProductLotDocument;
import co.uniquindio.tiendasana.repos.mongo.InventoryTransactionDocumentRepository;
import co.uniquindio.tiendasana.repos.mongo.ProductLotDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryTransactionService {

    private final InventoryTransactionDocumentRepository repo;
    private final ProductLotDocumentRepository lotRepo;
    private final MongoTemplate mongo;

    public InventoryTransactionDocument createTransaction(
            String productId,
            String type,
            int quantity,
            String referenceId,
            String createdBy,
            String reason) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId es obligatorio en el ledger de inventario");
        }
        InventoryTransactionDocument t = InventoryTransactionDocument.builder()
                .productId(productId)
                .type(type)
                .quantity(quantity)
                .referenceId(referenceId)
                .createdAt(LocalDateTime.now())
                .createdBy(createdBy != null ? createdBy : "system")
                .reason(reason)
                .build();
        return repo.save(t);
    }

    /**
     * Descuenta stock por venta asignando unidades a lotes en orden FIFO (por fecha de ingreso).
     * Si no hay capacidad en lotes activos, el resto se registra como SALE con {@code saleReference} como referencia.
     */
    public void recordFifoSale(String productId, int quantity, String saleReference, String createdBy) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad vendida debe ser mayor a cero");
        }
        int left = quantity;
        List<ProductLotDocument> lots = lotRepo.findByProductIdAndVoidedFalseOrderByEntryDateAsc(productId);
        for (ProductLotDocument lot : lots) {
            int bucket = sumByReference(lot.getId());
            if (bucket <= 0) {
                continue;
            }
            int take = Math.min(bucket, left);
            if (take <= 0) {
                continue;
            }
            createTransaction(
                    productId,
                    InventoryTransactionTypes.SALE,
                    -take,
                    lot.getId(),
                    createdBy,
                    "Venta " + saleReference);
            left -= take;
            if (left == 0) {
                return;
            }
        }
        if (left > 0) {
            createTransaction(
                    productId,
                    InventoryTransactionTypes.SALE,
                    -left,
                    saleReference,
                    createdBy,
                    "Venta (sin lote FIFO) " + saleReference);
        }
    }

    /**
     * Salida por ajuste manual repartiendo en FIFO por buckets de lote (misma lógica que venta, pero tipo ADJUSTMENT).
     * El remanente sin capacidad en lotes se descuenta con referencia nula (solo afecta stock total del producto).
     */
    public void recordFifoAdjustmentOut(String productId, int quantity, String reason, String createdBy) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero");
        }
        int left = quantity;
        List<ProductLotDocument> lots = lotRepo.findByProductIdAndVoidedFalseOrderByEntryDateAsc(productId);
        for (ProductLotDocument lot : lots) {
            int bucket = sumByReference(lot.getId());
            if (bucket <= 0) {
                continue;
            }
            int take = Math.min(bucket, left);
            if (take <= 0) {
                continue;
            }
            createTransaction(
                    productId,
                    InventoryTransactionTypes.ADJUSTMENT,
                    -take,
                    lot.getId(),
                    createdBy,
                    reason);
            left -= take;
            if (left == 0) {
                return;
            }
        }
        if (left > 0) {
            createTransaction(
                    productId,
                    InventoryTransactionTypes.ADJUSTMENT,
                    -left,
                    "ADJ-OUT:" + productId + ":" + UUID.randomUUID(),
                    createdBy,
                    reason + " (parte sin asignar a lote con saldo)");
        }
    }

    /** Repone stock tras reembolso (no revierte asignación FIFO; ajuste a nivel producto). */
    public void recordRefund(String productId, int quantity, String saleReference, String createdBy) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad a reponer debe ser mayor a cero");
        }
        createTransaction(
                productId,
                InventoryTransactionTypes.ADJUSTMENT,
                quantity,
                saleReference,
                createdBy,
                "Reembolso venta " + saleReference);
    }

    public boolean hasSaleForLotReference(String lotId) {
        return repo.existsByReferenceIdAndType(lotId, InventoryTransactionTypes.SALE);
    }

    /**
     * Suma transacciones con {@code productId} del producto más movimientos huérfanos (sin {@code productId}
     * usable) cuya {@code referenceId} coincida con cualquier referencia ya usada en otra transacción de ese
     * producto. Así, si se elimina el documento del lote en Mongo, las transacciones históricas con ese
     * {@code referenceId} siguen contabilizando y no aparece stock negativo ficticio.
     */
    public int sumByProduct(String productId) {
        if (productId == null || productId.isBlank()) {
            return 0;
        }
        int direct = sumQuantities(Query.query(Criteria.where("productId").is(productId)));

        Query cohortQuery = Query.query(Criteria.where("productId").is(productId));
        cohortQuery.fields().include("referenceId");
        Set<String> cohortRefs = new HashSet<>();
        for (InventoryTransactionDocument d : mongo.find(cohortQuery, InventoryTransactionDocument.class)) {
            if (d.getReferenceId() != null && !d.getReferenceId().isBlank()) {
                cohortRefs.add(d.getReferenceId());
            }
        }

        int fromOrphanRefs = 0;
        if (!cohortRefs.isEmpty()) {
            Criteria missingProduct = new Criteria()
                    .orOperator(
                            Criteria.where("productId").exists(false),
                            Criteria.where("productId").is(null),
                            Criteria.where("productId").is(""));
            Criteria orphan = new Criteria().andOperator(missingProduct, Criteria.where("referenceId").in(cohortRefs));
            fromOrphanRefs = sumQuantities(Query.query(orphan));
        }
        return direct + fromOrphanRefs;
    }

    /**
     * Asigna {@code productId} a transacciones huérfanas con ese {@code referenceId} (p. ej. tras eliminar el lote
     * del catálogo pero manteniendo el historial inmutable).
     */
    public void repairProductIdForReferenceId(String referenceId, String productId) {
        if (referenceId == null || referenceId.isBlank() || productId == null || productId.isBlank()) {
            return;
        }
        Query q = new Query(new Criteria().andOperator(
                Criteria.where("referenceId").is(referenceId),
                new Criteria()
                        .orOperator(
                                Criteria.where("productId").exists(false),
                                Criteria.where("productId").is(null),
                                Criteria.where("productId").is(""))));
        mongo.updateMulti(q, new Update().set("productId", productId), InventoryTransactionDocument.class);
    }

    private int sumQuantities(Query query) {
        return mongo.find(query, InventoryTransactionDocument.class).stream()
                .mapToInt(InventoryTransactionDocument::getQuantity)
                .sum();
    }

    public int sumByReference(String referenceId) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(org.springframework.data.mongodb.core.query.Criteria.where("referenceId").is(referenceId)),
                Aggregation.group("referenceId").sum("quantity").as("total"));
        AggregationResults<org.bson.Document> res = mongo.aggregate(agg, "inventory_transactions", org.bson.Document.class);
        List<org.bson.Document> mapped = res.getMappedResults();
        if (mapped.isEmpty()) {
            return 0;
        }
        Object val = mapped.get(0).get("total");
        return (val instanceof Number) ? ((Number) val).intValue() : 0;
    }

    public List<InventoryTransactionDocument> findByReference(String referenceId) {
        return repo.findByReferenceId(referenceId);
    }

    public List<InventoryTransactionDocument> findByProduct(String productId) {
        return repo.findByProductId(productId);
    }
}
