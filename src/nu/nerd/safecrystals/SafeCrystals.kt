package nu.nerd.safecrystals

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World.Environment
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.LocalPlayer
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.bukkit.WorldGuardPlugin

/**
 * Prevent Ender Crystals from exploding and breaking blocks or damaging
 * entities.
 *
 * Players who have permission to build in a WorldGuard region can break the
 * crystals into dropped items.
 *
 * WorldGuard build permission is checked before allowing a player to place an
 * Ender Crystal in a region.
 */
class SafeCrystals : JavaPlugin(), Listener {
    /**
     * Singleton-like reference to this plugin.
     */
    companion object {
        lateinit var PLUGIN: SafeCrystals
    }

    /**
     * Configuration instance.
     */
    companion object {
        val CONFIG = Configuration()
    }

    private lateinit var worldGuard: WorldGuardPlugin

    /**
     * @see org.bukkit.plugin.java.JavaPlugin.onEnable
     */
    override fun onEnable() {
        PLUGIN = this
        saveDefaultConfig()
        CONFIG.reload()

        worldGuard = server.pluginManager.getPlugin("WorldGuard") as WorldGuardPlugin
        Bukkit.getPluginManager().registerEvents(this, this)
    }

    /**
     * Prevent Ender Crystals from exploding, except in the case of those on
     * bedrock in the end.
     */
    @EventHandler(ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        val entity = event.entity
        if (entity.type == EntityType.ENDER_CRYSTAL && !isDragonFightCrystal(entity.location)) {
            event.isCancelled = true
        }
    }

    /**
     * Prevent Ender Crystals from being damaged by other entities.
     *
     * If the damager is a player who can build, drop the crystal as an item.
     * Projectiles are handled the same as the player who shot them.
     */
    @EventHandler(ignoreCancelled = true)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val entity = event.entity
        if (entity.type == EntityType.ENDER_CRYSTAL) {
            if (isDragonFightCrystal(entity.location)) {
                // Vanilla handling.
                return
            }

            event.isCancelled = true

            if (event.damager is Player) {
                tryBreakEnderCrystal(entity, event.damager as Player)
            } else if (event.damager is Projectile) {
                val projectile = event.damager as Projectile
                if (projectile.shooter is Player) {
                    tryBreakEnderCrystal(entity, projectile.shooter as Player)
                }
            }
        }
    }

    /**
     * Check that a player can build before placing an Ender Crystal.
     *
     * Minecraft will place an Ender Crystal on top of obsidian or bedrock even
     * when the player clicks the sides or underside of the block. Therefore, we
     * always check build permissions <i>above</i> the clicked block.
     */
    @EventHandler(ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.material == Material.END_CRYSTAL && event.action == Action.RIGHT_CLICK_BLOCK) {
            val destination = event.clickedBlock?.getRelative(BlockFace.UP)
            if (destination != null && !canBuild(event.player, destination.location)) {
                event.isCancelled = true
            }
        }
    }

    /**
     * Handle a specific player's attempt to break an Ender Crystal.
     *
     * @param crystal the crystal.
     * @param player the player.
     */
    protected fun tryBreakEnderCrystal(crystal: Entity, player: Player) {
        val loc = crystal.location
        if (canBuild(player, loc)) {
            crystal.remove()
            val suppressed: String
            if (isDragonSpawningCrystal(loc)) {
                suppressed = " - drop suppressed because dragon may spawn"
            } else {
                loc.world?.dropItemNaturally(loc, ItemStack(Material.END_CRYSTAL))
                suppressed = ""
            }
            logger.info(player.name + " broke an Ender Crystal at " +
                    loc.world?.name + ", " +
                    loc.blockX + ", " + loc.blockY + ", " + loc.blockZ + suppressed)
        }
    }

    /**
     * Return true if the crystal is in a position that can be used to summon
     * the dragon.
     *
     * When summoning the dragon, players can break the crystals on the frame
     * before the dragon CreatureSpawnEvent (reason DEFAULT) occurs, and
     * potentially recover them, if they don't burn up in the fire underneath.
     * This method is used to detect that situation and prevent recovery of the
     * crystals.
     *
     * Getting delicate about the exact coordinates of the crystal won't work
     * because a determined player will move the crystals with pistons
     * (verified). Any crystals too close to the portal would normally be blown
     * up by the explosion when the dragon spawns, anyway.
     *
     * @param loc the location of the crystal.
     * @return true if the crystal is in a position that can be used to summon
     *         the dragon.
     */
    protected fun isDragonSpawningCrystal(loc: Location): Boolean {
        return loc.world?.equals(CONFIG.END_PORTAL_LOCATION?.world) == true &&
                loc.distance(CONFIG.END_PORTAL_LOCATION) < CONFIG.END_PORTAL_RADIUS
    }

    /**
     * Returns true if the given player can build at the given location.
     * Effectively replaces the lost functionality of WorldGuardPlugin#canBuild.
     *
     * @param player the player.
     * @param location the location.
     * @return true if the given player can build at the given location.
     */
    private fun canBuild(player: Player, location: Location): Boolean {
        val wrappedLocation = BukkitAdapter.adapt(location)
        val localPlayer = worldGuard.wrapPlayer(player)
        return WorldGuard.getInstance().platform.regionContainer.createQuery().testBuild(wrappedLocation, localPlayer)
    }

    /**
     * Return true if the crystal is associated with the dragon fight.
     *
     * For this purpose, any crystal on bedrock in the end is assumed to be part
     * of the dragon fight. These crystals are not protected (they will behave
     * as in vanilla).
     *
     * @param loc the location of the end crystal.
     * @return true if the crystal is associated with the dragon fight.
     */
    private fun isDragonFightCrystal(loc: Location): Boolean {
        val blockUnder = loc.block.getRelative(0, -1, 0)
        return loc.world?.environment == Environment.THE_END &&
                blockUnder?.type == Material.BEDROCK
    }
}
