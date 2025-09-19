package com.elitelogs.listeners;
import com.elitelogs.utils.FileLogger;
import com.elitelogs.utils.LogRouter;
import com.elitelogs.utils.PlayerTracker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CommandListener implements Listener {
  private final Plugin plugin;
  private final LogRouter router;
  private final PlayerTracker tracker;

  public CommandListener(Plugin plugin, LogRouter router, PlayerTracker tracker){
    this.plugin = plugin;
    this.router = router;
    this.tracker = tracker;
  }

  @EventHandler public void onCmd(PlayerCommandPreprocessEvent e){
    Player p = e.getPlayer();
    String commandLine = e.getMessage();
    router.command(p.getUniqueId(), p.getName(), commandLine);
    if (tracker != null) {
      tracker.action(p, "[cmd] " + commandLine);
    }

    try {
        String day = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        FileLogger session = new FileLogger(new File(plugin.getDataFolder(), "logs/commands/sessions"));
        String ts = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String identity = "[" + p.getName() + "|" + p.getUniqueId() + "]";
        session.append("session-" + day + ".log", "[" + ts + "] " + identity + " " + commandLine);
    } catch (Throwable ignored){}
  }
}
