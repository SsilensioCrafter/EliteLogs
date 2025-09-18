package com.elitelogs.listeners;
import com.elitelogs.utils.LogRouter;
import com.elitelogs.utils.PlayerTrackerHolder;
import com.elitelogs.utils.FileLogger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.entity.Player;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CommandListener implements Listener {
  private final LogRouter router;
  public CommandListener(LogRouter r){ this.router = r; }

  @EventHandler public void onCmd(PlayerCommandPreprocessEvent e){
    Player p = e.getPlayer();
    String msg = "[" + p.getName() + "] " + e.getMessage();
    router.command(msg);
    if (PlayerTrackerHolder.get()!=null) PlayerTrackerHolder.get().action(p, "[cmd] " + msg);

    // session-level commands log
    try {
        String day = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        FileLogger session = new FileLogger(new File(routerPluginData(), "logs/commands/sessions"));
        session.append("session-" + day + ".log", "[" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + msg);
    } catch (Throwable ignored){}
  }

  private File routerPluginData(){
      try {
          java.lang.reflect.Field f = LogRouter.class.getDeclaredField("plugin");
          f.setAccessible(true);
          org.bukkit.plugin.Plugin pl = (org.bukkit.plugin.Plugin) f.get(router);
          return pl.getDataFolder();
      } catch(Exception e){ return new File("plugins/EliteLogs"); }
  }
}
