package pebbleantivpn.pebbleantivpn;

import pebbleantivpn.SpigotAlerts.MainAlert;
import pebbleantivpn.SpigotAlerts.WebhookAlert;
import pebbleantivpn.data.SpigotHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;

public class SpigotProxyChecker {

    private final SpigotHandler handler;
    private final WebhookAlert webhook;
    private final MainAlert spigotAlert;


    public SpigotProxyChecker(PebbleAntiVPNSpigot plugin) {
        this.handler = plugin.getHandler();
        this.webhook = plugin.getWebhook();
        this.spigotAlert = plugin.getSpigotAlert();
    }

    public boolean isProxy(String IP, String name) {
        String dataIP = IP.replace(".", "_");
        if (this.handler.isSet("details." + dataIP)) {
            Object cached = this.handler.getData("details." + dataIP + ".proxy");
            if (cached instanceof Boolean) return (Boolean) cached;
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        HttpURLConnection http = null;
        InputStream inputStream = null;
        try {
            URL url = new URL("http://ip-api.com/json/" + IP + "?fields=country,proxy,countryCode");
            http = (HttpURLConnection) url.openConnection();

            http.setRequestProperty("Accept", "application/json");
            http.setConnectTimeout(2000); // U CAN CHYANGE TIMEOUT HERE
            http.setReadTimeout(2000); // and read timeout too
            http.setRequestMethod("GET");
            http.setDoInput(true);

            int responseCode = http.getResponseCode();
            if (200 <= responseCode && responseCode <= 299) {
                inputStream = http.getInputStream();
            } else {
                inputStream = http.getErrorStream();
                if (inputStream == null) {
                    // then nyot proxy
                    return false;
                }
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String currentLine;
                while ((currentLine = in.readLine()) != null) {
                    response.append(currentLine);
                }
            }

            String body = response.toString();

            String country = extractJsonString(body, "country");
            String countryCode = extractJsonString(body, "countryCode");
            Boolean proxy = extractJsonBoolean(body, "proxy");

            if (proxy == null) {
                proxy = false;
            }
            if (country == null) country = "unknown";
            if (countryCode == null) countryCode = "??";

            this.handler.writeData("details." + dataIP + ".proxy", proxy);
            this.handler.writeData("details." + dataIP + ".country.name", country);
            this.handler.writeData("details." + dataIP + ".country.code", countryCode);

            if (proxy) {
                // alyerts
                try {
                    this.spigotAlert.execute(IP, name, country, countryCode, dtf.format(now));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    this.webhook.discordAlert(IP, name, country, countryCode, dtf.format(now));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return proxy;
        } catch (SocketTimeoutException ste) {
            // loginah timeout
            System.err.println("PebbleAntiVPN: proxy check timed out for IP " + IP);
            return false;
        } catch (IOException ioe) {
            System.err.println("PebbleAntiVPN: I/O error during proxy check for IP " + IP + " : " + ioe.getMessage());
            return false;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {}
            }
            if (http != null) {
                http.disconnect();
            }
        }
    }

    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;
        int start = idx + pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }

    private static Boolean extractJsonBoolean(String json, String key) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;
        int start = idx + pattern.length();
        int endComma = json.indexOf(",", start);
        int endBrace = json.indexOf("}", start);
        int end;
        if (endComma == -1 && endBrace == -1) {
            end = json.length();
        } else if (endComma == -1) {
            end = endBrace;
        } else if (endBrace == -1) {
            end = endComma;
        } else {
            end = Math.min(endComma, endBrace);
        }
        String token = json.substring(start, end).trim();
        // strip quotes if any
        token = token.replaceAll("^\"|\"$", "");
        if ("true".equalsIgnoreCase(token)) return true;
        if ("false".equalsIgnoreCase(token)) return false;
        return null;
    }
}