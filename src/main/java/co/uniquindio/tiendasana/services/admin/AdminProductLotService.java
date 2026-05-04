package co.uniquindio.tiendasana.services.admin;

import co.uniquindio.tiendasana.dto.admin.DeleteLotResultDTO;
import co.uniquindio.tiendasana.dto.admin.InventoryAdjustmentRequest;
import co.uniquindio.tiendasana.dto.admin.InventoryResponse;
import co.uniquindio.tiendasana.dto.admin.ProductLotRequest;
import co.uniquindio.tiendasana.dto.admin.ProductLotResponse;
import co.uniquindio.tiendasana.model.mongo.InventoryTransactionTypes;
import co.uniquindio.tiendasana.model.mongo.ProductLotDocument;
import co.uniquindio.tiendasana.model.mongo.ProductoDocument;
import co.uniquindio.tiendasana.repos.mongo.ProductLotDocumentRepository;
import co.uniquindio.tiendasana.repos.mongo.ProductoDocumentRepository;
import co.uniquindio.tiendasana.repos.mongo.SupplierDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminProductLotService {

    public static final String OPENING_STOCK_SUPPLIER_ID = "__OPENING_STOCK__";

    /** Lote sintético para entradas de ajuste sin proveedor (visible en tabla de lotes). */
    public static final String ADJUSTMENT_SUPPLIER_ID = "__ADJUSTMENT__";

    private final ProductLotDocumentRepository lotRepo;
    private final ProductoDocumentRepository productRepo;
    private final SupplierDocumentRepository supplierRepo;
    private final InventoryTransactionService transactionService;

    public List<ProductLotResponse> listAll(String productIdFilter) {
        List<ProductLotDocument> list = (productIdFilter == null || productIdFilter.isBlank())
                ? lotRepo.findAllByOrderByEntryDateDesc()
                : lotRepo.findByProductIdOrderByEntryDateDesc(productIdFilter);
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ProductLotResponse create(ProductLotRequest req) {
        ProductoDocument product = productRepo.findById(req.productId())
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + req.productId()));
        return createLot(product, req.supplierId(), req.entryDate(), req.quantity(), req.unitValue(), "admin", "Ingreso por lote");
    }

    @Transactional
    public ProductLotResponse registerOpeningStock(String productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad inicial debe ser mayor a cero");
        }
        ProductoDocument product = productRepo.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + productId));
        return createLot(product, OPENING_STOCK_SUPPLIER_ID, LocalDateTime.now(), quantity, 0.0, "system", "Inventario inicial");
    }

    /**
     * Solo si el lote no tiene consumo (ninguna unidad ha salido del bucket del lote).
     * Los cambios de cantidad se reflejan como {@code ADJUSTMENT} sobre el mismo {@code referenceId} del lote.
     */
    @Transactional
    public ProductLotResponse update(String id, ProductLotRequest req) {
        ProductLotDocument lot = lotRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado: " + id));
        if (lot.isVoided()) {
            throw new IllegalArgumentException("No se puede editar un lote anulado");
        }
        if (OPENING_STOCK_SUPPLIER_ID.equals(lot.getSupplierId())) {
            throw new IllegalArgumentException("El inventario inicial no se puede modificar desde inventario");
        }
        if (!lot.getProductId().equals(req.productId())) {
            throw new IllegalArgumentException("No se permite cambiar el producto de un lote existente");
        }

        int remaining = transactionService.sumByReference(lot.getId());
        boolean hasConsumption = lot.getQuantity() > remaining;
        if (hasConsumption) {
            throw new IllegalArgumentException(
                    "Este lote ya ha sido utilizado y no puede editarse. Para corregir inventario use un ajuste.");
        }

        ProductoDocument product = productRepo.findById(lot.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + lot.getProductId()));

        if (!OPENING_STOCK_SUPPLIER_ID.equals(req.supplierId())) {
            supplierRepo.findById(req.supplierId())
                    .orElseThrow(() -> new IllegalArgumentException("Proveedor no encontrado: " + req.supplierId()));
        }

        int oldQty = lot.getQuantity();
        int newQty = req.quantity();
        int delta = newQty - oldQty;

        lot.setSupplierId(req.supplierId());
        lot.setEntryDate(req.entryDate());
        lot.setUnitValue(req.unitValue());
        lot.setQuantity(newQty);
        lotRepo.save(lot);

        if (delta != 0) {
            transactionService.createTransaction(
                    product.getId(),
                    InventoryTransactionTypes.ADJUSTMENT,
                    delta,
                    lot.getId(),
                    "admin",
                    "Corrección de cantidad inicial del lote");
        }

        syncProductCachedStock(product.getId());
        return toResponse(lotRepo.findById(lot.getId()).orElse(lot));
    }

    @Transactional
    public DeleteLotResultDTO deleteLot(String id) {
        ProductLotDocument lot = lotRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado: " + id));
        if (lot.isVoided()) {
            return new DeleteLotResultDTO(DeleteLotResultDTO.ALREADY_VOIDED, "El lote ya estaba anulado");
        }
        if (OPENING_STOCK_SUPPLIER_ID.equals(lot.getSupplierId())) {
            throw new IllegalArgumentException("El inventario inicial no se puede eliminar desde inventario");
        }
        ProductoDocument product = productRepo.findById(lot.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + lot.getProductId()));

        transactionService.repairProductIdForReferenceId(lot.getId(), product.getId());

        int remaining = Math.max(0, transactionService.sumByReference(lot.getId()));
        boolean hasSales = transactionService.hasSaleForLotReference(lot.getId());

        if (hasSales) {
            if (remaining > 0) {
                transactionService.createTransaction(
                        product.getId(),
                        InventoryTransactionTypes.ADJUSTMENT,
                        -remaining,
                        lot.getId(),
                        "admin",
                        "Anulación de lote (stock restante)");
            }
            lot.setVoided(true);
            lotRepo.save(lot);
            syncProductCachedStock(product.getId());
            return remaining > 0
                    ? new DeleteLotResultDTO(
                            DeleteLotResultDTO.VOIDED_WITH_ADJUSTMENT,
                            "El lote ya tenía consumo. Se registró un ajuste compensatorio y el lote quedó anulado.")
                    : new DeleteLotResultDTO(
                            DeleteLotResultDTO.VOIDED_CONSUMED_LOT,
                            "El lote estaba totalmente consumido y se marcó como anulado.");
        }

        if (remaining > 0) {
            transactionService.createTransaction(
                    product.getId(),
                    InventoryTransactionTypes.ADJUSTMENT,
                    -remaining,
                    lot.getId(),
                    "admin",
                    "Eliminación de lote sin ventas asociadas");
        }
        lotRepo.deleteById(lot.getId());
        syncProductCachedStock(product.getId());
        return new DeleteLotResultDTO(DeleteLotResultDTO.DELETED, "Lote eliminado");
    }

    @Transactional
    public void adjustInventory(InventoryAdjustmentRequest req, String createdBy) {
        ProductoDocument product = productRepo.findById(req.productId())
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + req.productId()));
        String actor = createdBy != null ? createdBy : "admin";
        String targetLotId = req.targetLotId() == null || req.targetLotId().isBlank() ? null : req.targetLotId().trim();

        if (req.direction() == InventoryAdjustmentRequest.Direction.IN) {
            if (targetLotId != null) {
                ProductLotDocument lot = lotRepo.findById(targetLotId)
                        .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado: " + targetLotId));
                if (lot.isVoided()) {
                    throw new IllegalArgumentException("No se puede ajustar un lote anulado");
                }
                if (!lot.getProductId().equals(product.getId())) {
                    throw new IllegalArgumentException("El lote no pertenece al producto seleccionado");
                }
                transactionService.createTransaction(
                        product.getId(),
                        InventoryTransactionTypes.ADJUSTMENT,
                        req.quantity(),
                        lot.getId(),
                        actor,
                        req.reason());
            } else {
                createLot(
                        product,
                        ADJUSTMENT_SUPPLIER_ID,
                        LocalDateTime.now(),
                        req.quantity(),
                        0.0,
                        actor,
                        req.reason());
                return;
            }
        } else {
            int available = transactionService.sumByProduct(product.getId());
            if (req.quantity() > available) {
                throw new IllegalArgumentException(
                        "Stock insuficiente: hay " + available + " unidades disponibles en inventario");
            }
            if (targetLotId != null) {
                ProductLotDocument lot = lotRepo.findById(targetLotId)
                        .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado: " + targetLotId));
                if (lot.isVoided()) {
                    throw new IllegalArgumentException("No se puede ajustar un lote anulado");
                }
                if (!lot.getProductId().equals(product.getId())) {
                    throw new IllegalArgumentException("El lote no pertenece al producto seleccionado");
                }
                int inLot = Math.max(0, transactionService.sumByReference(lot.getId()));
                if (req.quantity() > inLot) {
                    throw new IllegalArgumentException(
                            "En el lote seleccionado solo hay " + inLot + " unidades disponibles");
                }
                transactionService.createTransaction(
                        product.getId(),
                        InventoryTransactionTypes.ADJUSTMENT,
                        -req.quantity(),
                        lot.getId(),
                        actor,
                        req.reason());
            } else {
                transactionService.recordFifoAdjustmentOut(product.getId(), req.quantity(), req.reason(), actor);
            }
        }
        syncProductCachedStock(product.getId());
    }

    public List<InventoryResponse> inventory() {
        return productRepo.findAllByOrderByNombreAsc().stream()
                .map(p -> new InventoryResponse(p.getId(), p.getNombre(), transactionService.sumByProduct(p.getId())))
                .toList();
    }

    private void syncProductCachedStock(String productId) {
        ProductoDocument p = productRepo.findById(productId).orElse(null);
        if (p == null) {
            return;
        }
        p.setStockQuantity(transactionService.sumByProduct(productId));
        productRepo.save(p);
    }

    private ProductLotResponse toResponse(ProductLotDocument l) {
        int remainingRaw = transactionService.sumByReference(l.getId());
        int remaining = Math.max(0, remainingRaw);
        int cappedForConsumed = Math.min(remaining, l.getQuantity());
        int consumed = Math.max(0, l.getQuantity() - cappedForConsumed);
        String status;
        if (l.isVoided()) {
            status = "ANULADO";
        } else if (remaining <= 0) {
            status = "CONSUMIDO";
        } else {
            status = "ACTIVO";
        }
        return new ProductLotResponse(
                l.getId(),
                l.getProductId(),
                l.getSupplierId(),
                l.getEntryDate(),
                l.getQuantity(),
                l.getUnitValue(),
                remaining,
                consumed,
                status,
                l.isVoided());
    }

    private ProductLotResponse createLot(
            ProductoDocument product,
            String supplierId,
            LocalDateTime entryDate,
            int quantity,
            double unitValue,
            String createdBy,
            String entryReason) {
        if (!OPENING_STOCK_SUPPLIER_ID.equals(supplierId) && !ADJUSTMENT_SUPPLIER_ID.equals(supplierId)) {
            supplierRepo.findById(supplierId)
                    .orElseThrow(() -> new IllegalArgumentException("Proveedor no encontrado: " + supplierId));
        }

        ProductLotDocument lot = ProductLotDocument.builder()
                .productId(product.getId())
                .supplierId(supplierId)
                .entryDate(entryDate)
                .quantity(quantity)
                .voided(false)
                .unitValue(unitValue)
                .build();
        lot = lotRepo.save(lot);

        transactionService.createTransaction(
                product.getId(),
                InventoryTransactionTypes.ENTRY,
                quantity,
                lot.getId(),
                createdBy,
                entryReason != null && !entryReason.isBlank() ? entryReason : "Ingreso por lote");

        syncProductCachedStock(product.getId());
        return toResponse(lot);
    }
}
