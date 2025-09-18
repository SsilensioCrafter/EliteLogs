package com.elitelogs.listeners;
import com.elitelogs.utils.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;
import java.lang.reflect.Method;
public class JoinQuitListener implements Listener {
  private final LogRouter router;
  public JoinQuitListener(LogRouter r){ this.router = r; }
  @EventHandler public void onJoin(PlayerJoinEvent e){
    Player p = e.getPlayer();
    String ip = p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : "unknown";
    String region = GeoIPResolver.resolve(ip);
    String brand = tryClientBrand(p);
    router.info("[join] " + p.getName());
    PlayerTracker pt = PlayerTrackerHolder.get();
    if (pt != null) pt.onLogin(p, ip + " " + region + (brand!=null?(" Brand="+brand):""));
    router.player(p.getName(), "[login] " + ip + " " + region + " UUID=" + p.getUniqueId() + (brand!=null?(" Brand="+brand):""));
  }
  @EventHandler public void onQuit(PlayerQuitEvent e){
    Player p = e.getPlayer();
    PlayerTracker pt = PlayerTrackerHolder.get();
    if (pt != null) pt.onLogout(p);
    router.info("[quit] " + p.getName());
    router.player(p.getName(), "[logout]");
  }
  private String tryClientBrand(Player p){
    try { Method m = p.getClass().getMethod("getClientBrandName"); Object v = m.invoke(p); return v != null ? v.toString() : null; }
    catch (Throwable t){ return null; }
  }
}