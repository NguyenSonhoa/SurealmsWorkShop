package me.sanenuyan.surealmsWorkshop.workshops.cooking

import me.sanenuyan.surealmsWorkshop.utils.ColorUtils
import me.sanenuyan.surealmsWorkshop.utils.ConfigManager
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType

class CookingWorkshopListener(private val workshop: CookingWorkshop) : Listener {

    private val RECIPE_ID_KEY = NamespacedKey(workshop.plugin, "recipe_id")

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        val clickedInventory = event.clickedInventory ?: return
        val topInventory = event.inventory

        if (topInventory.holder is CookingGUIHolder && clickedInventory.holder is Player && event.action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            val guiStatePair = workshop.guiState[player.uniqueId] ?: return
            if (guiStatePair.first == CookingWorkshop.GUIType.SELECTED_RECIPE_PLACE_INGREDIENTS) {
                event.isCancelled = true
                val itemToMove = event.currentItem?.clone() ?: return

                val targetSlot = workshop.ingredientInputSlots.firstOrNull { topInventory.getItem(it) == null }
                if (targetSlot != null) {
                    topInventory.setItem(targetSlot, itemToMove)
                    event.currentItem = null
                }

                return
            }
        }

        val holder = clickedInventory.holder

        when (holder) {
            is CookingGUIHolder -> {
                if (event.currentItem != null) {
                    try {
                        player.playSound(player.location, ConfigManager.guiClickSound, ConfigManager.guiClickSoundVolume, ConfigManager.guiClickSoundPitch)
                    } catch (e: IllegalArgumentException) {
                        workshop.plugin.logger.warning("Invalid sound name in config: ${ConfigManager.guiClickSound}")
                    }
                }

                val guiStatePair = workshop.guiState[player.uniqueId] ?: return
                val guiType = guiStatePair.first

                when (guiType) {
                    CookingWorkshop.GUIType.RECIPE_LIST -> {
                        event.isCancelled = true

                        val currentItem = event.currentItem ?: return

                        when (currentItem) {
                            workshop.nextPageItem -> {
                                val (_, page) = guiStatePair
                                workshop.openGUI(player, page + 1)
                            }
                            workshop.prevPageItem -> {
                                val (_, page) = guiStatePair
                                workshop.openGUI(player, page - 1)
                            }
                            else -> {
                                val itemMeta = currentItem.itemMeta
                                if (itemMeta != null) {
                                    val recipeId = itemMeta.persistentDataContainer.get(RECIPE_ID_KEY, PersistentDataType.STRING)
                                    if (recipeId != null) {
                                        // Check if the recipe is currently being crafted
                                        if (workshop.craftingManager.isCrafting(player, recipeId)) {
                                            // If crafting, attempt to cancel it
                                            workshop.craftingManager.cancelCraft(player, recipeId)
                                            workshop.openGUI(player) // Refresh RECIPE_LIST to update status
                                            return // Prevent further processing
                                        }

                                        val recipe = workshop.recipes.find { it.id == recipeId }
                                        if (recipe != null) {
                                            workshop.selectedRecipeMap[player.uniqueId] = recipe

                                            workshop.guiState[player.uniqueId] = Pair(CookingWorkshop.GUIType.SELECTED_RECIPE_SHOW_GHOSTS, 0)
                                            workshop.populateInventoryWithRecipeDetails(clickedInventory, recipe, true)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    CookingWorkshop.GUIType.SELECTED_RECIPE_SHOW_GHOSTS -> {
                        event.isCancelled = true

                        val currentItem = event.currentItem
                        val rawSlot = event.rawSlot

                        if (currentItem != null) {
                            if (currentItem == workshop.backButtonItem && rawSlot == workshop.backButtonSlot) {
                                workshop.selectedRecipeMap.remove(player.uniqueId)
                                workshop.openGUI(player)
                            } else if (rawSlot == ConfigManager.outputSlot) {
                                val recipe = workshop.selectedRecipeMap[player.uniqueId]
                                if (recipe != null) {

                                    workshop.guiState[player.uniqueId] = Pair(CookingWorkshop.GUIType.SELECTED_RECIPE_PLACE_INGREDIENTS, 0)
                                    workshop.populateInventoryWithRecipeDetails(clickedInventory, recipe, false)
                                }
                            }
                        }

                    }
                    CookingWorkshop.GUIType.SELECTED_RECIPE_PLACE_INGREDIENTS -> {

                        if (event.slot in workshop.ingredientInputSlots &&
                            (event.action == InventoryAction.PLACE_ALL || event.action == InventoryAction.PLACE_ONE || event.action == InventoryAction.PLACE_SOME ||
                                    event.action == InventoryAction.SWAP_WITH_CURSOR || event.action == InventoryAction.PICKUP_ALL || event.action == InventoryAction.PICKUP_HALF ||
                                    event.action == InventoryAction.PICKUP_ONE || event.action == InventoryAction.PICKUP_SOME || event.action == InventoryAction.MOVE_TO_OTHER_INVENTORY)) {
                            event.isCancelled = false
                            return
                        }

                        event.isCancelled = true

                        val currentItem = event.currentItem
                        val rawSlot = event.rawSlot

                        if (currentItem != null) {
                            if (currentItem == workshop.backButtonItem && rawSlot == workshop.backButtonSlot) {

                                for (slot in workshop.ingredientInputSlots) {
                                    val item = clickedInventory.getItem(slot)
                                    if (item != null) {
                                        val remaining = player.inventory.addItem(item)
                                        if (remaining.isNotEmpty()) {
                                            remaining.values.forEach { player.world.dropItem(player.location, it) }
                                        }
                                        clickedInventory.setItem(slot, null)
                                    }
                                }

                                workshop.selectedRecipeMap.remove(player.uniqueId)
                                workshop.openGUI(player)
                            } else if (rawSlot == ConfigManager.outputSlot) {
                                workshop.craft(player, clickedInventory)
                                // After crafting, set GUI state to RECIPE_LIST and open it
                                workshop.selectedRecipeMap.remove(player.uniqueId) // Clear selected recipe
                                workshop.guiState[player.uniqueId] = Pair(CookingWorkshop.GUIType.RECIPE_LIST, 0)
                                workshop.openGUI(player)
                            }
                        }
                    }
                    CookingWorkshop.GUIType.CRAFTING_IN_PROGRESS -> {
                        // This block is now largely redundant as crafting actions will return to RECIPE_LIST.
                        // The logic for cancelling craft is now handled in RECIPE_LIST.
                        event.isCancelled = true
                        val rawSlot = event.rawSlot
                        if (rawSlot == ConfigManager.outputSlot) {
                            // Player clicked the item being crafted, attempt to cancel
                            // This logic is now handled in RECIPE_LIST
                            // workshop.cancelCraft(player, clickedInventory)
                        } else if (rawSlot == workshop.backButtonSlot && event.currentItem == workshop.backButtonItem) {
                            // Player clicked the back button, close GUI without cancelling craft
                            player.closeInventory()
                        }
                    }
                }
            }
            else -> {

            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val holder = event.inventory.holder

        if (holder is CookingGUIHolder) {
            val guiStatePair = workshop.guiState[player.uniqueId]
            val guiType = guiStatePair?.first

            Bukkit.getScheduler().runTask(workshop.plugin, Runnable {
                val newHolder = player.openInventory.topInventory.holder
                if (newHolder !is CookingGUIHolder) {

                    if (guiType == CookingWorkshop.GUIType.SELECTED_RECIPE_PLACE_INGREDIENTS) {
                        for (slot in workshop.ingredientInputSlots) {
                            val item = event.inventory.getItem(slot)
                            if (item != null) {
                                val remaining = player.inventory.addItem(item)

                                if (remaining.isNotEmpty()) {
                                    remaining.values.forEach { player.world.dropItem(player.location, it) }
                                }
                                event.inventory.setItem(slot, null)
                            }
                        }
                    }

                    workshop.selectedRecipeMap.remove(player.uniqueId)
                    workshop.openGUIs.remove(player.uniqueId)
                    workshop.guiState.remove(player.uniqueId)
                }
            })
        }
    }
}