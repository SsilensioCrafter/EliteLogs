package com.elitelogs.listeners;
import com.elitelogs.utils.LogRouter;
import com.elitelogs.utils.PlayerTrackerHolder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {
  private final LogRouter router;

  public ChatListener(LogRouter r){ this.router = r; }

  @EventHandler public void onChat(AsyncPlayerChatEvent e){
    String message = e.getMessage();
    router.chat(e.getPlayer().getUniqueId(), e.getPlayer().getName(), message);
    if (PlayerTrackerHolder.get()!=null) {
      PlayerTrackerHolder.get().action(e.getPlayer(), "[chat] " + message);
    }
  }
}