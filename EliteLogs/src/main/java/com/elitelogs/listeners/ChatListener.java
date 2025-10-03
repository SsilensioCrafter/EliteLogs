package com.elitelogs.listeners;
import com.elitelogs.logging.LogRouter;
import com.elitelogs.players.PlayerTracker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {
  private final LogRouter router;
  private final PlayerTracker tracker;

  public ChatListener(LogRouter r, PlayerTracker tracker){ this.router = r; this.tracker = tracker; }

  @EventHandler public void onChat(AsyncPlayerChatEvent e){
    String message = e.getMessage();
    router.chat(e.getPlayer().getUniqueId(), e.getPlayer().getName(), message);
    if (tracker != null) {
      tracker.action(e.getPlayer(), "[chat] " + message);
    }
  }
}
