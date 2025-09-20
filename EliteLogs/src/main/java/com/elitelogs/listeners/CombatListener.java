package com.elitelogs.listeners;
import com.elitelogs.logging.LogRouter;
import com.elitelogs.compat.ServerCompat;
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

    if (e.getEntity() instanceof Player) {
      Player victim = (Player) e.getEntity();
      String cause = e.getEntity().getLastDamageCause() != null ? e.getEntity().getLastDamageCause().getCause().name() : "UNKNOWN";
      router.combat(victim.getUniqueId(), victim.getName(), "[death] cause=" + cause + " " + locStr);
    }

    Player killer = e.getEntity().getKiller();
    if (killer != null) {
      String target;
      if (e.getEntity() instanceof Player) {
        target = ((Player) e.getEntity()).getName();
      } else {
        target = e.getEntity().getType().name();
      }
      org.bukkit.inventory.ItemStack stack = ServerCompat.getHeldItem(killer);
      String weapon = stack != null ? stack.getType().name() : "UNKNOWN";
      router.combat(killer.getUniqueId(), killer.getName(), "[kill] target=" + target + " weapon=" + weapon + " " + locStr);
    }
  }
}