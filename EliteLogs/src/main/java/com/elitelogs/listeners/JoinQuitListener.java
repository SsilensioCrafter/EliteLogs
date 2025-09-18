package com.elitelogs.listeners;
import com.elitelogs.utils.GeoIPResolver;
import com.elitelogs.utils.LogRouter;
import com.elitelogs.utils.PlayerTracker;
import com.elitelogs.utils.PlayerTrackerHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Method;

public class JoinQuitListener implements Listener {
  private final LogRouter router;

  public JoinQuitListener(LogRouter r){ this.router = r; }

  @EventHandler public void onJoin(PlayerJoinEvent e){
    Player p = e.getPlayer();
    String ip = p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : "unknown";
    String region = GeoIPResolver.resolve(ip);
    String brand = tryClientBrand(p);
    String brandPart = brand != null ? " brand=" + brand : "";

    router.info(p.getUniqueId(), p.getName(), "[join] ip=" + ip + " region=" + region + brandPart);

    PlayerTracker pt = PlayerTrackerHolder.get();
    if (pt != null) pt.onLogin(p, ip + " " + region + brandPart);

    router.player(p.getUniqueId(), p.getName(), "[login] ip=" + ip + " region=" + region + brandPart);
  }

  @EventHandler public void onQuit(PlayerQuitEvent e){
    Player p = e.getPlayer();
    PlayerTracker pt = PlayerTrackerHolder.get();
    if (pt != null) pt.onLogout(p);
    router.info(p.getUniqueId(), p.getName(), "[quit]");
    router.player(p.getUniqueId(), p.getName(), "[logout]");
  }

  private String tryClientBrand(Player p){
    try { Method m = p.getClass().getMethod("getClientBrandName"); Object v = m.invoke(p); return v != null ? v.toString() : null; }
    catch (Throwable t){ return null; }
  }
}