package com.elitelogs.listeners;
import com.elitelogs.utils.LogRouter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
public class ChatListener implements Listener {
  private final LogRouter router;
  public ChatListener(LogRouter r){ this.router = r; }
  @EventHandler public void onChat(AsyncPlayerChatEvent e){
    String msg = "[chat] [" + e.getPlayer().getName() + "] " + e.getMessage();
    router.chat("[" + e.getPlayer().getName() + "] " + e.getMessage());
    if (com.elitelogs.utils.PlayerTrackerHolder.get()!=null) com.elitelogs.utils.PlayerTrackerHolder.get().action(e.getPlayer(), msg);
  }
}