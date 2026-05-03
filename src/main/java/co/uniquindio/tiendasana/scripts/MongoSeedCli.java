package co.uniquindio.tiendasana.scripts;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import co.uniquindio.tiendasana.model.enums.Localidad;
import org.bson.Document;

import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.mongodb.client.model.Filters.eq;

/**
 * Inserta datos semilla de forma idempotente y versionada en MongoDB.
 * <p>
 * Ejecutar: {@code ./gradlew seedMongo} (o {@code gradlew.bat seedMongo} en Windows).
 * URI: variable de entorno {@code MONGODB_URI} o {@code mongodb://localhost:27017/tienda_sana}.
 * Modo: variable de entorno {@code SEED_MODE} con valores {@code reference} o {@code demo}.
 */
public final class MongoSeedCli {

    private static final String DEFAULT_URI = "mongodb://localhost:27017/tienda_sana";
        private static final String SEED_HISTORY_COLLECTION = "seed_history";

    private static final String HASH_CLIENTE = "$2a$10$IvLX08tKkzPt7y0JUbL2xeO07vBhBjt9E2tVUzbAxdLZ2J9IydgkG"; // Cliente123!
    private static final String HASH_ADMIN = "$2a$10$eX7bf6igFrHrvpqnK4NOFuk6oNNDOLSwZXJt4i9ufWlv/tQydSu9K"; // Admin123!

        private enum SeedMode {
                REFERENCE,
                DEMO;

                static SeedMode fromEnv(String value) {
                        if (value == null || value.isBlank()) {
                                return DEMO;
                        }
                        String normalized = value.trim().toUpperCase(Locale.ROOT);
                        if ("REFERENCE".equals(normalized)) {
                                return REFERENCE;
                        }
                        if ("DEMO".equals(normalized)) {
                                return DEMO;
                        }
                        throw new IllegalArgumentException("SEED_MODE invalido: " + value + ". Usa reference o demo.");
                }
        }

        @FunctionalInterface
        private interface SeedAction {
                void apply(MongoDatabase db, Date now);
        }

        private record SeedChange(String id, String description, SeedMode mode, SeedAction action) {
        }

