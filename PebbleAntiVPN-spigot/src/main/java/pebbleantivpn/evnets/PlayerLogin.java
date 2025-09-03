package pebbleantivpn.evnets;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import pebbleantivpn.data.SpigotHandler;
import pebbleantivpn.pebbleantivpn.PebbleAntiVPNSpigot;
import pebbleantivpn.pebbleantivpn.SpigotProxyChecker;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PlayerLogin implements Listener {

    private final SpigotProxyChecker proxyChecker;
    private final PebbleAntiVPNSpigot main;
    private final SpigotHandler handler;

    public PlayerLogin(PebbleAntiVPNSpigot plugin) {
        this.main = plugin;
        this.proxyChecker = plugin.getProxyChecker();
        this.handler = plugin.getHandler();
    }

    @EventHandler
    public void onPlayerLogin(AsyncPlayerPreLoginEvent e) throws IOException {
        if (!this.main.isPluginEnabled()) return;

        String IP = e.getAddress().getHostAddress();
        String dataIP = IP.replace(".", "_");
        String name = e.getName();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        String country;
        String countryCode;

        if (this.proxyChecker.isProxy(IP, name)) {
            country = this.handler.getData("details." + dataIP + ".country.name").toString();
            countryCode = this.handler.getData("details." + dataIP + ".country.name").toString();

            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, this.handler.getConfig("block-message", true).toString().replace("%ip%", IP).replace("%player%", name).replace("%time%", dtf.format(now)).replace("%country%", country).replace("%countryCode%", countryCode));
        } else if ((boolean) this.handler.getConfig("blocked-countries.enabled", false)) {
            country = this.handler.getData("details." + dataIP + ".country.name").toString();
            countryCode = this.handler.getData("details." + dataIP + ".country.name").toString();
            List<?> BlockedCountries = this.handler.getList("blocked-countries.countries");

            if (BlockedCountries.contains(this.handler.getData("details." + dataIP + ".country.name")))
                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, this.handler.getConfig("blocked-countries.kick-message", true).toString().replace("%ip%", IP).replace("%player%", name).replace("%time%", dtf.format(now)).replace("%country%", country).replace("%countryCode%", countryCode));
            else if (BlockedCountries.contains(this.handler.getData("details." + dataIP + ".country.code"))) {
                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, this.handler.getConfig("blocked-countries.kick-message", true).toString().replace("%ip%", IP).replace("%player%", name).replace("%time%", dtf.format(now)).replace("%country%", country).replace("%countryCode%", countryCode));
            }
        } else if ((boolean) this.handler.getConfig("users-per-ip.enabled", false)) {
            int max = (int) this.handler.getConfig("users-per-ip.limit", false);
            int connection = this.handler.getConnections(IP);

            if (max <= 0) {
                this.main.getServer().getConsoleSender().sendMessage("§cThe minimum amount of connections per IP must be over 0.");
            } else if (connection >= max) {
                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, this.handler.getConfig("users-per-ip.kick-message", true).toString());
            } else {
                this.handler.addConnection(IP);
            }
        }
    }
}
