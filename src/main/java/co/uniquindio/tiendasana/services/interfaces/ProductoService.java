package co.uniquindio.tiendasana.services.interfaces;

import co.uniquindio.tiendasana.dto.productodtos.FiltroProductoDTO;
import co.uniquindio.tiendasana.dto.productodtos.ListaProductosDTO;
import co.uniquindio.tiendasana.dto.productodtos.ProductoInfoDTO;
import co.uniquindio.tiendasana.exceptions.ProductoParseException;
import co.uniquindio.tiendasana.model.documents.Producto;
import co.uniquindio.tiendasana.model.enums.CategoriaProducto;

import java.io.IOException;
import java.util.List;

public interface ProductoService {

    /**
     * Metodo usado para obtener los detalles de un producto
     * @param id ID del producto.
     * @return DTO con toda la información del producto.
     * @throws Exception Si el producto no existe o hay error al acceder en la base de datos
     */
    ProductoInfoDTO obtenerInfoProducto(String id) throws Exception;

    /**
     * Metodo usado para obtener lo informacion de los productos que los clientes
     * verán en primera instancia
     * @param pagina Número de pagina (empezando en 0).
     * @return Objeto con total de páginas y lista de items de productos.
     * @throws IOException Si al acceder a la base de datos
     * @throws ProductoParseException  Si al parsear un producto ocurre un error.
     */
    ListaProductosDTO obtenerProductosCliente(int pagina) throws IOException, ProductoParseException;

    /**
     * Obtener un producto (Entidad) dado su ID
     * @param id Id del producto
     * @return Producto obtenido
     * @throws ProductoParseException Si el producto no existe
     * @throws IOException Error al acceder a la base de datos
     */
    Producto obtenerProducto(String id) throws ProductoParseException, IOException;

    /**
     * Metodo para reducir la cantidad de Stock de un producto una vez se tenga un pago aprovado y acreditado
     * @param id Id del producto
     * @param cantidadComprada Cantidad que del producto que se compro y que se va a deducir
     * @throws Exception Error al acceder a la base de datos
     * o la cantidad a comprar es mal alta que el stock
     */
    void reducirCantidadProductosStock(String id, int cantidadComprada) throws Exception;

    void reducirCantidadProductosStock(String id, int cantidadComprada, String inventoryReference) throws Exception;

    /**
     * Metodo para reponer stock de un producto despues de un reembolso exitoso.
     * @param id Id del producto
     * @param cantidadAReponer Cantidad a devolver al inventario
     * @throws Exception Error al acceder a la base de datos o cantidad invalida
     */
    void aumentarCantidadProductosStock(String id, int cantidadAReponer) throws Exception;

    void aumentarCantidadProductosStock(String id, int cantidadAReponer, String inventoryReference) throws Exception;

    ListaProductosDTO filtrarProductos(FiltroProductoDTO filtroProductoDTO) throws Exception;

    List<String> listarTipos() throws Exception;
}
