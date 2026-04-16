package co.uniquindio.tiendasana;

import co.uniquindio.tiendasana.dto.productodtos.FiltroProductoDTO;
import co.uniquindio.tiendasana.dto.productodtos.ProductoInfoDTO;
import co.uniquindio.tiendasana.exceptions.ProductoParseException;
import co.uniquindio.tiendasana.model.documents.Producto;
import co.uniquindio.tiendasana.model.mongo.ProductoDocument;
import co.uniquindio.tiendasana.repos.mongo.ProductoDocumentRepository;
import co.uniquindio.tiendasana.services.implementations.ProductoServiceImp;
import co.uniquindio.tiendasana.services.mongo.ProductCatalogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ServicioProductoTest {

	@Mock
	ProductoDocumentRepository productDocumentRepo;

	@Mock
	ProductCatalogMapper catalogMapper;

	@InjectMocks
	ProductoServiceImp productoService;

	@Test
	void obtenerInfoProducto_success() throws Exception {
		ProductoDocument doc = ProductoDocument.builder()
				.id("p1")
				.nombre("Manzana")
				.categoria("Frutas")
				.descripcion("Rica manzana")
				.imagen("img.png")
				.precioUnitario(12.5)
				.stockQuantity(5)
				.active(true)
				.outOfStock(false)
				.build();

		when(productDocumentRepo.findById("p1")).thenReturn(Optional.of(doc));

		ProductoInfoDTO info = productoService.obtenerInfoProducto("p1");

		assertNotNull(info);
		assertEquals("p1", info.id());
		assertEquals("Manzana", info.nombre());
		assertEquals("Frutas", info.categoria());
		assertEquals("Rica manzana", info.descripcion());
		assertEquals(12.5f, info.precioUnitario());
		assertEquals(5, info.cantidad());
	}

	@Test
	void obtenerInfoProducto_notVisible_throws() {
		ProductoDocument doc = ProductoDocument.builder()
				.id("p2")
				.nombre("Pera")
				.stockQuantity(0)
				.active(false)
				.outOfStock(true)
				.build();

		when(productDocumentRepo.findById("p2")).thenReturn(Optional.of(doc));

		Exception ex = assertThrows(Exception.class, () -> productoService.obtenerInfoProducto("p2"));
		assertTrue(ex.getMessage().contains("Producto no encontrado"));
	}

	@Test
	void reducirCantidadProductosStock_success() throws Exception {
		ProductoDocument doc = ProductoDocument.builder()
				.id("p3")
				.nombre("Leche")
				.stockQuantity(10)
				.active(true)
				.outOfStock(false)
				.build();

		when(productDocumentRepo.findById("p3")).thenReturn(Optional.of(doc));

		productoService.reducirCantidadProductosStock("p3", 3);

		assertEquals(7, doc.getStockQuantity());
		verify(productDocumentRepo, times(1)).save(doc);
	}

	@Test
	void reducirCantidadProductosStock_insufficient_throws() {
		ProductoDocument doc = ProductoDocument.builder()
				.id("p4")
				.nombre("Queso")
				.stockQuantity(1)
				.active(true)
				.outOfStock(false)
				.build();

		when(productDocumentRepo.findById("p4")).thenReturn(Optional.of(doc));

		Exception ex = assertThrows(Exception.class, () -> productoService.reducirCantidadProductosStock("p4", 2));
		assertTrue(ex.getMessage().contains("es alta para el stock") || ex.getMessage().contains("no encontrado") );
		verify(productDocumentRepo, never()).save(any());
	}

	@Test
	void aumentarCantidadProductosStock_success() throws Exception {
		ProductoDocument doc = ProductoDocument.builder()
				.id("p5")
				.nombre("Pan")
				.stockQuantity(0)
				.active(true)
				.outOfStock(true)
				.build();

		when(productDocumentRepo.findById("p5")).thenReturn(Optional.of(doc));

		productoService.aumentarCantidadProductosStock("p5", 5);

		assertEquals(5, doc.getStockQuantity());
		assertFalse(doc.isOutOfStock());
		verify(productDocumentRepo, times(1)).save(doc);
	}

	@Test
	void aumentarCantidadProductosStock_invalidArg_throws() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> productoService.aumentarCantidadProductosStock("p6", 0));
		assertTrue(ex.getMessage().contains("mayor a cero"));
	}

	@Test
	void obtenerProducto_success() throws Exception {
		ProductoDocument doc = ProductoDocument.builder()
				.id("p7")
				.nombre("Arroz")
				.stockQuantity(3)
				.active(true)
				.outOfStock(false)
				.build();

		Producto legacy = Producto.builder()
				.id("p7")
				.nombre("Arroz")
				.descripcion("Arroz blanco")
				.estado("Disponible")
				.categoria("Cereales")
				.cantidad(3)
				.imagen("")
				.precioUnitario(20f)
				.build();

		when(productDocumentRepo.findById("p7")).thenReturn(Optional.of(doc));
		when(catalogMapper.toLegacy(doc)).thenReturn(legacy);

		Producto result = productoService.obtenerProducto("p7");
		assertEquals(legacy, result);
	}

	@Test
	void obtenerProducto_notActive_throws() {
		ProductoDocument doc = ProductoDocument.builder()
				.id("p8")
				.nombre("Huevos")
				.stockQuantity(5)
				.active(false)
				.outOfStock(false)
				.build();

		when(productDocumentRepo.findById("p8")).thenReturn(Optional.of(doc));

		assertThrows(ProductoParseException.class, () -> productoService.obtenerProducto("p8"));
	}

	@Test
	void listarTipos_returnsValues() throws Exception {
		List<String> tipos = productoService.listarTipos();
		assertNotNull(tipos);
		assertTrue(tipos.contains("Bebida"));
		assertTrue(tipos.contains("Cereales"));
		assertTrue(tipos.contains("Frutas"));
	}

}
