package me.sanenuyan.surealmsWorkshop.managers

import me.sanenuyan.surealmsWorkshop.SurealmsWorkshop
import me.sanenuyan.surealmsWorkshop.utils.ConfigManager
import me.sanenuyan.surealmsWorkshop.utils.ColorUtils
import me.sanenuyan.surealmsWorkshop.workshops.cooking.CookingGUIHolder
import me.sanenuyan.surealmsWorkshop.workshops.cooking.CookingWorkshop // Import CookingWorkshop
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class CraftingTask(
    val playerUUID: UUID,
    val recipeId: String,
    val craftingItem: ItemStack, // The item being crafted, with lore and CMD
    val startTime: Long, // System.currentTimeMillis() when craft started
    val craftingTime: Int, // Total crafting time in seconds
    @Transient var taskId: Int = -1 // Bukkit task ID, not saved
) {
    fun toSerializableMap(): Map<String, Any> {
        return mapOf(
            "playerUUID" to playerUUID.toString(),
            "recipeId" to recipeId,
            "craftingItem" to craftingItem.serialize(),
            "startTime" to startTime,
            "craftingTime" to craftingTime
        )
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromSerializableMap(map: Map<String, Any>): CraftingTask? {
            return try {
                val playerUUID = UUID.fromString(map["playerUUID"] as String)
                val recipeId = map["recipeId"] as String
                val craftingItem = ItemStack.deserialize(map["craftingItem"] as Map<String, Any>)
                val startTime = map["startTime"] as Long
                val craftingTime = map["craftingTime"] as Int
                CraftingTask(playerUUID, recipeId, craftingItem, startTime, craftingTime)
            } catch (e: Exception) {
                SurealmsWorkshop.instance.logger.severe("Failed to deserialize CraftingTask: ${e.message}")
                null
            }
        }
    }
}

@Suppress("DEPRECATION")
class CraftingManager(private val plugin: SurealmsWorkshop) {

    private lateinit var cookingWorkshop: CookingWorkshop // Add this
    fun setCookingWorkshop(workshop: CookingWorkshop) { this.cookingWorkshop = workshop } // Setter for CookingWorkshop

    private val activeCrafts: ConcurrentHashMap<UUID, CraftingTask> = ConcurrentHashMap()
    private val recipeIdKey = NamespacedKey(plugin, "recipe_id")

    private val craftsFile: File = File(plugin.dataFolder, "active_crafts.yml")
    private var craftsConfig: YamlConfiguration = YamlConfiguration()

