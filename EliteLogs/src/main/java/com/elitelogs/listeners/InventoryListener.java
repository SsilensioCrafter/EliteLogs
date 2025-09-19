package com.elitelogs.listeners;
import com.elitelogs.utils.LogRouter;
import com.elitelogs.utils.PlayerTracker;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public class InventoryListener implements Listener {
  private final LogRouter router;
  private final PlayerTracker tracker;

  public InventoryListener(LogRouter r, PlayerTracker tracker){ this.router = r; this.tracker = tracker; }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onInv(InventoryClickEvent e){
    if (!(e.getWhoClicked() instanceof Player)) return;
    Player p = (Player) e.getWhoClicked();
    Inventory inv = e.getInventory();
    ItemStack item = e.getCurrentItem();
    InventoryView view = e.getView();
    String title = view != null && view.getTitle() != null ? view.getTitle() : inv.getType().name();
    String holder = (inv.getHolder()!=null)? inv.getHolder().getClass().getSimpleName() : "none";
    String itemStr = describeItem(item);
    String cursor = describeItem(e.getCursor());
    String msg = String.format("[inv] slot=%d action=%s click=%s gui=%s holder=%s item=%s cursor=%s",
            e.getSlot(), e.getAction(), e.getClick(), title, holder, itemStr, cursor);
    router.inventory(p.getUniqueId(), p.getName(), msg);
    if (tracker != null) tracker.action(p, msg);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDrag(InventoryDragEvent e) {
    if (!(e.getWhoClicked() instanceof Player)) return;
    Player p = (Player) e.getWhoClicked();
    String slots = e.getInventorySlots().toString();
    java.util.Collection<ItemStack> values = e.getNewItems().values();
    StringBuilder items = new StringBuilder();
    for (ItemStack stack : values) {
      if (items.length() > 0) {
        items.append(", ");
      }
      items.append(describeItem(stack));
    }
    String msg = String.format("[inv] drag type=%s slots=%s items=%s", e.getType(), slots,
            items.length() > 0 ? items.toString() : "none");
    router.inventory(p.getUniqueId(), p.getName(), msg);
    if (tracker != null) tracker.action(p, msg);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onCreative(InventoryCreativeEvent e) {
    if (!(e.getWhoClicked() instanceof Player)) return;
    Player p = (Player) e.getWhoClicked();
    String itemStr = describeItem(e.getCurrentItem());
    String msg = String.format("[inv] creative slot=%d item=%s", e.getSlot(), itemStr);
    router.inventory(p.getUniqueId(), p.getName(), msg);
    if (tracker != null) tracker.action(p, msg);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDrop(PlayerDropItemEvent e) {
    Player p = e.getPlayer();
    String itemStr = describeItem(e.getItemDrop().getItemStack());
    String msg = String.format("[inv] drop item=%s loc=%.1f,%.1f,%.1f", itemStr,
            e.getItemDrop().getLocation().getX(),
            e.getItemDrop().getLocation().getY(),
            e.getItemDrop().getLocation().getZ());
    router.inventory(p.getUniqueId(), p.getName(), msg);
    if (tracker != null) tracker.action(p, msg);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPickup(EntityPickupItemEvent e) {
    Entity entity = e.getEntity();
    if (!(entity instanceof Player)) {
      return;
    }
    Player p = (Player) entity;
    String itemStr = describeItem(e.getItem().getItemStack());
    org.bukkit.Location loc = e.getItem().getLocation();
    String msg = String.format("[inv] pickup item=%s loc=%.1f,%.1f,%.1f", itemStr,
            loc.getX(), loc.getY(), loc.getZ());
    router.inventory(p.getUniqueId(), p.getName(), msg);
    if (tracker != null) tracker.action(p, msg);
  }

  private String describeItem(ItemStack stack) {
    if (stack == null || stack.getType() == org.bukkit.Material.AIR) {
      return "AIR";
    }
    StringBuilder sb = new StringBuilder(stack.getType().name());
    sb.append(" x").append(stack.getAmount());
    if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
      sb.append(" name=\"").append(stack.getItemMeta().getDisplayName()).append('\"');
    }
    if (stack.getEnchantments() != null && !stack.getEnchantments().isEmpty()) {
      sb.append(" ench=").append(stack.getEnchantments());
    }
    return sb.toString();
  }
}
