package co.uniquindio.tiendasana.repos;

import co.uniquindio.tiendasana.model.documents.CarritoCompras;
import co.uniquindio.tiendasana.model.mongo.CarritoComprasDocument;
import co.uniquindio.tiendasana.model.vo.DetalleCarrito;
import co.uniquindio.tiendasana.repos.mongo.CarritoComprasDocumentRepository;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Repository
public class CarritoComprasRepo {

    private final CarritoComprasDocumentRepository mongo;

    public CarritoComprasRepo(CarritoComprasDocumentRepository mongo) {
        this.mongo = mongo;
    }

    public List<CarritoCompras> obtenerCarritos() {
        List<CarritoCompras> carritos = obtenerCarritosSimples();
        asignarDetalles(carritos);
        return carritos;
    }

    public void asignarDetalles(List<CarritoCompras> carritos) {
        for (CarritoCompras c : carritos) {
            mongo.findById(c.getId()).ifPresent(doc -> {
                List<DetalleCarrito> p = doc.getProductos();
                c.setProductos(p != null ? new ArrayList<>(p) : new ArrayList<>());
            });
        }
    }

    public List<CarritoCompras> filtrar(Predicate<CarritoCompras> expresion) throws IOException {
        return obtenerCarritos().stream().filter(expresion).collect(Collectors.toList());
    }

    public List<CarritoCompras> obtenerCarritosSimples() {
        return mongo.findAll().stream().map(this::toCarritoSimple).collect(Collectors.toList());
    }

    public CarritoCompras mapearCarrito(List<Object> row) {
        String id = row.get(0).toString();
        LocalDateTime fecha = LocalDateTime.parse(row.get(1).toString());
        String idUsuario = row.get(2).toString();
        return CarritoCompras.builder()
                .id(id)
                .fecha(fecha)
                .idUsuario(idUsuario)
                .productos(null)
                .build();
    }

    public List<Object> mapearCarritoInverso(CarritoCompras carrito) {
        return Arrays.asList(
                carrito.getId(),
                carrito.getFecha().toString(),
                carrito.getIdUsuario()
        );
    }

    public List<CarritoCompras> filtrarCarritosSimple(Predicate<CarritoCompras> expresion) throws IOException {
        List<CarritoCompras> carritos = obtenerCarritosSimples();
        List<CarritoCompras> filtrados = carritos.stream().filter(expresion).collect(Collectors.toList());
        asignarDetalles(filtrados);
        return filtrados;
    }

    public int contarCarritosExistintes() {
        return (int) mongo.count();
    }

    public void guardarCarritoCompraSimple(CarritoCompras carrito) {
        mongo.findById(carrito.getId()).ifPresentOrElse(
                existing -> {
                    existing.setFecha(carrito.getFecha());
                    existing.setIdUsuario(carrito.getIdUsuario());
                    mongo.save(existing);
                },
                () -> mongo.save(toDocument(carrito, false))
        );
    }

    public void guardarCarritoCompra(CarritoCompras carrito) {
        mongo.save(toDocument(carrito, true));
    }

    public int obtenerIndiceCarrito(String idUsuario) {
        List<CarritoCompras> carritos = obtenerCarritosSimples();
        for (int i = 0; i < carritos.size(); i++) {
            if (carritos.get(i).getIdUsuario().equals(idUsuario)) {
                return i;
            }
        }
        return -1;
    }

    public void actualizarCarritoSimple(CarritoCompras carrito) throws IOException {
        if (!mongo.existsById(carrito.getId())) {
            throw new IOException("Registro no encontrado");
        }
        CarritoComprasDocument existing = mongo.findById(carrito.getId()).orElseThrow(() -> new IOException("Registro no encontrado"));
        existing.setFecha(carrito.getFecha());
        existing.setIdUsuario(carrito.getIdUsuario());
        mongo.save(existing);
    }

    public void actualizarCarrito(CarritoCompras carrito) throws IOException {
        if (!mongo.existsById(carrito.getId())) {
            throw new IOException("Registro no encontrado");
        }
        mongo.save(toDocument(carrito, true));
    }

    public List<DetalleCarrito> obtenerDetallesCarrito() {
        return mongo.findAll().stream()
                .flatMap(d -> d.getProductos() != null ? d.getProductos().stream() : java.util.stream.Stream.empty())
                .collect(Collectors.toList());
    }

    public DetalleCarrito mapearDetalleCarrito(List<Object> row) {
        String productoId = row.get(0).toString();
        int cantidad = Integer.parseInt(row.get(1).toString());
        float subtotal = Float.parseFloat(row.get(2).toString());
        String idCarrito = row.get(3).toString();
        return DetalleCarrito.builder()
                .productoId(productoId)
                .cantidad(cantidad)
                .subtotal(subtotal)
                .idCarrito(idCarrito)
                .build();
    }

    public List<Object> mapearDetalleCarritoInverso(DetalleCarrito detalle) {
        return Arrays.asList(
                detalle.getProductoId(),
                "" + detalle.getCantidad(),
                "" + (int) detalle.getSubtotal(),
                detalle.getIdCarrito()
        );
    }

    public List<DetalleCarrito> filtrarDetalles(Predicate<DetalleCarrito> expresion) throws IOException {
        return obtenerDetallesCarrito().stream().filter(expresion).collect(Collectors.toList());
    }

    public int contarDetallesExistintes() {
        return obtenerDetallesCarrito().size();
    }

