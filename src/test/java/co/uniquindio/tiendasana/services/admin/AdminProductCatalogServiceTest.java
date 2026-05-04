package co.uniquindio.tiendasana.services.admin;

import co.uniquindio.tiendasana.dto.admin.AdminProductRequest;
import co.uniquindio.tiendasana.model.mongo.ProductoDocument;
import co.uniquindio.tiendasana.repos.mongo.ProductoDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminProductCatalogServiceTest {

    @Mock
    private ProductoDocumentRepository productRepo;

    @Mock
    private AdminProductLotService lotService;

    @InjectMocks
    private AdminProductCatalogService service;

    @Test
    void createWithInitialStockCreatesOpeningLot() {
        ProductoDocument saved = ProductoDocument.builder()
                .id("p1")
                .nombre("Café")
                .descripcion("Molido")
                .categoria("Bebidas")
                .imagen("https://img.test/cafe.png")
                .precioUnitario(12000)
                .stockQuantity(0)
                .active(true)
                .outOfStock(false)
                .build();

        when(productRepo.save(any())).thenReturn(saved);
        when(productRepo.findById("p1")).thenReturn(Optional.of(saved));
        doAnswer(invocation -> {
                        saved.setStockQuantity(invocation.getArgument(1, Integer.class));
            return null;
        }).when(lotService).registerOpeningStock("p1", 7);

        var response = service.create(new AdminProductRequest(
                "Café",
                "Molido",
                "Bebidas",
                12000d,
                "https://img.test/cafe.png",
                false,
                7
        ));

        assertEquals(7, response.stockQuantity());
        verify(lotService).registerOpeningStock("p1", 7);
    }

    @Test
    void createWithoutInitialStockDoesNotCreateOpeningLot() {
        ProductoDocument saved = ProductoDocument.builder()
                .id("p2")
                .nombre("Té")
                .descripcion("Verde")
                .categoria("Bebidas")
                .imagen("https://img.test/te.png")
                .precioUnitario(8000)
                .stockQuantity(0)
                .active(true)
                .outOfStock(false)
                .build();

        when(productRepo.save(any())).thenReturn(saved);

        var response = service.create(new AdminProductRequest(
                "Té",
                "Verde",
                "Bebidas",
                8000d,
                "https://img.test/te.png",
                false,
                0
        ));

        assertEquals(0, response.stockQuantity());
                verify(lotService, never()).registerOpeningStock(any(), anyInt());
    }
}