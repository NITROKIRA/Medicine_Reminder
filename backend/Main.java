import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Main {
    private static final String LOGIN_FILE = "database/logincredentials.txt";
    private static final String DB_DIR = "database";
    private static final Map<String, String> sessions = new ConcurrentHashMap<>();
    private static final Map<String, String> userPasswords = new ConcurrentHashMap<>();
    private static final Map<String, List<HealthReading>> userReadings = new ConcurrentHashMap<>();
    private static final Map<String, List<Medicine>> userMedicines = new ConcurrentHashMap<>();
    private static final Map<String, Integer> medicineIdCounter = new ConcurrentHashMap<>();
    private static final Map<String, List<DiseaseHistory>> userHistory = new ConcurrentHashMap<>();
    private static final Map<String, Integer> historyIdCounter = new ConcurrentHashMap<>();
    private static final Random rand = new Random();

    public static void main(String[] args) throws IOException {
        new File(DB_DIR).mkdirs();
        loadLoginData();
        loadAllPatientData();
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/", new StaticHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/signup", new SignupHandler());
        server.createContext("/api/dashboard-data", new DashboardHandler());
        server.createContext("/api/report-data", new ReportDataHandler());
        server.createContext("/api/add-reading", new AddReadingHandler());
        server.createContext("/api/medicines", new MedicinesHandler());
        server.createContext("/api/history", new HistoryHandler());
        server.createContext("/api/logout", new LogoutHandler());

        server.setExecutor(null);
        server.start();
        System.out.println("Medicine Reminder Server started at http://localhost:8080");
        System.out.println("Data stored in: " + new File(LOGIN_FILE).getAbsolutePath());
    }

    static class HealthReading {
        String date;
        int systolic, diastolic, sugar, heartRate;
        HealthReading(String date, int systolic, int diastolic, int sugar, int heartRate) {
            this.date = date; this.systolic = systolic; this.diastolic = diastolic;
            this.sugar = sugar; this.heartRate = heartRate;
        }
    }

    static class Medicine {
        int id;
        String name, dosage, frequency, timeOfDay, startDate, endDate;
        boolean active;
        Medicine(int id, String name, String dosage, String frequency, String timeOfDay, String startDate, String endDate, boolean active) {
            this.id = id; this.name = name; this.dosage = dosage; this.frequency = frequency;
            this.timeOfDay = timeOfDay; this.startDate = startDate; this.endDate = endDate; this.active = active;
        }
    }

    static class DiseaseHistory {
        int id;
        String diseaseName, diseaseType, status, startDate, endDate, notes;
        DiseaseHistory(int id, String diseaseName, String diseaseType, String status, String startDate, String endDate, String notes) {
            this.id = id; this.diseaseName = diseaseName; this.diseaseType = diseaseType; this.status = status;
            this.startDate = startDate; this.endDate = endDate; this.notes = notes;
        }
    }

    private static String patientFilePath(String username) {
        return DB_DIR + File.separator + username + ".txt";
    }

    private static void loadAllPatientData() {
        File dir = new File(DB_DIR);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".txt") && !name.equals("logincredentials.txt"));
        if (files == null) return;
        for (File f : files) {
            String username = f.getName().replace(".txt", "").toLowerCase();
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    if (line.startsWith("R|")) {
                        String[] parts = line.split("\\|");
                        if (parts.length >= 6) {
                            try {
                                String date = parts[1].trim();
                                int systolic = Integer.parseInt(parts[2].trim());
                                int diastolic = Integer.parseInt(parts[3].trim());
                                int sugar = Integer.parseInt(parts[4].trim());
                                int heartRate = Integer.parseInt(parts[5].trim());
                                userReadings.computeIfAbsent(username, k -> new ArrayList<>())
                                    .add(new HealthReading(date, systolic, diastolic, sugar, heartRate));
                            } catch (NumberFormatException ignored) {}
                        }
                    } else if (line.startsWith("M|")) {
                        String[] parts = line.split("\\|");
                        if (parts.length >= 8) {
                            try {
                                int id = Integer.parseInt(parts[1].trim());
                                String name = parts[2].trim();
                                String dosage = parts[3].trim();
                                String frequency = parts[4].trim();
                                String timeOfDay = parts[5].trim();
                                String startDate = parts[6].trim();
                                String endDate = parts[7].trim();
                                boolean active = parts.length < 9 || parts[8].trim().equals("true");
                                userMedicines.computeIfAbsent(username, k -> new ArrayList<>()).add(new Medicine(id, name, dosage, frequency, timeOfDay, startDate, endDate, active));
                                int maxId = medicineIdCounter.getOrDefault(username, 0);
                                if (id > maxId) medicineIdCounter.put(username, id);
                            } catch (NumberFormatException ignored) {}
                        }
                    } else if (line.startsWith("D|")) {
                        String[] parts = line.split("\\|");
                        if (parts.length >= 4) {
                            try {
                                int id = Integer.parseInt(parts[1].trim());
                                String diseaseName = parts[2].trim();
                                String diseaseType = parts[3].trim();
                                String status = parts.length >= 5 ? parts[4].trim() : "";
                                String startDate = parts.length >= 6 ? parts[5].trim() : "";
                                String endDate = parts.length >= 7 ? parts[6].trim() : "";
                                String notes = parts.length >= 8 ? parts[7].trim() : "";
                                userHistory.computeIfAbsent(username, k -> new ArrayList<>()).add(new DiseaseHistory(id, diseaseName, diseaseType, status, startDate, endDate, notes));
                                int maxId = historyIdCounter.getOrDefault(username, 0);
                                if (id > maxId) historyIdCounter.put(username, id);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error loading patient data for " + username + ": " + e.getMessage());
            }
        }
        System.out.println("Loaded data for " + files.length + " patient(s) from " + DB_DIR);
    }

    private static synchronized void savePatientData(String username) {
        String path = patientFilePath(username);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(path))) {
            List<HealthReading> readings = userReadings.get(username);
            if (readings != null) {
                for (HealthReading r : readings) {
                    bw.write("R|" + r.date + "|" + r.systolic + "|" + r.diastolic + "|" + r.sugar + "|" + r.heartRate);
                    bw.newLine();
                }
            }
            List<Medicine> meds = userMedicines.get(username);
            if (meds != null) {
                for (Medicine m : meds) {
                    bw.write("M|" + m.id + "|" + m.name + "|" + m.dosage + "|" + m.frequency + "|" + m.timeOfDay + "|" + m.startDate + "|" + m.endDate + "|" + m.active);
                    bw.newLine();
                }
            }
            List<DiseaseHistory> history = userHistory.get(username);
            if (history != null) {
                for (DiseaseHistory h : history) {
                    bw.write("D|" + h.id + "|" + h.diseaseName + "|" + h.diseaseType + "|" + h.status + "|" + h.startDate + "|" + h.endDate + "|" + h.notes);
                    bw.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("Error saving patient data for " + username + ": " + e.getMessage());
        }
    }

    private static void saveReading(String username, HealthReading r) {
        userReadings.computeIfAbsent(username, k -> new ArrayList<>()).add(r);
        savePatientData(username);
    }

    private static List<HealthReading> getReadings(String username) {
        List<HealthReading> readings = userReadings.get(username);
        if (readings == null || readings.isEmpty()) {
            readings = generateDefaultReadings();
            userReadings.put(username, readings);
        }
        return readings;
    }

    private static List<HealthReading> generateDefaultReadings() {
        List<HealthReading> list = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 13; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            String date = d.format(DateTimeFormatter.ofPattern("MMM dd"));
            int systolic = 110 + rand.nextInt(15);
            int diastolic = 70 + rand.nextInt(10);
            int sugar = 80 + rand.nextInt(25);
            int heartRate = 65 + rand.nextInt(15);
            list.add(new HealthReading(date, systolic, diastolic, sugar, heartRate));
        }
        return list;
    }

    private static void loadLoginData() {
        File file = new File(LOGIN_FILE);
        if (!file.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\|", 2);
                if (parts.length >= 2) {
                    userPasswords.put(parts[0].trim().toLowerCase(), parts[1].trim());
                }
            }
            System.out.println("Loaded " + userPasswords.size() + " user(s) from " + LOGIN_FILE);
        } catch (IOException e) {
            System.err.println("Error loading login data: " + e.getMessage());
        }
    }

    private static synchronized void saveLoginData() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(LOGIN_FILE))) {
            for (Map.Entry<String, String> entry : userPasswords.entrySet()) {
                bw.write(entry.getKey() + "|" + entry.getValue());
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error saving login data: " + e.getMessage());
        }
    }

    private static void saveUser(String username, String password) {
        String key = username.toLowerCase();
        userPasswords.put(key, password);
        saveLoginData();
    }

    private static String getSessionUser(HttpExchange exchange) {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookie == null) return null;
        String[] cookies = cookie.split(";");
        for (String c : cookies) {
            c = c.trim();
            if (c.startsWith("sessionId=")) {
                String sessionId = c.substring(10);
                return sessions.get(sessionId);
            }
        }
        return null;
    }

    private static void setSession(HttpExchange exchange, String username) {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, username);
        exchange.getResponseHeaders().add("Set-Cookie", "sessionId=" + sessionId + "; Path=/; HttpOnly");
    }

    private static void clearSession(HttpExchange exchange) {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookie != null) {
            String[] cookies = cookie.split(";");
            for (String c : cookies) {
                c = c.trim();
                if (c.startsWith("sessionId=")) {
                    String sessionId = c.substring(10);
                    sessions.remove(sessionId);
                    exchange.getResponseHeaders().add("Set-Cookie", "sessionId=; Path=/; Max-Age=0");
                }
            }
        }
    }

    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";

            File file = new File("./frontend" + path);
            if (!file.exists() || file.isDirectory()) {
                String response = "404 Not Found";
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            String contentType = "text/html";
            if (path.endsWith(".css")) contentType = "text/css";
            else if (path.endsWith(".js")) contentType = "application/javascript";
            else if (path.endsWith(".png")) contentType = "image/png";

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, file.length());
            try (OutputStream os = exchange.getResponseBody()) {
                Files.copy(file.toPath(), os);
            }
        }
    }

    static class SignupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseFormData(body);
            String username = params.get("username");
            String password = params.get("password");

            if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
                sendJson(exchange, 400, "{\"success\":false,\"message\":\"Username and password required\"}");
                return;
            }

            String key = username.trim().toLowerCase();
            if (userPasswords.containsKey(key)) {
                sendJson(exchange, 200, "{\"success\":false,\"message\":\"Username already exists\"}");
                return;
            }

            saveUser(key, password);
            sendJson(exchange, 200, "{\"success\":true,\"message\":\"Account created successfully\"}");
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseFormData(body);
            String username = params.get("username");
            String password = params.get("password");

            if (username == null || password == null) {
                sendJson(exchange, 400, "{\"success\":false,\"message\":\"Username and password required\"}");
                return;
            }

            String key = username.trim().toLowerCase();
            String storedPassword = userPasswords.get(key);
            if (storedPassword == null || !storedPassword.equals(password)) {
                sendJson(exchange, 200, "{\"success\":false,\"message\":\"Invalid username or password\"}");
                return;
            }

            setSession(exchange, key);

            sendJson(exchange, 200, "{\"success\":true,\"message\":\"Login successful\"}");
        }
    }

    static class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String username = getSessionUser(exchange);
            if (username == null) {
                sendJson(exchange, 200, "{\"success\":false,\"message\":\"Not logged in\"}");
                return;
            }

            String json = "{\"success\":true,\"username\":\"" + escapeJson(username) +
                          "\",\"chronic\":\"\",\"acute\":\"\"}";
            sendJson(exchange, 200, json);
        }
    }

    static class ReportDataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String username = getSessionUser(exchange);
            if (username == null) {
                sendJson(exchange, 200, "{\"success\":false}");
                return;
            }

            List<HealthReading> readings = getReadings(username);
            StringBuilder sb = new StringBuilder();
            sb.append("{\"success\":true,\"username\":\"").append(escapeJson(username)).append("\"");
            sb.append(",\"labels\":[");
            for (int i = 0; i < readings.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(readings.get(i).date).append("\"");
            }
            sb.append("],\"systolic\":[");
            for (int i = 0; i < readings.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(readings.get(i).systolic);
            }
            sb.append("],\"diastolic\":[");
            for (int i = 0; i < readings.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(readings.get(i).diastolic);
            }
            sb.append("],\"sugar\":[");
            for (int i = 0; i < readings.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(readings.get(i).sugar);
            }
            sb.append("],\"heartRate\":[");
            for (int i = 0; i < readings.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(readings.get(i).heartRate);
            }
            sb.append("]}");
            sendJson(exchange, 200, sb.toString());
        }
    }

    static class AddReadingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            String username = getSessionUser(exchange);
            if (username == null) {
                sendJson(exchange, 401, "{\"success\":false,\"message\":\"Not logged in\"}");
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseFormData(body);
            try {
                int systolic = Integer.parseInt(params.getOrDefault("systolic", "0"));
                int diastolic = Integer.parseInt(params.getOrDefault("diastolic", "0"));
                int sugar = Integer.parseInt(params.getOrDefault("sugar", "0"));
                int heartRate = Integer.parseInt(params.getOrDefault("heartRate", "0"));
                String date = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM dd"));
                saveReading(username, new HealthReading(date, systolic, diastolic, sugar, heartRate));
                sendJson(exchange, 200, "{\"success\":true}");
            } catch (NumberFormatException e) {
                sendJson(exchange, 200, "{\"success\":false,\"message\":\"Invalid reading values\"}");
            }
        }
    }

    static class MedicinesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String username = getSessionUser(exchange);
            if (username == null) {
                sendJson(exchange, 200, "{\"success\":false,\"message\":\"Not logged in\"}");
                return;
            }

            if ("GET".equals(exchange.getRequestMethod())) {
                List<Medicine> meds = userMedicines.getOrDefault(username, new ArrayList<>());
                StringBuilder sb = new StringBuilder();
                sb.append("{\"success\":true,\"medicines\":[");
                for (int i = 0; i < meds.size(); i++) {
                    if (i > 0) sb.append(",");
                    Medicine m = meds.get(i);
                    sb.append("{\"id\":\"").append(m.id).append("\"");
                    sb.append(",\"name\":\"").append(escapeJson(m.name)).append("\"");
                    sb.append(",\"dosage\":\"").append(escapeJson(m.dosage)).append("\"");
                    sb.append(",\"frequency\":\"").append(escapeJson(m.frequency)).append("\"");
                    sb.append(",\"timeOfDay\":\"").append(escapeJson(m.timeOfDay)).append("\"");
                    sb.append(",\"startDate\":\"").append(escapeJson(m.startDate)).append("\"");
                    sb.append(",\"endDate\":\"").append(escapeJson(m.endDate)).append("\"");
                    sb.append(",\"active\":").append(m.active);
                    sb.append("}");
                }
                sb.append("]}");
                sendJson(exchange, 200, sb.toString());
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(body);
                String action = params.get("action");

                if ("add".equals(action)) {
                    String name = params.getOrDefault("name", "").trim();
                    String dosage = params.getOrDefault("dosage", "").trim();
                    String frequency = params.getOrDefault("frequency", "").trim();

                    if (name.isEmpty() || dosage.isEmpty() || frequency.isEmpty()) {
                        sendJson(exchange, 200, "{\"success\":false,\"message\":\"Name, dosage and frequency required\"}");
                        return;
                    }

                    int id = medicineIdCounter.getOrDefault(username, 0) + 1;
                    medicineIdCounter.put(username, id);
                    String timeOfDay = params.getOrDefault("timeOfDay", "").trim();
                    String startDate = params.getOrDefault("startDate", "").trim();
                    String endDate = params.getOrDefault("endDate", "").trim();
                    Medicine m = new Medicine(id, name, dosage, frequency, timeOfDay, startDate, endDate, true);
                    userMedicines.computeIfAbsent(username, k -> new ArrayList<>()).add(m);
                    savePatientData(username);
                    sendJson(exchange, 200, "{\"success\":true}");
                    return;
                }

                if ("delete".equals(action)) {
                    String idStr = params.get("id");
                    List<Medicine> meds = userMedicines.get(username);
                    if (meds != null && idStr != null) {
                        meds.removeIf(m -> m.id == Integer.parseInt(idStr));
                        savePatientData(username);
                    }
                    sendJson(exchange, 200, "{\"success\":true}");
                    return;
                }

                if ("toggle".equals(action)) {
                    String idStr = params.get("id");
                    boolean active = "true".equals(params.get("active"));
                    List<Medicine> meds = userMedicines.get(username);
                    if (meds != null && idStr != null) {
                        for (Medicine m : meds) {
                            if (m.id == Integer.parseInt(idStr)) {
                                m.active = active;
                                break;
                            }
                        }
                        savePatientData(username);
                    }
                    sendJson(exchange, 200, "{\"success\":true}");
                    return;
                }

                sendJson(exchange, 200, "{\"success\":false,\"message\":\"Unknown action\"}");
                return;
            }

            sendJson(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
        }
    }

    static class HistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String username = getSessionUser(exchange);
            if (username == null) {
                sendJson(exchange, 200, "{\"success\":false,\"message\":\"Not logged in\"}");
                return;
            }

            if ("GET".equals(exchange.getRequestMethod())) {
                List<DiseaseHistory> list = userHistory.getOrDefault(username, new ArrayList<>());
                StringBuilder sb = new StringBuilder();
                sb.append("{\"success\":true,\"history\":[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",");
                    DiseaseHistory h = list.get(i);
                    sb.append("{\"id\":\"").append(h.id).append("\"");
                    sb.append(",\"diseaseName\":\"").append(escapeJson(h.diseaseName)).append("\"");
                    sb.append(",\"diseaseType\":\"").append(escapeJson(h.diseaseType)).append("\"");
                    sb.append(",\"status\":\"").append(escapeJson(h.status)).append("\"");
                    sb.append(",\"startDate\":\"").append(escapeJson(h.startDate)).append("\"");
                    sb.append(",\"endDate\":\"").append(escapeJson(h.endDate)).append("\"");
                    sb.append(",\"notes\":\"").append(escapeJson(h.notes)).append("\"");
                    sb.append("}");
                }
                sb.append("]}");
                sendJson(exchange, 200, sb.toString());
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(body);
                String action = params.get("action");

                if ("add".equals(action)) {
                    String diseaseName = params.getOrDefault("diseaseName", "").trim();
                    String status = params.getOrDefault("status", "").trim();
                    if (diseaseName.isEmpty() || status.isEmpty()) {
                        sendJson(exchange, 200, "{\"success\":false,\"message\":\"Name and status required\"}");
                        return;
                    }
                    int id = historyIdCounter.getOrDefault(username, 0) + 1;
                    historyIdCounter.put(username, id);
                    String diseaseType = params.getOrDefault("diseaseType", "Chronic").trim();
                    String startDate = params.getOrDefault("startDate", "").trim();
                    String endDate = params.getOrDefault("endDate", "").trim();
                    String notes = params.getOrDefault("notes", "").trim();
                    userHistory.computeIfAbsent(username, k -> new ArrayList<>()).add(new DiseaseHistory(id, diseaseName, diseaseType, status, startDate, endDate, notes));
                    savePatientData(username);
                    sendJson(exchange, 200, "{\"success\":true}");
                    return;
                }

                if ("delete".equals(action)) {
                    String idStr = params.get("id");
                    List<DiseaseHistory> list = userHistory.get(username);
                    if (list != null && idStr != null) {
                        list.removeIf(h -> h.id == Integer.parseInt(idStr));
                        savePatientData(username);
                    }
                    sendJson(exchange, 200, "{\"success\":true}");
                    return;
                }

                if ("toggle".equals(action)) {
                    String idStr = params.get("id");
                    String newStatus = params.get("status");
                    List<DiseaseHistory> list = userHistory.get(username);
                    if (list != null && idStr != null && newStatus != null) {
                        for (DiseaseHistory h : list) {
                            if (h.id == Integer.parseInt(idStr)) {
                                h.status = newStatus;
                                break;
                            }
                        }
                        savePatientData(username);
                    }
                    sendJson(exchange, 200, "{\"success\":true}");
                    return;
                }

                sendJson(exchange, 200, "{\"success\":false,\"message\":\"Unknown action\"}");
                return;
            }

            sendJson(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
        }
    }

    static class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            clearSession(exchange);
            String response = "{\"success\":true}";
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> parseFormData(String body) {
        Map<String, String> params = new HashMap<>();
        if (body == null || body.isEmpty()) return params;
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    params.put(URLDecoder.decode(kv[0], "UTF-8"), URLDecoder.decode(kv[1], "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    params.put(kv[0], kv[1]);
                }
            }
        }
        return params;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
