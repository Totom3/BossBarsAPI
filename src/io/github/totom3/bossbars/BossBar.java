package io.github.totom3.bossbars;

import com.google.common.primitives.Floats;
import static java.lang.Math.toRadians;
import java.lang.ref.WeakReference;
import net.minecraft.server.v1_8_R3.DataWatcher;
import net.minecraft.server.v1_8_R3.Entity;
import net.minecraft.server.v1_8_R3.EntityWither;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityDestroy;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityMetadata;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityTeleport;
import net.minecraft.server.v1_8_R3.PacketPlayOutSpawnEntityLiving;
import net.minecraft.server.v1_8_R3.PlayerConnection;
import net.minecraft.server.v1_8_R3.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 *
 * @author Totom3
 */
public class BossBar {    
    private static final float DEFAULT_PERCENT = 100;
    private static final String DEFAULT_MESSAGE = "";

    static float check(float percent) {
	if (percent < 0 || percent > 100) {
	    throw new IllegalArgumentException("Percent must be between 0 and 100 (both inclusive), but got: " + percent);
	}
	if (!Floats.isFinite(percent)) {
	    throw new IllegalArgumentException("Percent must be finite; cannot be " + percent);
	}

	return percent;
    }

    private static String fixMsg(String msg) {
	if (msg == null) {
	    return "";
	}

	if (msg.length() > 64) {
	    return msg.substring(0, 63);
	}
	return msg;
    }

    private static WeakReference<Player> makeRef(Player p) {
	if (p == null) {
	    throw new NullPointerException("Player cannot be null");
	}
	return new WeakReference<>(p);
    }

    private static void sendPacket(Player p, Packet packet) {
	((CraftPlayer) p).getHandle().playerConnection.sendPacket(packet);
    }

    private final WeakReference<Player> playerRef;
    private String message;
    private float percent;
    private int entityID;

    private Entity bossEntity;
    private PlayerListener listener;
    private boolean dirty = true;
    private Location oldLocation;

    /**
     * Creates a new {@code BossBar}, with an empty message and full health.
     *
     * @param player the player to receive the {@code BossBar}.
     */
    protected BossBar(Player player) {
	this.message = DEFAULT_MESSAGE;
	this.percent = DEFAULT_PERCENT;
	this.playerRef = makeRef(player);
	registerListener();
    }

    /**
     * Creates a new {@code BossBar} with a specified message and full health.
     *
     * @param player  the player to receive the {@code BossBar}.
     * @param message the message of the bar. If {@code null}, will be converted
     *                to an empty string. If the string is over 64 characters,
     *                it will be truncated.
     */
    protected BossBar(Player player, String message) {
	this.playerRef = makeRef(player);
	this.message = fixMsg(message);
	this.percent = DEFAULT_PERCENT;
	registerListener();
    }

    /**
     * Creates a new {@code BossBar} with the following arguments:
     *
     * @param player  the player to receive the {@code BossBar}.
     * @param message the message of the bar. If {@code null}, will be converted
     *                to an empty string.
     * @param percent the percentage of health. Must be between 0 and 100, both
     *                inclusive.
     *
     * @throws IllegalArgumentException if {@code percent} is not within the
     *                                  range specified above.
     */
    protected BossBar(Player player, String message, float percent) {
	this.playerRef = makeRef(player);
	this.message = fixMsg(message);
	this.percent = check(percent);
	registerListener();
    }

    /**
     * Returns the message to be displayed in this {@code BossBar}.
     *
     * @return the message of this {@code BossBar}.
     */
    public String getMessage() {
	return message;
    }

    /**
     * Sets the message of this {@code BossBar}. The client will receive the
     * update at the next update loop.
     *
     * @param message the new message to set. If {@code null}, will be converted
     *                to an empty string.
     *
     */
    public void setMessage(String message) {
	String old = this.message;
	this.message = fixMsg(message);
	this.dirty = dirty || old.equals(this.message);
    }

    /**
     * Returns the percentage of health to be displayed in this {@code BossBar}.
     *
     * @return the percentage of health of this {@code BossBar}.
     */
    public float getPercent() {
	return percent;
    }

    /**
     * Sets the percentage of health of this {@code BossBar}. The client will
     * receive the update at the next update loop.
     *
     * @param percent the new percentage to set. Must be between 0 and 100, both
     *                inclusive.
     *
     * @throws IllegalArgumentException if {@code percent} is not within the
     *                                  range specified above.
     */
    public void setPercent(float percent) {
	float old = this.percent;
	this.percent = check(percent);
	this.dirty = dirty || old != percent;
    }

    /**
     * Returns the location of the underlying boss, creating the client-side
     * effect of the {@code BossBar} for the client. Note that the entity does
     * not exist for the server; normal {@code Bukkit} methods will not be able
     * to find it.
     *
     * @return the location of the boss.
     */
    public Location getBossLocation() {
	Player p = getPlayer();
	Location loc = p.getEyeLocation();

	// Boss is 40 blocks away from player, directly in the direction it is facing.
	final float distance = 30;
	float yaw = loc.getYaw();
	double pitch = toRadians(loc.getPitch());
	double fixedYaw = toRadians(yaw + 90);

	// the more the player looks down/up, the less the boss should be far, on the x and z axis
	// cos(-90) = 0, cos(90) = 0, cos(0) = 1
	double cosPitch = Math.cos(pitch);

	loc.add(
		Math.cos(fixedYaw) * distance * cosPitch,
		// remove 2 blocks to center the wither
		Math.sin(-pitch) * distance - 2,
		Math.sin(fixedYaw) * distance * cosPitch
	);

	// make sure the wither is not underground, because it would stop rendering
	loc.setY(Math.max(1, loc.getY()));

	return loc;
    }

