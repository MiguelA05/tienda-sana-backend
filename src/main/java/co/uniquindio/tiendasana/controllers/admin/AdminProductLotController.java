package co.uniquindio.tiendasana.controllers.admin;

import co.uniquindio.tiendasana.dto.admin.DeleteLotResultDTO;
import co.uniquindio.tiendasana.dto.admin.InventoryAdjustmentRequest;
import co.uniquindio.tiendasana.dto.admin.InventoryResponse;
import co.uniquindio.tiendasana.dto.admin.ProductLotRequest;
import co.uniquindio.tiendasana.dto.admin.ProductLotResponse;
import co.uniquindio.tiendasana.dto.jwtdtos.MessageDTO;
import co.uniquindio.tiendasana.services.admin.AdminProductLotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductLotController {

    private final AdminProductLotService lotService;

    @GetMapping("/lots")
    public ResponseEntity<MessageDTO<List<ProductLotResponse>>> listLots(
            @RequestParam(required = false) String productId) {
        return ResponseEntity.ok(new MessageDTO<>(false, lotService.listAll(productId)));
    }

    @PostMapping("/lots")
    public ResponseEntity<MessageDTO<ProductLotResponse>> create(@Valid @RequestBody ProductLotRequest request) {
        return ResponseEntity.ok(new MessageDTO<>(false, lotService.create(request)));
    }

    @PutMapping("/lots/{id}")
    public ResponseEntity<MessageDTO<ProductLotResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody ProductLotRequest request) {
        return ResponseEntity.ok(new MessageDTO<>(false, lotService.update(id, request)));
    }

    @DeleteMapping("/lots/{id}")
    public ResponseEntity<MessageDTO<DeleteLotResultDTO>> deleteLot(@PathVariable String id) {
        return ResponseEntity.ok(new MessageDTO<>(false, lotService.deleteLot(id)));
    }

    @PostMapping("/inventory/adjustment")
    public ResponseEntity<MessageDTO<String>> adjustInventory(
            @Valid @RequestBody InventoryAdjustmentRequest request, Authentication authentication) {
        String user = authentication != null && authentication.getName() != null ? authentication.getName() : "admin";
        lotService.adjustInventory(request, user);
        return ResponseEntity.ok(new MessageDTO<>(false, "Ajuste registrado"));
    }

    @GetMapping("/inventory")
    public ResponseEntity<MessageDTO<List<InventoryResponse>>> inventory() {
        return ResponseEntity.ok(new MessageDTO<>(false, lotService.inventory()));
    }
}
