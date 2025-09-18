package com.elitelogs.listeners;
import com.elitelogs.utils.LogRouter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
public class DeathListener implements Listener {
  private final LogRouter router;
  public DeathListener(LogRouter r){ this.router = r; }
  @EventHandler public void onDeath(PlayerDeathEvent e){
    router.write("combat", e.getDeathMessage());
  }
}