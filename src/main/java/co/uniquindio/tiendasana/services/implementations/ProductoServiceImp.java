package co.uniquindio.tiendasana.services.implementations;



import co.uniquindio.tiendasana.dto.productodtos.*;

import co.uniquindio.tiendasana.exceptions.ProductoParseException;

import co.uniquindio.tiendasana.model.documents.Producto;

import co.uniquindio.tiendasana.model.enums.CategoriaProducto;

import co.uniquindio.tiendasana.model.mongo.ProductoDocument;

import co.uniquindio.tiendasana.repos.mongo.ProductoDocumentRepository;

import co.uniquindio.tiendasana.services.interfaces.ProductoService;

import co.uniquindio.tiendasana.services.mongo.ProductCatalogMapper;

import co.uniquindio.tiendasana.utils.ProductoConstantes;

import org.jetbrains.annotations.NotNull;

import org.springframework.data.domain.Page;

import org.springframework.data.domain.PageRequest;

import org.springframework.data.domain.Sort;

import org.springframework.stereotype.Service;



import java.io.IOException;

import java.util.Arrays;

import java.util.Collections;

import java.util.List;

import java.util.function.Predicate;

import java.util.stream.Collectors;



/**

 * Catálogo de productos para el cliente y operaciones de stock tras pago.

 * Persistencia: MongoDB ({@link ProductoDocument}).

 */

@Service

public class ProductoServiceImp implements ProductoService {



    private final ProductoDocumentRepository productDocumentRepo;

    private final ProductCatalogMapper catalogMapper;



    public ProductoServiceImp(

            ProductoDocumentRepository productDocumentRepo,

            ProductCatalogMapper catalogMapper) {

        this.productDocumentRepo = productDocumentRepo;

        this.catalogMapper = catalogMapper;

    }



    @Override

    public ProductoInfoDTO obtenerInfoProducto(String id) throws Exception {

        ProductoDocument doc = productDocumentRepo.findById(id)

                .orElseThrow(() -> new Exception("Producto no encontrado"));

        if (!isVisibleToClient(doc)) {

            throw new Exception("Producto no encontrado");

        }

        return new ProductoInfoDTO(

                doc.getId(),

                doc.getNombre(),

                doc.getCategoria(),

                doc.getDescripcion(),

                doc.getImagen(),

                (float) doc.getPrecioUnitario(),

                doc.getStockQuantity()

        );

    }



    @Override

    public ListaProductosDTO obtenerProductosCliente(int pagina) throws IOException, ProductoParseException {

        Page<ProductoDocument> page = productDocumentRepo.findByActiveTrueAndOutOfStockFalseAndStockQuantityGreaterThan(

                0,

                PageRequest.of(pagina, ProductoConstantes.ELEMENTOSPAGINA, Sort.by("nombre")));

        List<ProductoItemDTO> items = page.getContent().stream()

                .map(doc -> new ProductoItemDTO(

                        doc.getId(),

                        doc.getNombre(),

                        doc.getCategoria(),

                        doc.getImagen(),

                        (float) doc.getPrecioUnitario()))

                .collect(Collectors.toList());

        return new ListaProductosDTO(page.getTotalPages(), items);

    }



    @Override

    public Producto obtenerProducto(String id) throws ProductoParseException, IOException {

        ProductoDocument doc = productDocumentRepo.findById(id)

                .orElseThrow(() -> new ProductoParseException("Producto no encontrado"));

        if (!doc.isActive() || doc.isOutOfStock()) {

            throw new ProductoParseException("Producto no encontrado");

        }

        return catalogMapper.toLegacy(doc);

    }



    @Override

    public void reducirCantidadProductosStock(String id, int cantidadComprada) throws Exception {

        ProductoDocument doc = productDocumentRepo.findById(id)

                .orElseThrow(() -> new Exception("Producto no encontrado"));

        if (doc.getStockQuantity() - cantidadComprada < 0) {

            throw new Exception("La compra del producto " + doc.getNombre() + " es alta para el stock");

        }

        doc.setStockQuantity(doc.getStockQuantity() - cantidadComprada);

        productDocumentRepo.save(doc);

        System.out.println("Se ha actualizado el producto correctamente");

    }



    @Override