    init {
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }
        if (!craftsFile.exists()) {
            craftsFile.createNewFile()
        }
        craftsConfig = YamlConfiguration.loadConfiguration(craftsFile)
    }

    private fun prepareCraftingItem(item: ItemStack, recipeId: String, time: Int): ItemStack {
        val newItem = item.clone()
        val newMeta = newItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(newItem.type)
        if (newMeta != null) {
            newMeta.setCustomModelData(ConfigManager.craftingCustomModelData)
            newMeta.persistentDataContainer.set(recipeIdKey, PersistentDataType.STRING, recipeId)
            newItem.itemMeta = updateCraftTimeLore(newMeta, time)
        }
        return newItem
    }

    fun startCrafting(player: Player, recipeId: String, finalCraftedItem: ItemStack, craftingTime: Int) {
        if (activeCrafts.containsKey(player.uniqueId)) {
            player.sendMessage(ConfigManager.craftingAlreadyCraftingMessage)
            return
        }

        val initialCraftingItem = prepareCraftingItem(finalCraftedItem, recipeId, craftingTime)

        val startTime = System.currentTimeMillis()
        val craftingTask = CraftingTask(player.uniqueId, recipeId, initialCraftingItem, startTime, craftingTime)

        val task = object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline) {
                    // Player logged off, keep the task active but don't update GUI
                    return
                }

                val currentTask = activeCrafts[player.uniqueId]
                if (currentTask == null || currentTask.recipeId != recipeId) {
                    // Craft was cancelled or replaced
                    cancel()
                    return
                }

                val remainingTime = (currentTask.craftingTime - ((System.currentTimeMillis() - currentTask.startTime) / 1000)).toInt().coerceAtLeast(0)

                if (remainingTime <= 0) {
                    completeCraft(player, recipeId, finalCraftedItem)
                    cancel()
                    return
                }

                // Update lore in player's open GUI if it's the workshop GUI
                val openInventory = player.openInventory.topInventory
                if (openInventory.holder is CookingGUIHolder) {
                    val outputSlot = ConfigManager.outputSlot
                    val currentItemInSlot = openInventory.getItem(outputSlot)
                    if (currentItemInSlot != null && currentItemInSlot.itemMeta?.persistentDataContainer?.get(recipeIdKey, PersistentDataType.STRING) == recipeId) {
                        val updatedMeta = updateCraftTimeLore(currentItemInSlot.itemMeta!!, remainingTime)
                        currentItemInSlot.itemMeta = updatedMeta
                        openInventory.setItem(outputSlot, currentItemInSlot)
                    }
                }

                // Update the craftingTask's item in activeCrafts to reflect current lore
                val updatedCraftingItem = currentTask.craftingItem.clone()
                val updatedMeta = updateCraftTimeLore(updatedCraftingItem.itemMeta!!, remainingTime)
                updatedCraftingItem.itemMeta = updatedMeta
                activeCrafts[player.uniqueId] = currentTask.copy(craftingItem = updatedCraftingItem)
            }
        }.runTaskTimer(plugin, 0L, 20L) // Update every second

        craftingTask.taskId = task.taskId
        activeCrafts[player.uniqueId] = craftingTask
        
        player.sendMessage(ConfigManager.craftingStartedMessage.replace("%item_name%", finalCraftedItem.itemMeta?.displayName ?: finalCraftedItem.type.name).replace("%time%", craftingTime.toString()))
    }

    fun cancelCraft(player: Player, recipeId: String): Boolean {
        val task = activeCrafts[player.uniqueId]
        if (task != null && task.recipeId == recipeId) {
            Bukkit.getScheduler().cancelTask(task.taskId)
            activeCrafts.remove(player.uniqueId)

            // Retrieve the recipe to get ingredients
            val recipe = cookingWorkshop.recipes.find { it.id == recipeId }

            if (recipe != null) {
                val droppedItems = mutableListOf<ItemStack>()
                recipe.ingredients.forEach { (_, ingredient) ->
                    val itemToReturn = ingredient.itemStack.clone()
                    itemToReturn.amount = ingredient.amount
                    val leftovers = player.inventory.addItem(itemToReturn)
                    if (leftovers.isNotEmpty()) {
                        droppedItems.addAll(leftovers.values)
                    }
                }

                if (droppedItems.isNotEmpty()) {
                    player.sendMessage(ConfigManager.craftingCancelledIngredientsDroppedMessage)
                    droppedItems.forEach { droppedItem ->
                        player.world.dropItemNaturally(player.location, droppedItem)
                    }
                } else {
                    player.sendMessage(ConfigManager.craftingCancelledIngredientsReturnedMessage)
                }
            } else {
                plugin.logger.warning("Could not find recipe with ID '$recipeId' for player ${player.name} during craft cancellation.")
            }

            // Update the item in the GUI to show it's cancelled
            val openInventory = player.openInventory.topInventory
            if (openInventory.holder is CookingGUIHolder) {
                val outputSlot = ConfigManager.outputSlot
                val currentItemInSlot = openInventory.getItem(outputSlot)
                if (currentItemInSlot != null && currentItemInSlot.itemMeta?.persistentDataContainer?.get(recipeIdKey, PersistentDataType.STRING) == recipeId) {
                    val cancelledMeta = currentItemInSlot.itemMeta?.clone()
                    if (cancelledMeta != null) {
                        val lore = cancelledMeta.lore ?: mutableListOf()
                        lore.add(0, ColorUtils.translateColors(ConfigManager.craftingCancelledLore)) // Add cancelled lore at the top
                        cancelledMeta.lore = lore
                        currentItemInSlot.itemMeta = cancelledMeta
                        openInventory.setItem(outputSlot, currentItemInSlot)
                    }
                }
            }
            // Notify CookingWorkshop to open RECIPE_LIST after cancellation
            cookingWorkshop.onCraftComplete(player) // Use the same method for completion/cancellation refresh
            return true
        }
        return false
    }

    fun getCraftingTask(player: Player): CraftingTask? {
        return activeCrafts[player.uniqueId]
    }

    fun isCrafting(player: Player, recipeId: String): Boolean {
        val task = activeCrafts[player.uniqueId]
        return task != null && task.recipeId == recipeId
    }

    fun getRemainingCraftTime(player: Player, recipeId: String): Int {
        val task = activeCrafts[player.uniqueId]
        if (task != null && task.recipeId == recipeId) {
            val elapsedTime = (System.currentTimeMillis() - task.startTime) / 1000
            return (task.craftingTime - elapsedTime).toInt().coerceAtLeast(0)
        }
        return 0
    }

    private fun completeCraft(player: Player, recipeId: String, finalCraftedItem: ItemStack) {
        activeCrafts.remove(player.uniqueId)
        if (player.isOnline) {
            player.inventory.addItem(finalCraftedItem)
            player.sendMessage(ConfigManager.craftSuccessMessage.replace("%item_name%", finalCraftedItem.itemMeta?.displayName ?: finalCraftedItem.type.name))

            // Clear the output slot in the GUI
            val openInventory = player.openInventory.topInventory
            if (openInventory.holder is CookingGUIHolder) {
                val outputSlot = ConfigManager.outputSlot
                val currentItemInSlot = openInventory.getItem(outputSlot)
                if (currentItemInSlot != null && currentItemInSlot.itemMeta?.persistentDataContainer?.get(recipeIdKey, PersistentDataType.STRING) == recipeId) {
                    openInventory.setItem(outputSlot, null)
                }
            }
            // Notify CookingWorkshop to open RECIPE_LIST
            cookingWorkshop.onCraftComplete(player)
        } else {
            // Player is offline, handle pending item (e.g., store in a separate file or give on next login)
            plugin.logger.info("Craft for ${player.name} (${player.uniqueId}) completed while offline. Item: ${finalCraftedItem.type.name}. Will be given on next login.")
            // TODO: Implement a proper pending items system for offline players
        }
    }

    private fun updateCraftTimeLore(meta: ItemMeta, remainingTime: Int): ItemMeta {
        val newMeta = meta.clone()
        val lore = newMeta.lore?.toMutableList() ?: mutableListOf()

        // Remove existing craft time lore if present
        lore.removeIf { ColorUtils.stripColor(it).contains(ColorUtils.stripColor(ConfigManager.craftTimeLoreFormat.split("{")[0])) }
        lore.removeIf { ColorUtils.stripColor(it).contains(ColorUtils.stripColor(ConfigManager.craftingInProgressLore)) }

        // Add new craft time lore
        lore.add(0, ColorUtils.translateColors(ConfigManager.craftTimeLoreFormat.replace("{time}", remainingTime.toString())))
        lore.add(1, ColorUtils.translateColors(ConfigManager.craftingInProgressLore))

        newMeta.lore = lore
        return newMeta
    }

    fun onDisable() {
        saveActiveCrafts()
        Bukkit.getScheduler().cancelTasks(plugin) // Cancel all plugin tasks on disable
    }

    private fun saveActiveCrafts() {
        craftsConfig = YamlConfiguration() // Clear existing config to avoid stale data
        var craftCount = 0
        activeCrafts.forEach { (uuid, task) ->
            craftsConfig.set("crafts.${uuid}.${task.recipeId}", task.toSerializableMap())
            craftCount++
        }
        try {
            craftsConfig.save(craftsFile)
            plugin.logger.info("Saved $craftCount active crafts to ${craftsFile.name}")
        } catch (e: Exception) {
            plugin.logger.severe("Could not save active crafts: ${e.message}")
        }
    }

    internal fun loadActiveCrafts() {
        if (!craftsFile.exists()) return

        craftsConfig = YamlConfiguration.loadConfiguration(craftsFile)
        val craftsSection = craftsConfig.getConfigurationSection("crafts") ?: return

        var loadedCount = 0
        for (playerUUIDString in craftsSection.getKeys(false)) {
            val playerUUID = UUID.fromString(playerUUIDString)
            val playerCraftsSection = craftsSection.getConfigurationSection(playerUUIDString) ?: continue

            for (recipeId in playerCraftsSection.getKeys(false)) {
                val craftMap = playerCraftsSection.getConfigurationSection(recipeId)?.getValues(false) ?: continue
                val loadedTask = CraftingTask.fromSerializableMap(craftMap)

                if (loadedTask != null) {
                    val elapsedTime = (System.currentTimeMillis() - loadedTask.startTime) / 1000
                    val remainingTime = loadedTask.craftingTime - elapsedTime.toInt()

                    if (remainingTime <= 0) {
                        // Craft completed while server was offline
                        val player = Bukkit.getOfflinePlayer(playerUUID)
                        plugin.logger.info("Craft for ${player.name} (${playerUUID}) completed offline. Item: ${loadedTask.craftingItem.type.name}. Will be given on next login.")
                        // TODO: Add to a pending items list for player on login
                    } else {
                        // Re-schedule the craft
                        val player = Bukkit.getPlayer(playerUUID)
                        if (player != null && player.isOnline) {
                            val originalRecipe = cookingWorkshop.recipes.find { it.id == loadedTask.recipeId }
                            if (originalRecipe != null) {
                                // Pass the original output item, as prepareCraftingItem will add the lore
                                startCrafting(player, loadedTask.recipeId, originalRecipe.output.itemStack.clone(), remainingTime)
                                // Update the start time and crafting time for the re-scheduled task
                                val currentTask = activeCrafts[playerUUID]
                                if (currentTask != null) {
                                    activeCrafts[playerUUID] = currentTask.copy(startTime = System.currentTimeMillis(), craftingTime = remainingTime)
                                }
                            } else {
                                plugin.logger.warning("Could not find recipe for loaded craft: ${loadedTask.recipeId}. Craft will not be resumed.")
                            }
                        } else {
                            // Player is offline, just add to activeCrafts for now, task will be re-scheduled on login
                            activeCrafts[playerUUID] = loadedTask.copy(craftingTime = remainingTime) // Update remaining time
                            plugin.logger.info("Loaded craft for offline player ${Bukkit.getOfflinePlayer(playerUUID).name} (${playerUUID}). Remaining time: ${remainingTime}s")
                        }
                    }
                    loadedCount++
                }
            }
        }
        plugin.logger.info("Loaded $loadedCount active crafts from ${craftsFile.name}")
    }
}