    public static void main(String[] args) {
        String uri = System.getenv().getOrDefault("MONGODB_URI", DEFAULT_URI);
                SeedMode seedMode = SeedMode.fromEnv(System.getenv("SEED_MODE"));

        ConnectionString cs = new ConnectionString(uri);
        String dbName = cs.getDatabase();
        if (dbName == null || dbName.isBlank()) {
            dbName = "tienda_sana";
        }

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(cs)
                .build();

        try (MongoClient client = MongoClients.create(settings)) {
            MongoDatabase db = client.getDatabase(dbName);
            db.runCommand(new Document("ping", 1));
            System.out.println("Conexión OK: " + uri.replaceAll("://[^@]+@", "://***@"));
            System.out.println("Base objetivo: " + dbName);
            System.out.println("Modo seed: " + seedMode.name().toLowerCase(Locale.ROOT));

            Date now = new Date();
            MongoCollection<Document> history = db.getCollection(SEED_HISTORY_COLLECTION);

            List<SeedChange> changes = List.of(
                    new SeedChange(
                            "V001_REFERENCE_PRODUCTS_TABLES",
                            "Carga catalogo base de productos y mesas visibles",
                            SeedMode.REFERENCE,
                            (database, timestamp) -> {
                                List<Document> products = List.of(
                                        product("seed-prod-ensalada-001", "Ensalada Mediterránea",
                                                "Lechuga, tomate, pepino, aceitunas y queso feta.", "Ensaladas",
                                                "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=800", 18900, 40),
                                        product("seed-prod-jugo-002", "Jugo natural de naranja",
                                                "500 ml, recien exprimido.", "Bebidas",
                                                "https://images.unsplash.com/photo-1600271886742-f049cd451bba?w=800", 6500, 100),
                                        product("seed-prod-wrap-003", "Wrap integral pollo",
                                                "Pollo grill, vegetales y aderezo yogurt.", "Platos fuertes",
                                                "https://images.unsplash.com/photo-1626700051175-6818013e1d4f?w=800", 22500, 25));
                                upsertById(database, "products", products);

                                List<Document> tables = List.of(
                                        mesaCliente("seed-mesa-centro-01", "Mesa terraza Centro", "Disponible", "Centro", 45000, 4,
                                                "https://images.unsplash.com/photo-1414235077428-338989a2e8c0?w=800"),
                                        mesaCliente("seed-mesa-patio-02", "Mesa jardin", "Disponible", "Patio", 55000, 6,
                                                "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=800"),
                                        mesaCliente("seed-mesa-salon-03", "Mesa salon VIP", "Disponible", "Salon", 60000, 8,
                                                "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800"));
                                upsertById(database, "tables", tables);
                            }),
                    new SeedChange(
                            "V002_DEMO_ACCOUNTS",
                            "Carga cuentas demo para pruebas funcionales",
                            SeedMode.DEMO,
                            (database, timestamp) -> {
                                List<Document> accounts = List.of(
                                        new Document("_id", "cliente.demo@tiendasana.local")
                                                .append("nombre", "Cliente Demo")
                                                .append("telefono", "3001234567")
                                                .append("direccion", "Calle 10 # 20-30")
                                                .append("contrasenia", HASH_CLIENTE)
                                                .append("rol", "CLIENTE")
                                                .append("estado", "ACTIVA")
                                                .append("fechaRegistro", timestamp)
                                                .append("codigoRegistro", "SEED01")
                                                .append("fechaCodigoRegistro", timestamp)
                                                .append("codigoContrasenia", "SEED02")
                                                .append("fechaCodigoContrasenia", timestamp),
                                        new Document("_id", "admin@tiendasana.local")
                                                .append("nombre", "Administrador Demo")
                                                .append("telefono", "3009876543")
                                                .append("direccion", "Tienda Sana HQ")
                                                .append("contrasenia", HASH_ADMIN)
                                                .append("rol", "ADMIN")
                                                .append("estado", "ACTIVA")
                                                .append("fechaRegistro", timestamp)
                                                .append("codigoRegistro", "SEED03")
                                                .append("fechaCodigoRegistro", timestamp)
                                                .append("codigoContrasenia", "SEED04")
                                                .append("fechaCodigoContrasenia", timestamp));
                                upsertById(database, "accounts", accounts);
                            }),
                    new SeedChange(
                            "V003_REFERENCE_EXPANDED_CATALOG",
                            "Amplia catalogo con productos y mesas variados para pruebas mas realistas",
                            SeedMode.REFERENCE,
                            (database, timestamp) -> {
                                List<Document> products = List.of(
                                        product("seed-prod-bowl-004", "Bowl de quinoa y garbanzos",
                                                "Quinoa, garbanzos crocantes, aguacate y verduras mixtas.", "Platos fuertes",
                                                "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=800", 27900, 35),
                                        product("seed-prod-sopa-005", "Sopa de tomate rostizado",
                                                "Sopa cremosa de tomate, albahaca y aceite de oliva.", "Entradas",
                                                "https://images.unsplash.com/photo-1547592166-23ac45744acd?w=800", 12900, 28),
                                        product("seed-prod-panini-006", "Panini integral de pavo",
                                                "Pan integral, pavo ahumado, espinaca y queso bajo en grasa.", "Platos fuertes",
                                                "https://images.unsplash.com/photo-1528735602780-2552fd46c7af?w=800", 23900, 22),
                                        product("seed-prod-ensalada-007", "Ensalada cesar fit",
                                                "Lechuga romana, pollo grill y aderezo yogurt sin azucar.", "Ensaladas",
                                                "https://images.unsplash.com/photo-1550304943-4f24f54ddde9?w=800", 21500, 32),
                                        product("seed-prod-jugo-008", "Jugo verde detox",
                                                "Pepino, pina, espinaca y jengibre natural.", "Bebidas",
                                                "https://images.unsplash.com/photo-1502741224143-90386d7f8c82?w=800", 9900, 70),
                                        product("seed-prod-smothie-009", "Smoothie frutos rojos",
                                                "Fresa, mora, yogur griego y chia.", "Bebidas",
                                                "https://images.unsplash.com/photo-1505253716362-afaea6e1f6af?w=800", 14500, 55),
                                        product("seed-prod-wrap-010", "Wrap vegano de hummus",
                                                "Tortilla integral con hummus, tofu marinado y verduras asadas.", "Platos fuertes",
                                                "https://images.unsplash.com/photo-1626700051175-6818013e1d4f?w=800", 22900, 26),
                                        product("seed-prod-pasta-011", "Pasta integral pesto",
                                                "Pasta integral con pesto de espinaca y nuez.", "Platos fuertes",
                                                "https://images.unsplash.com/photo-1473093295043-cdd812d0e601?w=800", 26900, 20),
                                        product("seed-prod-postre-012", "Parfait de yogur y granola",
                                                "Yogur natural, granola artesanal y frutas de temporada.", "Postres",
                                                "https://images.unsplash.com/photo-1488477181946-6428a0291777?w=800", 11900, 40),
                                        product("seed-prod-postre-013", "Brownie sin azucar",
                                                "Brownie de cacao 70% con endulzante natural.", "Postres",
                                                "https://images.unsplash.com/photo-1606313564200-e75d5e30476e?w=800", 10500, 30),
                                        product("seed-prod-cafe-014", "Cafe cold brew",
                                                "Cafe de especialidad infusionado en frio.", "Bebidas",
                                                "https://images.unsplash.com/photo-1517701604599-bb29b565090c?w=800", 8900, 80),
                                        product("seed-prod-desayuno-015", "Tostada de aguacate",
                                                "Pan de masa madre, aguacate, semillas y limon.", "Desayunos",
                                                "https://images.unsplash.com/photo-1525351484163-7529414344d8?w=800", 16500, 33),
                                        product("seed-prod-desayuno-016", "Omelette de claras",
                                                "Claras de huevo, espinaca, champinon y tomate cherry.", "Desayunos",
                                                "https://images.unsplash.com/photo-1510693206972-df098062cb71?w=800", 18400, 27),
                                        product("seed-prod-sandwich-017", "Sandwich caprese integral",
                                                "Mozzarella fresca, tomate, albahaca y reduccion balsamica.", "Platos fuertes",
                                                "https://images.unsplash.com/photo-1481070414801-51fd732d7184?w=800", 21900, 24),
                                        product("seed-prod-snack-018", "Mix de frutos secos",
                                                "Almendras, nueces, mani y arandanos secos.", "Snacks",
                                                "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=800", 9500, 90),
                                        product("seed-prod-sopa-019", "Crema de zapallo",
                                                "Crema suave de zapallo con topping de semillas.", "Entradas",
                                                "https://images.unsplash.com/photo-1547592166-23ac45744acd?w=800", 13300, 26),
                                        product("seed-prod-ensalada-020", "Ensalada tropical",
                                                "Mix verde, mango, pepino, almendras y vinagreta citrica.", "Ensaladas",
                                                "https://images.unsplash.com/photo-1540420773420-3366772f4999?w=800", 19800, 29));
                                upsertById(database, "products", products);

                                List<Document> tables = List.of(
                                        mesaCliente("seed-mesa-urbana-04", "Mesa urbana 2p", "Disponible", "Centro", 39000, 2,
                                                "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800"),
                                        mesaCliente("seed-mesa-urbana-05", "Mesa urbana 4p", "Disponible", "Centro", 46000, 4,
                                                "https://images.unsplash.com/photo-1414235077428-338989a2e8c0?w=800"),
                                        mesaCliente("seed-mesa-jardin-06", "Mesa jardin familiar", "Disponible", "Patio", 62000, 6,
                                                "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=800"),
                                        mesaCliente("seed-mesa-jardin-07", "Mesa picnic premium", "Disponible", "Patio", 68000, 8,
                                                "https://images.unsplash.com/photo-1466978913421-dad2ebd01d17?w=800"),
                                        mesaCliente("seed-mesa-salon-08", "Mesa salon pareja", "Disponible", "Salon", 43000, 2,
                                                "https://images.unsplash.com/photo-1559339352-11d035aa65de?w=800"),
                                        mesaCliente("seed-mesa-salon-09", "Mesa salon ejecutiva", "Disponible", "Salon", 59000, 6,
                                                "https://images.unsplash.com/photo-1551632436-cbf8dd35adfa?w=800"),
                                        mesaCliente("seed-mesa-terraza-10", "Mesa terraza brunch", "Disponible", "Terraza", 51000, 4,
                                                "https://images.unsplash.com/photo-1508424757105-b6d5ad9329d0?w=800"),
                                        mesaCliente("seed-mesa-terraza-11", "Mesa terraza panoramica", "Disponible", "Terraza", 74000, 8,
                                                "https://images.unsplash.com/photo-1550966871-3ed3cdb5ed0c?w=800"),
                                        mesaCliente("seed-mesa-vip-12", "Mesa degustacion VIP", "Disponible", "Salon", 98000, 10,
                                                "https://images.unsplash.com/photo-1578474846511-04ba529f0b88?w=800"),
                                        mesaCliente("seed-mesa-vip-13", "Mesa privada celebracion", "Disponible", "Pasillo", 120000, 12,
                                                "https://images.unsplash.com/photo-1514933651103-005eec06c04b?w=800"));
                                upsertById(database, "tables", tables);
                            }),
                    new SeedChange(
                            "V004_REFERENCE_NORMALIZE_TABLE_LOCALITIES",
                            "Normaliza localidades de mesas existentes a valores soportados por el enum",
                            SeedMode.REFERENCE,
                            (database, timestamp) -> {
                                MongoCollection<Document> tablesCollection = database.getCollection("tables");
                                int fixed = 0;
                                for (Document table : tablesCollection.find()) {
                                    String localidad = table.getString("localidad");
                                    if (!isValidLocalidad(localidad)) {
                                        String normalized = normalizeLocalidad(localidad);
                                        table.put("localidad", normalized);
                                        tablesCollection.replaceOne(eq("_id", table.getString("_id")), table, new ReplaceOptions().upsert(true));
                                        fixed++;
                                    }
                                }
                                System.out.println("Mesas normalizadas con localidad valida: " + fixed);
                            })
            );

            int applied = 0;
            int skipped = 0;
            for (SeedChange change : changes) {
                if (seedMode == SeedMode.REFERENCE && change.mode == SeedMode.DEMO) {
                    System.out.println("[SKIP mode] " + change.id + " -> requiere modo demo");
                    skipped++;
                    continue;
                }

                Document alreadyApplied = history.find(eq("_id", change.id)).first();
                if (alreadyApplied != null) {
                    System.out.println("[SKIP applied] " + change.id + " ya fue aplicado");
                    skipped++;
                    continue;
                }

                change.action.apply(db, now);
                history.insertOne(new Document("_id", change.id)
                        .append("description", change.description)
                        .append("mode", change.mode.name().toLowerCase(Locale.ROOT))
                        .append("appliedAt", now));
                System.out.println("[APPLIED] " + change.id + " - " + change.description);
                applied++;
            }

            System.out.println("\n=== Conteos ===");
            System.out.println("accounts:           " + db.getCollection("accounts").countDocuments());
            System.out.println("products:           " + db.getCollection("products").countDocuments());
            System.out.println("tables:             " + db.getCollection("tables").countDocuments());
            System.out.println("seed_history:       " + db.getCollection(SEED_HISTORY_COLLECTION).countDocuments());
            System.out.println("\nResumen seed -> aplicados: " + applied + ", omitidos: " + skipped);
            System.out.println("\nSeed completado.");
            if (seedMode == SeedMode.DEMO) {
                System.out.println("Usuarios demo insertados/actualizados.");
            }
        } catch (MongoException e) {
            System.err.println("Error MongoDB: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void upsertById(MongoDatabase db, String collectionName, List<Document> docs) {
        ReplaceOptions upsert = new ReplaceOptions().upsert(true);
        MongoCollection<Document> collection = db.getCollection(collectionName);
        for (Document doc : docs) {
            collection.replaceOne(eq("_id", doc.getString("_id")), doc, upsert);
        }
    }

    private static Document product(String id, String nombre, String descripcion, String categoria,
                                    String imagen, double precio, int stock) {
        return new Document("_id", id)
                .append("nombre", nombre)
                .append("descripcion", descripcion)
                .append("categoria", categoria)
                .append("imagen", imagen)
                .append("precioUnitario", precio)
                .append("stockQuantity", stock)
                .append("active", true)
                .append("outOfStock", false)
                .append("calificacionPromedio", 0);
    }

    private static Document mesaCliente(String id, String nombre, String estado, String localidad,
                                      double precio, int capacidad, String imagen) {
        return new Document("_id", id)
                .append("nombre", nombre)
                .append("estado", estado)
                .append("localidad", localidad)
                .append("precioReserva", precio)
                .append("capacidad", capacidad)
                .append("imagen", imagen)
                .append("visibleToClient", true);
    }

        private static boolean isValidLocalidad(String localidad) {
                if (localidad == null || localidad.isBlank()) {
                        return false;
                }
                try {
                        Localidad.fromLocalidad(localidad.trim());
                        return true;
                } catch (IllegalArgumentException e) {
                        return false;
                }
        }

        private static String normalizeLocalidad(String localidad) {
                if (localidad == null || localidad.isBlank()) {
                        return Localidad.CENTRO.getLocalidad();
                }
                try {
                        return Localidad.fromLocalidad(localidad.trim()).getLocalidad();
                } catch (IllegalArgumentException e) {
                        return Localidad.SALON.getLocalidad();
                }
        }

    private MongoSeedCli() {
    }
}
