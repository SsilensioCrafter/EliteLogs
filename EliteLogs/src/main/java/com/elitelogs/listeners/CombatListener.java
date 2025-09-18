package com.elitelogs.listeners;
import com.elitelogs.utils.LogRouter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.entity.Player;
public class CombatListener implements Listener {
  private final LogRouter router;
  public CombatListener(LogRouter r){ this.router = r; }
  @EventHandler public void onEntityDeath(EntityDeathEvent e){
    if (e.getEntity() instanceof Player) {
      Player p = (Player) e.getEntity();
      router.write("combat", "[death] " + p.getName());
    } else if (e.getEntity().getKiller() != null){
      Player k = e.getEntity().getKiller();
      router.write("combat", "[kill] " + k.getName() + " -> " + e.getEntity().getType().name());
    }
  }
}