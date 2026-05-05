package co.uniquindio.tiendasana.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/internal")
public class SmtpConnectivityController {

    @GetMapping("/test-smtp")
    public ResponseEntity<Map<String, Object>> testSmtp(@RequestParam String host,
                                                       @RequestParam int port,
                                                       @RequestParam(defaultValue = "60000") int timeoutMs) {
        Map<String, Object> res = new HashMap<>();
        try (Socket socket = new Socket()) {
            SocketAddress address = new InetSocketAddress(host, port);
            long start = System.currentTimeMillis();
            socket.connect(address, timeoutMs);
            long elapsed = System.currentTimeMillis() - start;
            res.put("success", true);
            res.put("host", host);
            res.put("port", port);
            res.put("timeoutMs", timeoutMs);
            res.put("connectTimeMs", elapsed);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            res.put("success", false);
            res.put("host", host);
            res.put("port", port);
            res.put("timeoutMs", timeoutMs);
            res.put("error", e.toString());
            return ResponseEntity.status(503).body(res);
        }
    }
}
