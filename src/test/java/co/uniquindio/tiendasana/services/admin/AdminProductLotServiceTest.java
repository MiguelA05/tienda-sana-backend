package co.uniquindio.tiendasana.services.admin;

import co.uniquindio.tiendasana.model.mongo.ProductLotDocument;
import co.uniquindio.tiendasana.model.mongo.ProductoDocument;
import co.uniquindio.tiendasana.repos.mongo.ProductLotDocumentRepository;
import co.uniquindio.tiendasana.repos.mongo.ProductoDocumentRepository;
import co.uniquindio.tiendasana.repos.mongo.SupplierDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminProductLotServiceTest {

    @Mock
    private ProductLotDocumentRepository lotRepo;

    @Mock
    private ProductoDocumentRepository productRepo;

    @Mock
    private SupplierDocumentRepository supplierRepo;

    @Mock
    private co.uniquindio.tiendasana.services.admin.InventoryTransactionService transactionService;

    @InjectMocks
    private AdminProductLotService service;

    @Test
    void registerOpeningStockCreatesInternalLotAndUpdatesStock() {
        ProductoDocument product = ProductoDocument.builder()
                .id("p1")
                .nombre("Arepa")
                .stockQuantity(0)
                .active(true)
                .outOfStock(false)
                .build();

        when(productRepo.findById("p1")).thenReturn(Optional.of(product));
        when(lotRepo.save(any())).thenAnswer(invocation -> {
            ProductLotDocument l = invocation.getArgument(0);
            l.setId("lot1");
            return l;
        });
        when(transactionService.sumByProduct("p1")).thenReturn(9);
        when(transactionService.sumByReference("lot1")).thenReturn(9);

        var response = service.registerOpeningStock("p1", 9);

        assertEquals(AdminProductLotService.OPENING_STOCK_SUPPLIER_ID, response.supplierId());
        assertEquals(0.0, response.unitValue());
        assertEquals(9, product.getStockQuantity());
        assertEquals(9, response.initialQuantity());
        verify(transactionService)
            .createTransaction(eq("p1"), eq("ENTRY"), eq(9), any(), eq("system"), eq("Inventario inicial"));
    }
}