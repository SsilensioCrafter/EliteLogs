package com.elitelogs.listeners;
import com.elitelogs.utils.LogRouter;
import com.elitelogs.utils.PlayerTrackerHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class InventoryListener implements Listener {
  private final LogRouter router;

  public InventoryListener(LogRouter r){ this.router = r; }

  @EventHandler public void onInv(InventoryClickEvent e){
    if (!(e.getWhoClicked() instanceof Player)) return;
    Player p = (Player) e.getWhoClicked();
    Inventory inv = e.getInventory();
    ItemStack item = e.getCurrentItem();
    String title = inv.getType().name();
    String holder = (inv.getHolder()!=null)? inv.getHolder().getClass().getSimpleName() : "none";
    String itemStr = (item!=null)? (item.getType().name() + " x" + item.getAmount()) : "AIR";
    String msg = String.format("[inv] slot=%d action=%s click=%s gui=%s holder=%s item=%s",
            e.getSlot(), e.getAction(), e.getClick(), title, holder, itemStr);
    router.inventory(p.getUniqueId(), p.getName(), msg);
    if (PlayerTrackerHolder.get()!=null) PlayerTrackerHolder.get().action(p, msg);
  }
}