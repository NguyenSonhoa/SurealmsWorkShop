package me.sanenuyan.surealmsWorkshop

import me.sanenuyan.surealmsWorkshop.utils.ConfigManager
import me.sanenuyan.surealmsWorkshop.workshops.cooking.CookingWorkshop
import me.sanenuyan.surealmsWorkshop.workshops.cooking.CookingWorkshopListener
import me.sanenuyan.surealmsWorkshop.hooks.VaultSupport
import me.sanenuyan.surealmsWorkshop.managers.CraftingManager
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.TabCompleter
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class SurealmsWorkshop : JavaPlugin(), CommandExecutor, TabCompleter {

    lateinit var cookingWorkshop: CookingWorkshop
        private set
    lateinit var craftingManager: CraftingManager
        private set

    override fun onEnable() {

        instance = this

        saveDefaultConfig()
        ConfigManager.load(this)

        if (!VaultSupport.setupEconomy(this)) {
            logger.warning("Vault not found or economy not hooked. Some features might be disabled.")
        }

        craftingManager = CraftingManager(this)
        craftingManager.loadActiveCrafts() // Call loadActiveCrafts here

        cookingWorkshop = CookingWorkshop(this, craftingManager, VaultSupport.economy)

        getCommand("surealmsworkshop")?.setExecutor(this)
        getCommand("surealmsworkshop")?.setTabCompleter(this)

        server.pluginManager.registerEvents(CookingWorkshopListener(cookingWorkshop), this)
    }

    override fun onDisable() {

        if (::craftingManager.isInitialized) {
            craftingManager.onDisable()
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (command.name.equals("surealmsworkshop", ignoreCase = true)) {
            if (args.isEmpty()) {

                if (sender is Player) {
                    if (sender.hasPermission("surealmsworkshop.command.cooking")) {
                        cookingWorkshop.openGUI(sender)
                    } else {
                        sender.sendMessage(ConfigManager.noPermissionMessage)
                    }
                } else {
                    sender.sendMessage("Usage: /surealmsworkshop [cooking|reload|open <player>] Aliases: /workshop")
                }
                return true
            }

            when (args[0].lowercase()) {
                "cooking" -> {
                    if (sender is Player) {
                        if (sender.hasPermission("surealmsworkshop.command.cooking")) {
                            cookingWorkshop.openGUI(sender)
                        }
                    } else {
                        sender.sendMessage("This command can only be run by a player.")
                    }
                    return true
                }
                "reload" -> {
                    if (sender.hasPermission("surealmsworkshop.reload")) {

                        ConfigManager.load(this)
                        cookingWorkshop.reload()
                        sender.sendMessage(ConfigManager.reloadSuccessMessage)
                    }
                    return true
                }
                "open" -> {
                    if (sender.hasPermission("surealmsworkshop.open.other")) {
                        if (args.size > 1) {
                            val targetPlayer = Bukkit.getPlayer(args[1])
                            if (targetPlayer != null) {
                                cookingWorkshop.openGUI(targetPlayer)
                                sender.sendMessage("§aOpened GUI for ${targetPlayer.name}.")
                            } else {
                                sender.sendMessage(ConfigManager.playerNotFoundMessage.replace("{player_name}", args[1]))
                            }
                        } else {
                            sender.sendMessage("§cUsage: /surealmsworkshop open <player>")
                        }
                    }
                    return true
                }
                else -> {
                    sender.sendMessage("§cUnknown subcommand. Usage: /surealmsworkshop [cooking|reload|open <player>] Aliases: /workshop")
                    return true
                }
            }
        }
        return false
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String>? {
        if (command.name.equals("surealmsworkshop", ignoreCase = true)) {
            if (args.size == 1) {
                val completions = mutableListOf<String>()
                if (sender.hasPermission("surealmsworkshop.command.cooking")) {
                    completions.add("cooking")
                }
                if (sender.hasPermission("surealmsworkshop.reload")) {
                    completions.add("reload")
                }
                if (sender.hasPermission("surealmsworkshop.open.other")) {
                    completions.add("open")
                }
                return completions.filter { it.startsWith(args[0], ignoreCase = true) }.toMutableList()
            } else if (args.size == 2 && args[0].lowercase() == "open" && sender.hasPermission("surealmsworkshop.open.other")) {
                return Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }.toMutableList()
            }
        }
        return null
    }

    companion object {
        lateinit var instance: SurealmsWorkshop
            private set
    }
}