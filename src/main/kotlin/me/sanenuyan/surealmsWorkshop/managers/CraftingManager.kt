package me.sanenuyan.surealmsWorkshop.managers

import me.sanenuyan.surealmsWorkshop.SurealmsWorkshop
import me.sanenuyan.surealmsWorkshop.utils.ConfigManager
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class CraftingTask(
    val playerId: UUID,
    val outputItem: ItemStack,
    val recipeId: String,
    val completionCallback: () -> Unit,
    var bukkitTask: BukkitTask? = null
)

class CraftingManager(private val plugin: SurealmsWorkshop) {

    private val activeCraftingTasks = ConcurrentHashMap<UUID, CraftingTask>()

    fun startCrafting(player: Player, outputItem: ItemStack, craftingTimeSeconds: Int, recipeId: String, onComplete: () -> Unit) {
        if (activeCraftingTasks.containsKey(player.uniqueId)) {
            player.sendMessage(ConfigManager.craftingAlreadyCraftingMessage)
            return
        }

        val itemName = outputItem.itemMeta?.displayName ?: recipeId
        player.sendMessage(ConfigManager.craftingStartedMessage
            .replace("%item_name%", itemName)
            .replace("%time%", craftingTimeSeconds.toString()))
        player.closeInventory()

        val task = CraftingTask(player.uniqueId, outputItem, recipeId, onComplete)

        val bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable {

            val completedTask = activeCraftingTasks.remove(player.uniqueId)
            if (completedTask != null) {

                player.inventory.addItem(completedTask.outputItem)
                completedTask.completionCallback.invoke()
            }
        }, craftingTimeSeconds * 20L)

        task.bukkitTask = bukkitTask
        activeCraftingTasks[player.uniqueId] = task
    }

    fun cancelCrafting(player: Player) {
        val task = activeCraftingTasks.remove(player.uniqueId)
        if (task != null) {
            task.bukkitTask?.cancel()
            val itemName = task.outputItem.itemMeta?.displayName ?: task.recipeId
            player.sendMessage(ConfigManager.craftingCancelledPlayerMessage.replace("%item_name%", itemName))
        } else {
            player.sendMessage(ConfigManager.craftingNotCraftingMessage)
        }
    }

    fun onDisable() {

        activeCraftingTasks.values.forEach { task ->
            task.bukkitTask?.cancel()
            Bukkit.getPlayer(task.playerId)?.sendMessage(ConfigManager.craftingCancelledServerMessage)
        }
        activeCraftingTasks.clear()
    }
}