    public void guardarDetalle(DetalleCarrito detalle) throws IOException {
        CarritoComprasDocument doc = mongo.findById(detalle.getIdCarrito())
                .orElseThrow(() -> new IOException("Carrito no encontrado"));
        List<DetalleCarrito> list = doc.getProductos() != null ? new ArrayList<>(doc.getProductos()) : new ArrayList<>();
        list.removeIf(d -> d.getProductoId().equals(detalle.getProductoId()) && d.getIdCarrito().equals(detalle.getIdCarrito()));
        list.add(detalle);
        doc.setProductos(list);
        mongo.save(doc);
    }

    public int obtenerIndiceDetalle(String idCarrito, String productoId) {
        CarritoComprasDocument doc = mongo.findById(idCarrito).orElse(null);
        if (doc == null || doc.getProductos() == null) {
            return -1;
        }
        List<DetalleCarrito> detalles = doc.getProductos();
        for (int i = 0; i < detalles.size(); i++) {
            if (detalles.get(i).getProductoId().equals(productoId)
                    && detalles.get(i).getIdCarrito().equals(idCarrito)) {
                return i;
            }
        }
        return -1;
    }

    public void actualizarDetalle(DetalleCarrito detalle) throws IOException {
        if (obtenerIndiceDetalle(detalle.getIdCarrito(), detalle.getProductoId()) < 0) {
            throw new IOException("Registro no encontrado");
        }
        guardarDetalle(detalle);
    }

    public List<Object> mapearBorrado() {
        return Arrays.asList("-", "0", "0", "-");
    }

    public void eliminarDetalle(DetalleCarrito detalle) throws IOException {
        CarritoComprasDocument doc = mongo.findById(detalle.getIdCarrito())
                .orElseThrow(() -> new IOException("Registro no encontrado"));
        if (doc.getProductos() == null) {
            throw new IOException("Registro no encontrado");
        }
        boolean removed = doc.getProductos().removeIf(d ->
                d.getProductoId().equals(detalle.getProductoId()) && d.getIdCarrito().equals(detalle.getIdCarrito()));
        if (!removed) {
            throw new IOException("Registro no encontrado");
        }
        mongo.save(doc);
    }

    public Optional<CarritoCompras> obtenerPorIdUsuario(String idUsuario) throws IOException {
        List<CarritoCompras> carritosObtenidos = filtrar(carrito -> carrito.getIdUsuario().equals(idUsuario));
        if (carritosObtenidos.isEmpty()) {
            return Optional.empty();
        }
        if (carritosObtenidos.size() > 1) {
            CarritoCompras consolidado = consolidarCarritosDuplicados(idUsuario, carritosObtenidos);
            return Optional.of(consolidado);
        }
        return Optional.of(carritosObtenidos.get(0));
    }

    private CarritoCompras consolidarCarritosDuplicados(String idUsuario, List<CarritoCompras> carritosObtenidos) throws IOException {
        CarritoCompras principal = carritosObtenidos.stream()
                .max(Comparator.comparing(CarritoCompras::getFecha))
                .orElseThrow(() -> new IOException("No fue posible consolidar carritos duplicados de " + idUsuario));

        Map<String, DetalleCarrito> byProducto = new LinkedHashMap<>();
        for (CarritoCompras carrito : carritosObtenidos) {
            List<DetalleCarrito> detalles = carrito.getProductos() != null ? carrito.getProductos() : new ArrayList<>();
            for (DetalleCarrito detalle : detalles) {
                if (detalle == null || detalle.getProductoId() == null || detalle.getProductoId().isBlank()) {
                    continue;
                }

                DetalleCarrito existente = byProducto.get(detalle.getProductoId());
                if (existente == null) {
                    DetalleCarrito nuevo = new DetalleCarrito();
                    nuevo.setIdCarrito(principal.getId());
                    nuevo.setProductoId(detalle.getProductoId());
                    nuevo.setCantidad(Math.max(detalle.getCantidad(), 0));
                    nuevo.setSubtotal(detalle.getSubtotal());
                    byProducto.put(nuevo.getProductoId(), nuevo);
                } else {
                    int cantidadActualizada = Math.max(existente.getCantidad(), 0) + Math.max(detalle.getCantidad(), 0);
                    existente.setCantidad(cantidadActualizada);
                    existente.setSubtotal(existente.getSubtotal() + detalle.getSubtotal());
                }
            }
        }

        principal.setProductos(new ArrayList<>(byProducto.values()));
        principal.setFecha(LocalDateTime.now());
        actualizarCarrito(principal);

        for (CarritoCompras carrito : carritosObtenidos) {
            if (!carrito.getId().equals(principal.getId())) {
                mongo.deleteById(carrito.getId());
            }
        }

        return principal;
    }

    public void eliminarDetalles(List<DetalleCarrito> detallesEliminar) throws IOException {
        for (DetalleCarrito detalle : detallesEliminar) {
            eliminarDetalle(detalle);
        }
    }

    private CarritoCompras toCarritoSimple(CarritoComprasDocument d) {
        return CarritoCompras.builder()
                .id(d.getId())
                .fecha(d.getFecha())
                .idUsuario(d.getIdUsuario())
                .productos(null)
                .build();
    }

    private CarritoComprasDocument toDocument(CarritoCompras c, boolean includeProductos) {
        List<DetalleCarrito> productos = includeProductos && c.getProductos() != null
                ? new ArrayList<>(c.getProductos())
                : new ArrayList<>();
        return CarritoComprasDocument.builder()
                .id(c.getId())
                .fecha(c.getFecha())
                .idUsuario(c.getIdUsuario())
                .productos(productos)
                .build();
    }
}
