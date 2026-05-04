package co.uniquindio.tiendasana.services.admin;

import co.uniquindio.tiendasana.dto.admin.AdminProductRequest;
import co.uniquindio.tiendasana.dto.admin.AdminProductResponse;
import co.uniquindio.tiendasana.model.mongo.ProductoDocument;
import co.uniquindio.tiendasana.repos.mongo.ProductoDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminProductCatalogService {

    private final ProductoDocumentRepository productRepo;
    private final AdminProductLotService lotService;

    public List<AdminProductResponse> listAllForAdmin() {
        return productRepo.findAllByOrderByNombreAsc().stream().map(this::toResponse).toList();
    }

    public AdminProductResponse create(AdminProductRequest req) {
        int initialStock = req.initialStock() == null ? 0 : req.initialStock();
        boolean oos = req.outOfStock() != null && req.outOfStock() && initialStock <= 0;
        ProductoDocument d = ProductoDocument.builder()
                .nombre(req.name())
                .descripcion(req.description())
                .categoria(req.category())
                .imagen(req.imageUrl())
                .precioUnitario(req.price())
                .stockQuantity(0)
                .active(true)
                .outOfStock(oos)
                .build();
        ProductoDocument saved = productRepo.save(d);
        if (initialStock > 0) {
            lotService.registerOpeningStock(saved.getId(), initialStock);
            saved = productRepo.findById(saved.getId()).orElse(saved);
        }
        return toResponse(saved);
    }

    public AdminProductResponse update(String id, AdminProductRequest req) {
        ProductoDocument d = productRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + id));
        d.setNombre(req.name());
        d.setDescripcion(req.description());
        d.setCategoria(req.category());
        d.setImagen(req.imageUrl());
        d.setPrecioUnitario(req.price());
        if (req.outOfStock() != null) {
            d.setOutOfStock(req.outOfStock());
        }
        return toResponse(productRepo.save(d));
    }

    public void deactivate(String id) {
        ProductoDocument d = productRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + id));
        d.setActive(false);
        productRepo.save(d);
    }

    /** Alterna el flag {@code active} (baja lógica / reactivación). */
    public AdminProductResponse toggleActive(String id) {
        ProductoDocument d = productRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + id));
        d.setActive(!d.isActive());
        return toResponse(productRepo.save(d));
    }

    private AdminProductResponse toResponse(ProductoDocument d) {
        return new AdminProductResponse(
                d.getId(),
                d.getNombre(),
                d.getDescripcion(),
                d.getCategoria(),
                d.getPrecioUnitario(),
                d.getImagen(),
                d.getStockQuantity(),
                d.isActive(),
                d.isOutOfStock()
        );
    }
}
