package io.github.totom3.bossbars;

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.WeakHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class BossBarsAPI extends JavaPlugin {

    private static BossBarsAPI instance;

    public static BossBarsAPI get() {
	return instance;
    }

    public static Map<Player, BossBar> getBossBars() {
	return get().getAll();
    }

    public static BossBar getOrCreate(Player player) {
	Map<Player, BossBar> bars = getBossBars();
	BossBar bossBar = bars.get(check(player));
	if (bossBar == null) {
	    bossBar = new BossBar(player);
	    bars.put(player, bossBar);
	}
	return bossBar;
    }

    public static BossBar getIfPresent(Player player) {
	return getBossBars().get(check(player));
    }

    public static BossBar set(Player player, String message, float percentage) {
	Map<Player, BossBar> all = getBossBars();
	BossBar bar = all.get(player);
	if (bar == null) {
	    bar = new BossBar(player, message, percentage);
	    all.put(player, bar);
	    return bar;
	}
	bar.setMessageAndPercent(message, percentage);
	return bar;
    }

    public static BossBar set(Player player, BossBar bossBarCopy) {
	return set(player, bossBarCopy.getMessage(), bossBarCopy.getPercent());
    }

    public static BossBar remove(Player player) {
	BossBar removed = getBossBars().remove(check(player));
	if (removed != null) {
	    removed.remove();
	}
	return removed;
    }

    public static void removeAll() {
	Map<Player, BossBar> bars = getBossBars();
	for (BossBar bar : bars.values()) {
	    bar.remove();
	}
	bars.clear();
    }

    private static Player check(Player player) {
	if (player == null) {
	    throw new NullPointerException("Player cannot be null");
	}
	return player;
    }

    private BukkitTask updaterTask;
    private final BossBarsUpdater updater;
    private final Map<Player, BossBar> bossBars;

    public BossBarsAPI() {
	instance = this;

	this.bossBars = new BossBarsMap();
	this.updater = new BossBarsUpdater();
    }

    @Override
    public void onEnable() {
	if (!bossBars.isEmpty()) {
	    startTask();
	}
    }

    @Override
    public void onDisable() {
	cancelTask();
	bossBars.clear();
    }

    public Map<Player, BossBar> getAll() {
	return bossBars;
    }

    private void startTask() {
	updaterTask = Bukkit.getScheduler().runTaskTimer(this, updater, 0, 5);
    }

    private void cancelTask() {
	if (updaterTask != null) {
	    updaterTask.cancel();
	    updaterTask = null;
	}
    }

    private class BossBarsUpdater implements Runnable {

	@Override
	public void run() {
	    if (bossBars.isEmpty()) {
		cancelTask();
		return;
	    }

	    for (Iterator<Entry<Player, BossBar>> it = bossBars.entrySet().iterator(); it.hasNext();) {
		Entry<Player, BossBar> entry = it.next();
		BossBar bar = entry.getValue();
		if (!entry.getKey().isOnline()) {
		    bar.remove();
		    it.remove();
		}
		bar.update();
	    }
	}

    }

    private class BossBarsMap extends AbstractMap<Player, BossBar> {

	WeakHashMap<Player, BossBar> deleguate = new WeakHashMap<>();

	@Override
	public int size() {
	    return deleguate.size();
	}

	@Override
	public BossBar get(Object key) {
	    return deleguate.get(key);
	}

	@Override
	public boolean containsKey(Object key) {
	    if (!(key instanceof Player)) {
		return false;
	    }
	    return deleguate.containsKey(key);
	}

	@Override
	public BossBar put(Player key, BossBar value) {
	    if (key == null) {
		throw new NullPointerException("Player cannot be null");
	    }

	    if (value == null) {
		return remove(key);
	    }

	    if (!key.isOnline()) {
		throw new IllegalArgumentException("Player " + key.getName() + " is not online.");
	    }

	    if (deleguate.isEmpty()) {
		startTask();
	    }

	    return deleguate.put(key, value);
	}

	@Override
	public BossBar remove(Object key) {
	    BossBar removed = deleguate.remove(key);

	    if (deleguate.isEmpty()) {
		cancelTask();
	    }

	    return removed;
	}

	@Override
	public void clear() {
	    deleguate.clear();
	    cancelTask();
	}

	@Override
	public boolean containsValue(Object value) {
	    return value instanceof BossBar && deleguate.containsValue(value);

	}

	@Override
	public Set<Entry<Player, BossBar>> entrySet() {
	    return deleguate.entrySet();
	}
    }
}
