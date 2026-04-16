package co.uniquindio.tiendasana.controllers;

import co.uniquindio.tiendasana.dto.aidtos.AiRecommendationRequestDTO;
import co.uniquindio.tiendasana.dto.aidtos.AiRecommendationResponseDTO;
import co.uniquindio.tiendasana.dto.jwtdtos.MessageDTO;
import co.uniquindio.tiendasana.dto.mesadtos.FiltroMesaDTO;
import co.uniquindio.tiendasana.dto.mesadtos.ListaMesasDTO;
import co.uniquindio.tiendasana.dto.mesadtos.MesaHorarioReservadoDTO;
import co.uniquindio.tiendasana.dto.mesadtos.MesaInfoDTO;
import co.uniquindio.tiendasana.dto.productodtos.FiltroProductoDTO;
import co.uniquindio.tiendasana.dto.productodtos.ListaProductosDTO;
import co.uniquindio.tiendasana.dto.productodtos.ProductoInfoDTO;
import co.uniquindio.tiendasana.model.enums.CategoriaProducto;
import co.uniquindio.tiendasana.model.enums.Localidad;
import co.uniquindio.tiendasana.services.interfaces.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controlador para los endpoints publicos, es decir no necesitan de un JWT con rol para ser accedidos
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public")
public class PublicoController {

    /**
     * Instacia del servicio para los métodos de los productos
     */
    private final ProductoService productService;
    private final CuentaService cuentaService;
    private final VentaProductoService ventaProductoService;
    private final ReservaService reservaService;
    private final MesaService mesaService;
    private final AiRecommendationService aiRecommendationService;

    /**
     *  Endporint mediante el cual se obtienen los productos que verán los clientes
     * @param pagina Pagina de los productos
     * @return Respuesta a la solicitud
     * @throws Exception
     */
    @GetMapping("/productos/get-all/{pagina}")
    public ResponseEntity<MessageDTO<ListaProductosDTO>> listarProductosCliente(@PathVariable int pagina) throws Exception {
        ListaProductosDTO productos= productService.obtenerProductosCliente(pagina);
        return ResponseEntity.ok( new MessageDTO<>(false, productos));
    }

    /**
     * Endpoint mediante el cual se obtiene las mesas que verán los clientes
     * @param pagina Pagina de las mesas
     * @return Respuesta a la solicitud
     * @throws Exception
     */
    @GetMapping("/mesas/get-all/{pagina}")
    public ResponseEntity<MessageDTO<ListaMesasDTO>> listarMesasCliente(@PathVariable int pagina) throws Exception {
        ListaMesasDTO mesas= mesaService.obtenerMesasCliente(pagina);
        return ResponseEntity.ok( new MessageDTO<>(false, mesas));
    }

    @GetMapping("/mesas/get-info/{id}")
    public ResponseEntity<MessageDTO<MesaInfoDTO>> obtenerInfoMesa(@PathVariable String id) throws Exception {
        MesaInfoDTO mesaInfo = mesaService.obtenerInfoMesa(id);
        return ResponseEntity.ok(new MessageDTO<>(false, mesaInfo));
    }

    @GetMapping("/mesas/get-reserved-slots/{id}")
    public ResponseEntity<MessageDTO<List<MesaHorarioReservadoDTO>>> obtenerHorariosReservadosMesa(@PathVariable String id) throws Exception {
        List<MesaHorarioReservadoDTO> horarios = reservaService.listarHorariosReservadosMesa(id);
        return ResponseEntity.ok(new MessageDTO<>(false, horarios));
    }

    /**
     * Endpoint mediante el cual se recibe la notificación de Mercado Pago para ventas
     * @param request Datos para poder recibir notificacion
     * @return Respuesta a la solicitud
     */
    @PostMapping("/venta/receive-notification")
    public ResponseEntity<MessageDTO<String>> receiveNotificationFromMercadoPago(@RequestBody Map<String, Object> request){
        ventaProductoService.receiveNotificationFromMercadoPago(request);
        return ResponseEntity.ok(new MessageDTO<>(false,"Notification received"));
    }


    /**
     * Endpoint mediante el cual se recibe la notificación de Mercado Pago para reservas
     * @param request Datos para poder recibir notificacion
     * @return Respuesta a la solicitud
     */
    @PostMapping("/reserva/receive-notification")
    public ResponseEntity<MessageDTO<String>> receiveNotificationFromMercadoPagoReserva(@RequestBody Map<String, Object> request){
        reservaService.receiveNotificationFromMercadoPago(request);
        return ResponseEntity.ok(new MessageDTO<>(false,"Notification received"));
    }

    /**
     * Endpoint mediante el cual se obtiene la información de un producto
     * @param id Id del producto
     * @return Respuesta a la solicitud
     * @throws Exception
     */
    @GetMapping("/productos/get-info/{id}")
    public ResponseEntity<MessageDTO<ProductoInfoDTO>> obtenerInfoProducto(@PathVariable String id) throws Exception{
        ProductoInfoDTO productoInfo = productService.obtenerInfoProducto(id);
        return ResponseEntity.ok(new MessageDTO<>(false,productoInfo));
    }

    @PostMapping("/productos/filter-products")
    public ResponseEntity<MessageDTO<ListaProductosDTO>> filtrarProductos(@Valid @RequestBody FiltroProductoDTO filtroProductoDTO) throws Exception{
        ListaProductosDTO productos = productService.filtrarProductos(filtroProductoDTO);
        return ResponseEntity.ok(new MessageDTO<>(false,productos));
    }

    @PostMapping("/mesas/filter-tables")
    public ResponseEntity<MessageDTO<ListaMesasDTO>> filtrarMesas(@Valid @RequestBody FiltroMesaDTO filtroMesaDTO) throws Exception{
        ListaMesasDTO mesas = mesaService.filtrarMesas(filtroMesaDTO);
        return ResponseEntity.ok(new MessageDTO<>(false,mesas));
    }

    @GetMapping("/productos/get-types")
    public ResponseEntity<MessageDTO<List<String>>>  listarTipos() throws Exception{
        List<String> tiposProduco = productService.listarTipos();
        return ResponseEntity.ok(new MessageDTO<>(false,tiposProduco));
    }

    @GetMapping("/mesas/get-locality")
    public ResponseEntity<MessageDTO<List<String>>>  listarLocalidades() throws Exception{
        List<String> localidadesMesa = mesaService.listarLocalidades();
        return ResponseEntity.ok(new MessageDTO<>(false,localidadesMesa));
    }

    @PostMapping("/ai/recommendations")
    public ResponseEntity<MessageDTO<AiRecommendationResponseDTO>> recomendarCombosIA(
            @Valid @RequestBody AiRecommendationRequestDTO requestDTO) throws Exception {
        AiRecommendationResponseDTO responseDTO = aiRecommendationService.generarRecomendaciones(requestDTO);
        return ResponseEntity.ok(new MessageDTO<>(false, responseDTO));
    }

}
