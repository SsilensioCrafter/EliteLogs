package com.elitelogs.listeners;

import com.elitelogs.utils.LogRouter;
import com.elitelogs.utils.ServerCompat;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class DeathListener implements Listener {
  private final LogRouter router;

  public DeathListener(LogRouter r){ this.router = r; }

  @EventHandler public void onDeath(PlayerDeathEvent e){
    String message = ServerCompat.describeDeathMessage(e);
    router.combat(e.getEntity().getUniqueId(), e.getEntity().getName(), "[death-message] " + message);
  }
}