    private World getWorld() {
	return ((CraftWorld) getPlayer().getWorld()).getHandle();
    }

    /**
     * Sets the message and the percentage of health of this {@code BossBar}.
     * The client will receive the update at the next update loop.
     *
     * @param message the new message to set. If {@code null}, will be converted
     *                to an empty string.
     * @param percent the new percentage to set. Must be between 0 and 100, both
     *                inclusive.
     */
    public void setMessageAndPercent(String message, float percent) {
	String oldMsg = this.message;
	float oldPercent = this.percent;
	this.message = fixMsg(message);
	this.percent = check(percent);
	this.dirty = dirty || (!oldMsg.equals(this.message) || oldPercent != this.percent);
    }

    public void update() {
	Player p = getPlayer();
	if (dirty) {
	    // Recreate dragon
	    PlayerConnection conn = ((CraftPlayer)p).getHandle().playerConnection;
	    conn.sendPacket(makeDestroyPacket());
	    conn.sendPacket(makeSpawnPacket());
	    conn.sendPacket(makeMetaPacket());
	    dirty = false;
	} else { // no need to teleport when spawning the boss
	    if (hasLocationChanged()) {
		teleport();
	    }
	}
    }

    public void teleport() {
	sendPacket(getPlayer(), makeTeleportPacket(getBossLocation()));
    }

    public boolean remove() {
	Player p = playerRef.get();
	if (p == null) {
	    return false;
	}
	sendPacket(p, makeDestroyPacket());
	bossEntity = null;
	unregisterListener();
	return true;
    }

    private float getHealth() {
	return 3 * getPercent();
    }

    protected Player getPlayer() throws IllegalStateException {
	Player p = playerRef.get();
	if (p == null) {
	    throw new IllegalStateException("Player is not online");
	}
	return p;
    }

    private PacketPlayOutSpawnEntityLiving makeSpawnPacket() {
	EntityWither newBossEntity = new EntityWither(getWorld());
	Location loc = getBossLocation();
	newBossEntity.setLocation(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
	newBossEntity.setCustomName(getMessage());
	newBossEntity.setHealth(getHealth());
	this.entityID = newBossEntity.getId();
	this.bossEntity = newBossEntity;
	return new PacketPlayOutSpawnEntityLiving(newBossEntity);
    }

    private PacketPlayOutEntityDestroy makeDestroyPacket() {
	return new PacketPlayOutEntityDestroy(entityID);
    }

    private PacketPlayOutEntityMetadata makeMetaPacket() {
	return new PacketPlayOutEntityMetadata(entityID, makeWatcher(), true);
    }

    private PacketPlayOutEntityTeleport makeTeleportPacket(Location newLocation) {
	return new PacketPlayOutEntityTeleport(
		entityID,
		(int) (newLocation.getX() * 32),
		(int) (newLocation.getY() * 32),
		(int) (newLocation.getZ() * 32),
		(byte) ((int) newLocation.getYaw() * 256 / 360), // yaw/360 = ?/256
		(byte) ((int) newLocation.getPitch() * 256 / 360), // pitch/360 = ?/256
		false // is on ground
	);
    }

    private DataWatcher makeWatcher() {
	DataWatcher dataWatcher = new DataWatcher(bossEntity);
	// FIXME: uncomment next line
	//dataWatcher.a(0, (byte) 32); // invisible
	dataWatcher.a(6, getHealth()); // health
	//dataWatcher.a(7, 0);
	//dataWatcher.a(8, (byte) 0);
	dataWatcher.a(2, getMessage()); // fixed name
	dataWatcher.a(3, (byte) 1); // fixed show name
	return dataWatcher;
    }

    private void registerListener() {
	listener = new PlayerListener();
	Bukkit.getPluginManager().registerEvents(listener, BossBarsAPI.get());
    }

    private void unregisterListener() {
	PlayerQuitEvent.getHandlerList().unregister(listener);
	PlayerKickEvent.getHandlerList().unregister(listener);
	PlayerTeleportEvent.getHandlerList().unregister(listener);
	PlayerRespawnEvent.getHandlerList().unregister(listener);
	listener = null;
    }

    private boolean hasLocationChanged() {
	Player p = getPlayer();
	Location newLoc = p.getLocation();
	
	boolean changed;
	if (oldLocation == null) {
	    changed = true;
	} else {
	    changed = !oldLocation.equals(p.getLocation());
	}
	
	this.oldLocation = newLoc;
	return changed;
    }

    private class PlayerListener implements Listener {

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void on(PlayerQuitEvent event) {
	    onQuit(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void on(PlayerKickEvent event) {
	    onQuit(event.getPlayer());
	}

	private void onQuit(Player p) {
	    if (!p.equals(playerRef.get())) {
		return;
	    }

	    unregisterListener();
	    remove();
	    BossBarsAPI.getBossBars().remove(p);
	}
    }
}
