package co.uniquindio.tiendasana.scripts;

import co.uniquindio.tiendasana.model.mongo.InventoryTransactionTypes;
import co.uniquindio.tiendasana.model.mongo.ProductLotDocument;
import co.uniquindio.tiendasana.model.mongo.ProductoDocument;
import co.uniquindio.tiendasana.repos.mongo.InventoryTransactionDocumentRepository;
import co.uniquindio.tiendasana.repos.mongo.ProductLotDocumentRepository;
import co.uniquindio.tiendasana.repos.mongo.ProductoDocumentRepository;
import co.uniquindio.tiendasana.services.admin.InventoryTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Migra lotes existentes a transacciones ENTRY y reconcilia {@code ProductoDocument.stockQuantity}
 * con la suma del ledger. Activar con {@code tiendasana.inventory.migration.run-on-startup=true}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "tiendasana.inventory.migration", name = "run-on-startup", havingValue = "true")
public class InventoryTransactionMigration {

    private final InventoryTransactionDocumentRepository txRepo;
    private final ProductLotDocumentRepository lotRepo;
    private final ProductoDocumentRepository productRepo;
    private final InventoryTransactionService txService;

    @EventListener(ApplicationReadyEvent.class)
    public void runMigration() {
        long count = txRepo.count();
        if (count > 0) {
            log.info("Migración de inventario omitida: ya existen {} transacciones", count);
            return;
        }
        List<ProductLotDocument> lots = lotRepo.findAll();
        for (ProductLotDocument l : lots) {
            if (l.isVoided() || l.getQuantity() <= 0) {
                continue;
            }
            txService.createTransaction(
                    l.getProductId(),
                    InventoryTransactionTypes.ENTRY,
                    l.getQuantity(),
                    l.getId(),
                    "migration",
                    "Migrado desde lote existente");
        }
        for (ProductoDocument p : productRepo.findAll()) {
            int ledger = txService.sumByProduct(p.getId());
            int delta = p.getStockQuantity() - ledger;
            if (delta != 0) {
                txService.createTransaction(
                        p.getId(),
                        InventoryTransactionTypes.ADJUSTMENT,
                        delta,
                        null,
                        "migration",
                        "Reconciliación post-migración (stock previo vs suma de transacciones)");
            }
            p.setStockQuantity(txService.sumByProduct(p.getId()));
            productRepo.save(p);
        }
        log.info("Migración de inventario completada: {} lotes procesados", lots.size());
    }
}
