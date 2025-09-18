package com.elitelogs.listeners;
import com.elitelogs.utils.LogRouter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class CombatListener implements Listener {
  private final LogRouter router;

  public CombatListener(LogRouter r){ this.router = r; }

  @EventHandler public void onEntityDeath(EntityDeathEvent e){
    Location loc = e.getEntity().getLocation();
    String locStr = String.format("at %d,%d,%d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

    if (e.getEntity() instanceof Player victim) {
      String cause = e.getEntity().getLastDamageCause() != null ? e.getEntity().getLastDamageCause().getCause().name() : "UNKNOWN";
      router.combat(victim.getUniqueId(), victim.getName(), "[death] cause=" + cause + " " + locStr);
    }

    Player killer = e.getEntity().getKiller();
    if (killer != null) {
      String target = e.getEntity() instanceof Player ? ((Player) e.getEntity()).getName() : e.getEntity().getType().name();
      String weapon = killer.getInventory().getItemInMainHand().getType().name();
      router.combat(killer.getUniqueId(), killer.getName(), "[kill] target=" + target + " weapon=" + weapon + " " + locStr);
    }
  }
}