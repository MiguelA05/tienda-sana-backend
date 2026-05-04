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
        when(lotRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.registerOpeningStock("p1", 9);

        ArgumentCaptor<ProductLotDocument> captor = ArgumentCaptor.forClass(ProductLotDocument.class);
        verify(lotRepo).save(captor.capture());
        assertEquals(AdminProductLotService.OPENING_STOCK_SUPPLIER_ID, captor.getValue().getSupplierId());
        assertEquals(0.0, response.unitValue());
        assertEquals(9, product.getStockQuantity());
        verify(productRepo).save(product);
    }
}