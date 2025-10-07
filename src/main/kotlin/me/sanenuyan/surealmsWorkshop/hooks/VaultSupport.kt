package me.sanenuyan.surealmsWorkshop.hooks

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.plugin.RegisteredServiceProvider
import org.bukkit.plugin.java.JavaPlugin

object VaultSupport {
    var economy: Economy? = null
        private set

    fun setupEconomy(plugin: JavaPlugin): Boolean {
        if (plugin.server.pluginManager.getPlugin("Vault") == null) {
            plugin.logger.warning("Vault plugin not found.")
            return false
        }
        val rsp: RegisteredServiceProvider<*>? = plugin.server.servicesManager.getRegistration(Economy::class.java)
        if (rsp == null) {
            plugin.logger.warning("No Vault Economy service registered.")
            return false
        }

        val provider = rsp.provider
        if (provider is Economy) {
            economy = provider
            Bukkit.getLogger().info("Vault economy hooked.")
            return true
        } else {
            plugin.logger.severe("Vault provider is not an Economy instance. Found: ${provider?.javaClass?.name ?: "null"}. This often indicates a classloader conflict (e.g., Vault shaded into your plugin).")
            return false
        }
    }
}