    public void aumentarCantidadProductosStock(String id, int cantidadAReponer) throws Exception {

        if (cantidadAReponer <= 0) {

            throw new IllegalArgumentException("La cantidad a reponer debe ser mayor a cero");

        }



        ProductoDocument doc = productDocumentRepo.findById(id)

                .orElseThrow(() -> new Exception("Producto no encontrado"));



        doc.setStockQuantity(doc.getStockQuantity() + cantidadAReponer);

        if (doc.getStockQuantity() > 0) {

            doc.setOutOfStock(false);

        }



        productDocumentRepo.save(doc);

        System.out.println("Se ha repuesto el stock del producto correctamente");

    }



    @Override

    public ListaProductosDTO filtrarProductos(FiltroProductoDTO filtroProductoDTO) throws Exception {

        Predicate<Producto> filtro = getProductoPredicate(filtroProductoDTO);

        List<Producto> visibles = productDocumentRepo

                .findAllByActiveTrueAndOutOfStockFalseAndStockQuantityGreaterThanOrderByNombreAsc(0)

                .stream()

                .map(catalogMapper::toLegacy)

                .filter(filtro)

                .collect(Collectors.toList());



        int pageSize = ProductoConstantes.ELEMENTOSPAGINA;

        int totalItems = visibles.size();

        int totalPaginas = (totalItems == 0) ? 0 : (int) Math.ceil((double) totalItems / pageSize);

        List<Producto> paginatedList = obtenerListaPaginada(visibles, filtroProductoDTO.pagina(), pageSize, totalItems);



        List<ProductoItemDTO> productosItems = paginatedList.stream()

                .map(producto -> new ProductoItemDTO(

                        producto.getId(),

                        producto.getNombre(),

                        producto.getCategoria(),

                        producto.getImagen(),

                        producto.getPrecioUnitario()))

                .collect(Collectors.toList());



        return new ListaProductosDTO(totalPaginas, productosItems);

    }



    private List<Producto> obtenerListaPaginada(List<Producto> productosFiltrados, int pagina, int pageSize, int totalItems) {

        int pageNumber = Math.max(pagina, 0);

        if (totalItems == 0 || pageNumber * pageSize >= totalItems) {

            return Collections.emptyList();

        }

        int startItem = pageNumber * pageSize;

        int endItem = Math.min(startItem + pageSize, totalItems);

        return productosFiltrados.subList(startItem, endItem);

    }



    private static @NotNull Predicate<Producto> getProductoPredicate(FiltroProductoDTO filtroProductoDTO) throws Exception {

        System.out.println("Filtro recibido de producto: " + filtroProductoDTO);

        boolean filtroVacio = (filtroProductoDTO.nombre() == null || filtroProductoDTO.nombre().isEmpty()) &&

                (filtroProductoDTO.categoria() == null || filtroProductoDTO.categoria().isEmpty()) &&

                filtroProductoDTO.cantidad() == 0;



        if (filtroVacio) {

            throw new Exception("Debe proporcionar al menos un criterio de filtro.");

        }



        return producto -> {

            boolean matches = true;

            if (filtroProductoDTO.nombre() != null && !filtroProductoDTO.nombre().isEmpty()) {

                matches &= (producto.getNombre() != null &&

                        producto.getNombre().toLowerCase().contains(filtroProductoDTO.nombre().toLowerCase()));

            }

            if (filtroProductoDTO.cantidad() != 0) {

                matches &= producto.getCantidad() >= filtroProductoDTO.cantidad();

            }

            if (filtroProductoDTO.categoria() != null && !filtroProductoDTO.categoria().isEmpty()) {

                matches &= (producto.getCategoria() != null &&

                        producto.getCategoria().equalsIgnoreCase(filtroProductoDTO.categoria()));

            }

            return matches;

        };

    }



    @Override

    public List<String> listarTipos() throws Exception {

        List<String> tipos = Arrays.stream(CategoriaProducto.values())

                .map(CategoriaProducto::getCategoria)

                .collect(Collectors.toList());

        if (tipos.isEmpty()) {

            throw new Exception("No existen categorias para los producots");

        }

        return tipos;

    }



    private static boolean isVisibleToClient(ProductoDocument doc) {

        return doc.isActive() && !doc.isOutOfStock() && doc.getStockQuantity() > 0;

    }

}

