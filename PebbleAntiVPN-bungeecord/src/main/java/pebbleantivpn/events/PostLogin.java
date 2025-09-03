package pebbleantivpn.events;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import pebbleantivpn.data.BungeeHandler;
import pebbleantivpn.pebbleantivpn.BungeeProxyChecker;
import pebbleantivpn.pebbleantivpn.PebbleAntiVPNBungeeCord;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PostLogin implements Listener {

    private final BungeeProxyChecker proxyChecker;
    private final PebbleAntiVPNBungeeCord main;
    private final BungeeHandler handler;

    public PostLogin(PebbleAntiVPNBungeeCord plugin) {
        this.main = plugin;
        this.proxyChecker = plugin.getProxyChecker();
        this.handler = plugin.getHandler();
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent e) {
        if (!this.main.isPluginEnabled() || e.getPlayer().hasPermission(this.handler.getConfig("bypass-permission", false).toString()))
            return;

        String IP = e.getPlayer().getSocketAddress().toString().split(":")[0].replace("/", "");
        String dataIP = IP.replace(".", "_");
        String name = e.getPlayer().getName();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        // async thingy
        ProxyServer.getInstance().getScheduler().runAsync(this.main, () -> {
            boolean proxyResult = false;
            try {
                proxyResult = this.proxyChecker.isProxy(IP, name);
            } catch (Throwable ex) {
                this.main.getLogger().warning("Proxy check failed for " + IP + " (" + name + "): " + ex.getMessage());
                ex.printStackTrace();
            }

            if (proxyResult) {
                String country = this.handler.getData("details." + dataIP + ".country.name").toString();
                String countryCode = this.handler.getData("details." + dataIP + ".country.code").toString();

                String kickMessage = this.handler.getConfig("block-message", true).toString().replace("%ip%", IP).replace("%player%", name).replace("%time%", dtf.format(now)).replace("%country%", country).replace("%countryCode%", countryCode);
                if (ProxyServer.getInstance().getPlayer(name) != null) {
                    ProxyServer.getInstance().getPlayer(name).disconnect(new TextComponent(kickMessage));
                }
                return;
            }

            if ((boolean) this.handler.getConfig("blocked-countries.enabled", false)) {
                String country = this.handler.getData("details." + dataIP + ".country.name").toString();
                String countryCode = this.handler.getData("details." + dataIP + ".country.code").toString();
                String kickMessage = this.handler.getConfig("blocked-countries.kick-message", true).toString().replace("%ip%", IP).replace("%player%", name).replace("%time%", dtf.format(now)).replace("%country%", country).replace("%countryCode%", countryCode);

                List<?> BlockedCountries = this.handler.getList("blocked-countries.countries");
                if (BlockedCountries.contains(this.handler.getData("details." + dataIP + ".country.name")))
                    if (ProxyServer.getInstance().getPlayer(name) != null) ProxyServer.getInstance().getPlayer(name).disconnect(new TextComponent(kickMessage));
                    else if (BlockedCountries.contains(this.handler.getData("details." + dataIP + ".country.code"))) {
                        if (ProxyServer.getInstance().getPlayer(name) != null) ProxyServer.getInstance().getPlayer(name).disconnect(new TextComponent(kickMessage));
                    }
            } else if ((boolean) this.handler.getConfig("users-per-ip.enabled", false)) {
                int max = (int) this.handler.getConfig("users-per-ip.limit", false);
                int connection = this.handler.getConnections(IP);

                if (max <= 0) {
                    this.main.getLogger().warning("§cThe minimum amount of connections per IP must be over 0.");
                } else if (connection >= max) {
                    if (ProxyServer.getInstance().getPlayer(name) != null) ProxyServer.getInstance().getPlayer(name).disconnect(new TextComponent(this.handler.getConfig("users-per-ip.kick-message", true).toString()));
                } else {
                    this.handler.addConnection(IP);
                }
            }
        });
    }

}