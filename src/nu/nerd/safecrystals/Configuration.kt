package nu.nerd.safecrystals

import org.bukkit.Location

/**
 * Configuration wrapper.
 */
class Configuration {
    /**
     * Radius around END_PORTAL_LOCATION where broken ender crystals don't drop.
     */
    var END_PORTAL_RADIUS: Double = 0.0

    /**
     * Location of the (assumed only) end-side end portal.
     */
    var END_PORTAL_LOCATION: Location? = null

    // ------------------------------------------------------------------------
    /**
     * Reload the configuration.
     */
    fun reload() {
        SafeCrystals.PLUGIN.reloadConfig()

        END_PORTAL_RADIUS = SafeCrystals.PLUGIN.config.getDouble("end-portal.radius")
        END_PORTAL_LOCATION = SafeCrystals.PLUGIN.config.get("end-portal.location") as Location?
    }
}
