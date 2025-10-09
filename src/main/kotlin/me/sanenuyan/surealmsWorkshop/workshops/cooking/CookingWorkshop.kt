package me.sanenuyan.surealmsWorkshop.workshops.cooking

import me.sanenuyan.surealmsWorkshop.SurealmsWorkshop
import me.sanenuyan.surealmsWorkshop.utils.ColorUtils
import me.sanenuyan.surealmsWorkshop.utils.ConfigManager
import me.sanenuyan.surealmsWorkshop.hooks.MMOItemsSupport
import me.sanenuyan.surealmsWorkshop.managers.CraftingManager
import me.sanenuyan.surealmsWorkshop.workshops.Ingredient
import me.sanenuyan.surealmsWorkshop.workshops.RecipeOutput
import me.sanenuyan.surealmsWorkshop.workshops.VanillaIngredient
import me.sanenuyan.surealmsWorkshop.workshops.VanillaOutput
import org.bukkit.event.inventory.InventoryType
import me.sanenuyan.surealmsWorkshop.hooks.MMOItemIngredient
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.io.File
import java.util.UUID
import kotlin.collections.get
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import org.bukkit.inventory.InventoryHolder
import me.sanenuyan.surealmsWorkshop.workshops.cooking.CookingGUIHolder
import me.sanenuyan.surealmsWorkshop.workshops.cooking.CookingRecipe
import io.lumine.mythic.lib.api.item.NBTItem
import net.Indyuce.mmoitems.util.MMOUtils
import java.util.Locale
import org.bukkit.inventory.ItemFlag
import org.bukkit.scheduler.BukkitTask

class CookingWorkshop(internal val plugin: SurealmsWorkshop, internal val craftingManager: CraftingManager, private val economy: Economy?) {

    internal val recipes = mutableListOf<CookingRecipe>()
    internal val openGUIs = mutableMapOf<UUID, Inventory>()
    internal val guiState = mutableMapOf<UUID, Pair<GUIType, Int>>()
    internal val selectedRecipeMap = mutableMapOf<UUID, CookingRecipe?>()

    private val mmoItemsSupport: MMOItemsSupport = MMOItemsSupport(plugin)
    internal val RECIPE_ID_KEY = NamespacedKey(plugin, "recipe_id")
    internal val RECIPE_LIST_GUI_KEY = NamespacedKey(plugin, "recipe_list_gui")
    internal val CRAFTING_ITEM_CUSTOM_MODEL_DATA_KEY = NamespacedKey(plugin, "crafting_item_cmd")
    internal val CRAFTING_CUSTOM_MODEL_DATA_VALUE = ConfigManager.craftingCustomModelData // Use value from ConfigManager

    internal val nextPageItem: ItemStack
    internal val prevPageItem: ItemStack
    internal val craftButtonItem: ItemStack
    internal val backButtonItem: ItemStack
    internal val fillerItem: ItemStack
    internal val requiredIngredientItem: ItemStack
    internal val guideButtonItem: ItemStack // New: Guide button item declaration

    internal val craftButtonSlot = 22
    internal val backButtonSlot = ConfigManager.backButtonSlot
    internal val nextPageSlot = ConfigManager.nextPageSlot
    internal val prevPageSlot = ConfigManager.prevPageSlot
    internal val recipesPerPage = 28

    internal val ingredientInputSlots = ConfigManager.ingredientInputSlots

    enum class GUIType { RECIPE_LIST, SELECTED_RECIPE_SHOW_GHOSTS, SELECTED_RECIPE_PLACE_INGREDIENTS, CRAFTING_IN_PROGRESS }
    enum class IngredientCheckResult { SUCCESS, NOT_ENOUGH_INGREDIENTS, WRONG_SLOTS }

    private data class VanillaKey(val material: Material, val displayName: String?, val lore: List<String>?)
    private data class MMOItemKey(val type: String, val id: String)

    private var guiRefreshTask: BukkitTask? = null

    init {
        craftingManager.setCookingWorkshop(this) // Set the reference to this workshop
        guideButtonItem = createItem(Material.matchMaterial(ConfigManager.guideButtonItem.material) ?: Material.BOOK, ConfigManager.guideButtonItem.name, ConfigManager.guideButtonItem.lore, ConfigManager.guideButtonItem.itemModel, ConfigManager.guideButtonItem.customModelData, ConfigManager.guideButtonItem.hideTooltip) // New: Guide button item initialization
        nextPageItem = createItem(Material.matchMaterial(ConfigManager.nextPageItem.material) ?: Material.ARROW, ConfigManager.nextPageItem.name, ConfigManager.nextPageItem.lore, ConfigManager.nextPageItem.itemModel, ConfigManager.nextPageItem.customModelData, ConfigManager.nextPageItem.hideTooltip)
        prevPageItem = createItem(Material.matchMaterial(ConfigManager.prevPageItem.material) ?: Material.ARROW, ConfigManager.prevPageItem.name, ConfigManager.prevPageItem.lore, ConfigManager.prevPageItem.itemModel, ConfigManager.prevPageItem.customModelData, ConfigManager.prevPageItem.hideTooltip)
        craftButtonItem = createItem(Material.matchMaterial(ConfigManager.craftButtonItem.material) ?: Material.ANVIL, ConfigManager.craftButtonItem.name, ConfigManager.craftButtonItem.lore, ConfigManager.craftButtonItem.itemModel, ConfigManager.craftButtonItem.customModelData, ConfigManager.craftButtonItem.hideTooltip)
        backButtonItem = createItem(Material.matchMaterial(ConfigManager.backButtonItem.material) ?: Material.BARRIER, ConfigManager.backButtonItem.name, ConfigManager.backButtonItem.lore, ConfigManager.backButtonItem.itemModel, ConfigManager.backButtonItem.customModelData, ConfigManager.backButtonItem.hideTooltip)
        fillerItem = createItem(Material.matchMaterial(ConfigManager.emptySlotItem.material) ?: Material.GRAY_STAINED_GLASS_PANE, ConfigManager.emptySlotItem.name, ConfigManager.emptySlotItem.lore, ConfigManager.emptySlotItem.itemModel, ConfigManager.emptySlotItem.customModelData, ConfigManager.emptySlotItem.hideTooltip)
        requiredIngredientItem = createItem(
            Material.matchMaterial(ConfigManager.ingredientPlaceholderItem.material) ?: Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            ConfigManager.ingredientPlaceholderItem.name,
            ConfigManager.ingredientPlaceholderItem.lore,
            ConfigManager.ingredientPlaceholderItem.itemModel,
            ConfigManager.ingredientPlaceholderItem.customModelData,
            ConfigManager.ingredientPlaceholderItem.hideTooltip
        )
        loadRecipes()
        startGUIRefreshTask() // Start the refresh task
    }

    fun reload() {
        recipes.clear()
        ConfigManager.load(plugin)
        loadRecipes()
        plugin.logger.info("Cooking workshop recipes and configuration have been reloaded.")
    }

    private fun loadRecipes() {
        val recipesFile = File(plugin.dataFolder, "recipes.yml")
        if (!recipesFile.exists()) {
            plugin.saveResource("recipes.yml", false)
        }

        val config = YamlConfiguration.loadConfiguration(recipesFile)
        val recipesSection = config.getConfigurationSection("recipes") ?: return

        recipes.clear()

        for (key in recipesSection.getKeys(false)) {
            try {
                val recipeSection = recipesSection.getConfigurationSection(key)!!

                val output = parseItem(recipeSection.getConfigurationSection("output")!!, true) as RecipeOutput
                val ingredients = mutableMapOf<Int, Ingredient>()
                val cost = recipeSection.getDouble("cost", 0.0)
                val sound = recipeSection.getString("sound")
                val craftingTime = recipeSection.getInt("craftingTime", 0)
                val priority = recipeSection.getInt("priority", 0)
                val craftingMaterial = recipeSection.getString("craftingMaterial") // Load craftingMaterial
                
                val permissions: List<String>
                val permsObject = recipeSection.get("permissions")
                if (permsObject is List<*>) {
                    permissions = recipeSection.getStringList("permissions")
                } else if (permsObject is String) {
                    permissions = listOf(permsObject)
                } else {
                    permissions = emptyList()
                }


                recipeSection.getMapList("ingredients").forEach { ingredientMap ->
                    val slot = ingredientMap["slot"] as Int
                    val itemSection = YamlConfiguration().createSection("item", ingredientMap["item"] as Map<*, *>)
                    val ingredient = parseItem(itemSection, false) as Ingredient

                    ingredients[slot] = ingredient
                }

                if (ingredients.isEmpty()) {
                    plugin.logger.warning("[CookingWorkshop] Recipe '$key' has no valid ingredients. Skipping.")
                    continue
                }

                recipes.add(CookingRecipe(key, ingredients, output, cost, sound, craftingTime, priority, craftingMaterial, permissions))
            } catch (e: Exception) {
                plugin.logger.warning("[CookingWorkshop] Failed to load recipe '$key'. Error: ${e.message}")
            }
        }

        recipes.sortBy { it.priority }
        plugin.logger.info("[CookingWorkshop] Loaded ${recipes.size} cooking recipes.")
    }

    private fun parseItem(section: ConfigurationSection, isOutput: Boolean): Any {
        val amount = section.getInt("amount", 1)
        val name = section.getString("name")
        val lore = section.getStringList("lore")
        val itemModelString = section.getString("item-model")
        val customModelData = if (section.contains("custom-model-data")) section.getInt("custom-model-data") else null
        val hideTooltip = if (section.contains("hide-tooltip")) section.getBoolean("hide-tooltip") else null

        val materialName = section.getString("material")
        val mmoItemSection = section.getConfigurationSection("mmoitem")

        val displayItem = when {
            mmoItemSection != null -> {
                createItem(Material.PAPER, name ?: "MMOItem", lore, itemModelString, customModelData, hideTooltip)
            }
            materialName != null -> {
                val material = Material.matchMaterial(materialName) ?: throw IllegalArgumentException("Invalid material: $materialName")
                createItem(material, name, lore, itemModelString, customModelData, hideTooltip)
            }
            else -> throw IllegalArgumentException("Item must have a 'material' or 'mmoitem' section")
        }

        return when {
            mmoItemSection != null && mmoItemsSupport.isMMOItemsEnabled -> {
                val type = mmoItemSection.getString("type")!!
                val id = mmoItemSection.getString("id")!!
                if (isOutput) mmoItemsSupport.createMMOItemOutput(type, id, amount, displayItem) else mmoItemsSupport.createMMOItemIngredient(type, id, amount, displayItem)
            }
            else -> {
                if (isOutput) VanillaOutput(displayItem.apply { this.amount = amount }) else VanillaIngredient(
                    displayItem,
                    amount
                )
            }
        }
    }

    fun openGUI(player: Player, page: Int = 0) {
        val selectedRecipe = selectedRecipeMap[player.uniqueId]
        val currentGuiState = guiState[player.uniqueId]

        if (selectedRecipe == null) {
            plugin.logger.info("--- Checking permissions for player: ${player.name} ---")
            val accessibleRecipes = recipes.filter { recipe ->
                val perms = recipe.permissions
                plugin.logger.info("Checking recipe: ${recipe.id} with permissions: $perms")
                if (perms.isEmpty()) {
                    plugin.logger.info(" -> Recipe ${recipe.id} has no permissions, showing.")
                    return@filter true
                }
                val hasRequiredPermission = perms.any { permission ->
                    val hasPerm = player.hasPermission(permission)
                    plugin.logger.info(" -> Checking permission '${permission}' for recipe ${recipe.id}... Player has permission: $hasPerm")
                    hasPerm
                }
                plugin.logger.info(" -> Final decision for recipe ${recipe.id}: Show = $hasRequiredPermission")
                hasRequiredPermission
            }
            plugin.logger.info("--- Finished checking permissions. Found ${accessibleRecipes.size} accessible recipes. ---")
            
            val totalPages = if (accessibleRecipes.isEmpty()) 1 else (accessibleRecipes.size - 1) / recipesPerPage + 1
            val guiHolder = CookingGUIHolder(RECIPE_LIST_GUI_KEY, ConfigManager.cookingGuiRows * 9, ConfigManager.recipeListTitle)
            val inventory = guiHolder.inventory

            for (i in 0 until ConfigManager.cookingGuiRows * 9) inventory.setItem(i, fillerItem)

            val startIndex = page * recipesPerPage
            val endIndex = (startIndex + recipesPerPage).coerceAtMost(accessibleRecipes.size)

            for (i in startIndex until endIndex) {
                val recipe = accessibleRecipes[i]
                val displayIndex = i - startIndex
                if (displayIndex < ConfigManager.recipeDisplaySlots.size) {
                    val guiSlot = ConfigManager.recipeDisplaySlots[displayIndex]

                    // Clone the output item to preserve all NBT data (like MMOItems tags)
                    var itemToDisplay = recipe.output.itemStack.clone()
                    val isCurrentlyCrafting = craftingManager.isCrafting(player, recipe.id)

                    if (isCurrentlyCrafting) {
                        // If crafting, apply the special material from the global config
                        Material.matchMaterial(ConfigManager.craftingMaterial)?.let { itemToDisplay.type = it }
                    }

                    itemToDisplay = addCraftTimeLore(itemToDisplay, recipe.craftingTime)
                    itemToDisplay = addCraftCostLore(itemToDisplay, recipe.cost)

                    val meta = itemToDisplay.itemMeta ?: Bukkit.getItemFactory().getItemMeta(itemToDisplay.type)
                    val lore = meta.lore?.toMutableList() ?: mutableListOf()

                    if (isCurrentlyCrafting) {
                        val remainingTime = craftingManager.getRemainingCraftTime(player, recipe.id)
                        lore.add(ConfigManager.craftingInProgressLore)
                        lore.add(ConfigManager.craftingRemainingTimeLore.replace("{remainingTime}", remainingTime.toString()))
                        lore.add(ConfigManager.craftingCancelLore) // Add cancellation lore
                        meta.setCustomModelData(CRAFTING_CUSTOM_MODEL_DATA_VALUE) // Set CMD only when crafting
                    }

                    meta.lore = lore
                    meta.persistentDataContainer.set(RECIPE_ID_KEY, PersistentDataType.STRING, recipe.id)
                    itemToDisplay.itemMeta = meta

                    inventory.setItem(guiSlot, itemToDisplay)
                }
            }

            if (page > 0) {
                inventory.setItem(prevPageSlot, prevPageItem)
            }
            if (page < totalPages - 1) {
                inventory.setItem(nextPageSlot, nextPageItem)
            }
            inventory.setItem(ConfigManager.guideButtonSlot, guideButtonItem) // New: Add guide button to recipe list GUI

            openGUIs[player.uniqueId] = inventory
            guiState[player.uniqueId] = Pair(GUIType.RECIPE_LIST, page)
            player.openInventory(inventory)
        } else {

            when (currentGuiState?.first) {
                GUIType.SELECTED_RECIPE_PLACE_INGREDIENTS -> {
                    createSelectedRecipeGUI(player, selectedRecipe, false)
                    guiState[player.uniqueId] = Pair(GUIType.SELECTED_RECIPE_PLACE_INGREDIENTS, 0)
                }
                GUIType.CRAFTING_IN_PROGRESS -> {
                    // If player is already crafting, reopen the GUI with the crafting item
                    val craftingTask = craftingManager.getCraftingTask(player)
                    if (craftingTask != null) {
                        val guiHolder = CookingGUIHolder(RECIPE_LIST_GUI_KEY, ConfigManager.cookingGuiRows * 9, ConfigManager.recipeDetailTitle.replace("{recipe_name}", selectedRecipe.output.itemStack.itemMeta?.displayName ?: selectedRecipe.id))
                        val inventory = guiHolder.inventory

                        for (i in 0 until inventory.size) {
                            inventory.setItem(i, fillerItem)
                        }
                        
                        // Prepare the item for crafting display
                        var craftingDisplayItem = selectedRecipe.output.itemStack.clone()

                        // Apply the global crafting material for display
                        Material.matchMaterial(ConfigManager.craftingMaterial)?.let { craftingDisplayItem.type = it }

                        // Apply custom model data
                        val meta = craftingDisplayItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(craftingDisplayItem.type)
                        if (meta != null) {
                            meta.setCustomModelData(CRAFTING_CUSTOM_MODEL_DATA_VALUE)
                            // Also store the recipe ID in the item's PDC for cancellation/identification
                            meta.persistentDataContainer.set(RECIPE_ID_KEY, PersistentDataType.STRING, selectedRecipe.id)
                            craftingDisplayItem.itemMeta = meta
                        }

                        inventory.setItem(ConfigManager.outputSlot, craftingDisplayItem)
                        inventory.setItem(ConfigManager.backButtonSlot, backButtonItem)
                        inventory.setItem(ConfigManager.guideButtonSlot, guideButtonItem) // New: Add guide button to crafting in progress GUI

                        openGUIs[player.uniqueId] = inventory
                        player.openInventory(inventory)
                    } else {
                        // Crafting task not found, revert to recipe list
                        selectedRecipeMap.remove(player.uniqueId)
                        guiState.remove(player.uniqueId)
                        openGUI(player)
                    }
                }
                else -> {
                    createSelectedRecipeGUI(player, selectedRecipe, true)
                    guiState[player.uniqueId] = Pair(GUIType.SELECTED_RECIPE_SHOW_GHOSTS, 0)
                }
            }
        }
    }

    internal fun populateInventoryWithRecipeDetails(inventory: Inventory, recipe: CookingRecipe, showGhostItems: Boolean) {

        inventory.clear()
        for (i in 0 until inventory.size) {
            inventory.setItem(i, fillerItem)
        }

        if (showGhostItems) {

            recipe.ingredients.forEach { (slot, ingredient) ->

                if (ConfigManager.ingredientInputSlots.contains(slot)) {
                    val ghostItem = ingredient.itemStack.clone()
                    ghostItem.amount = ingredient.amount

                    val meta = ghostItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(ghostItem.type) ?: return@forEach

                    val lore = meta.lore?.toMutableList() ?: mutableListOf()

                    val itemName = if (meta.hasDisplayName()) {
                        meta.displayName
                    } else {
                        ghostItem.type.name.replace('_', ' ').split(' ').joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }
                    }

                    val placeholderLore = ConfigManager.ingredientPlaceholderItem.lore.map { rawLine ->
                        ColorUtils.translateColors(rawLine)
                            .replace("{amount}", ingredient.amount.toString())
                            .replace("{item_name}", itemName)
                    }
                    lore.addAll(0, placeholderLore)

                    meta.lore = lore
                    ghostItem.itemMeta = meta
                    inventory.setItem(slot, ghostItem)
                }
            }
        } else {

            for (slot in ConfigManager.ingredientInputSlots) {
                inventory.setItem(slot, null)
            }
        }

        val outputItemDisplay = recipe.output.itemStack.clone()
        val outputMeta = outputItemDisplay.itemMeta ?: return
        val outputLore = outputMeta.lore?.toMutableList() ?: mutableListOf()

        if (showGhostItems) {
            outputLore.add(ColorUtils.translateColors(ConfigManager.outputItemLore.confirm))
        } else {
            outputLore.add(ColorUtils.translateColors(ConfigManager.outputItemLore.craft))
        }
        outputMeta.lore = outputLore
        outputItemDisplay.itemMeta = outputMeta
        inventory.setItem(ConfigManager.outputSlot, outputItemDisplay)

        inventory.setItem(ConfigManager.backButtonSlot, backButtonItem)
        inventory.setItem(ConfigManager.guideButtonSlot, guideButtonItem) // New: Add guide button to selected recipe GUI
    }

    private fun createSelectedRecipeGUI(player: Player, selectedRecipe: CookingRecipe, showGhostItems: Boolean) {
        val recipeName = selectedRecipe.output.itemStack.itemMeta?.displayName ?: selectedRecipe.id
        val title = ConfigManager.recipeDetailTitle.replace("{recipe_name}", recipeName, ignoreCase = true)

        val guiHolder = CookingGUIHolder(RECIPE_LIST_GUI_KEY, ConfigManager.cookingGuiRows * 9, title)
        val inventory = guiHolder.inventory

        populateInventoryWithRecipeDetails(inventory, selectedRecipe, showGhostItems)

        openGUIs[player.uniqueId] = inventory

        player.openInventory(inventory)
    }

    fun craft(player: Player, guiInventory: Inventory) {
        val recipe = selectedRecipeMap[player.uniqueId]
        if (recipe == null) {
            player.sendMessage("§cNo recipe selected for crafting.")
            player.closeInventory()
            return
        }

        val result = checkAndConsumeIngredients(player, guiInventory, recipe)
        when (result) {
            IngredientCheckResult.SUCCESS -> { /* Continue to economy check */ }
            IngredientCheckResult.NOT_ENOUGH_INGREDIENTS -> {
                player.sendMessage(ConfigManager.notEnoughIngredientsMessage)
                return
            }
            IngredientCheckResult.WRONG_SLOTS -> {
                player.sendMessage(ConfigManager.ingredientsInWrongSlotMessage)
                return
            }
        }


        if (recipe.cost > 0) {
            if (economy == null) {
                player.sendMessage(ConfigManager.craftingEconomyDisabledMessage)
                player.closeInventory()
                return
            }

            val balance = economy.getBalance(player)
            if (balance < recipe.cost) {
                player.sendMessage(ConfigManager.notEnoughMoneyMessage.replace("{cost}", recipe.cost.toString(), ignoreCase = true))
                player.closeInventory()
                return
            }
            val response = economy.withdrawPlayer(player, recipe.cost)
            if (!response.transactionSuccess()) {
                player.sendMessage("§cAn error occurred with the economy: ${response.errorMessage}")
                player.closeInventory()
                return
            }
        }

        // Prepare the item for crafting display
        var craftingDisplayItem = recipe.output.itemStack.clone()

        // Apply the global crafting material for display
        Material.matchMaterial(ConfigManager.craftingMaterial)?.let { craftingDisplayItem.type = it }

        // Apply custom model data
        val meta = craftingDisplayItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(craftingDisplayItem.type)
        if (meta != null) {
            meta.setCustomModelData(CRAFTING_CUSTOM_MODEL_DATA_VALUE)
            // Also store the recipe ID in the item's PDC for cancellation/identification
            meta.persistentDataContainer.set(RECIPE_ID_KEY, PersistentDataType.STRING, recipe.id)
            craftingDisplayItem.itemMeta = meta
        }

        // Update the output slot with the crafting item
        guiInventory.setItem(ConfigManager.outputSlot, craftingDisplayItem)

        // Update GUI state to indicate crafting is in progress
        guiState[player.uniqueId] = Pair(GUIType.CRAFTING_IN_PROGRESS, 0)

        // Corrected call to startCrafting
        craftingManager.startCrafting(player, recipe.id, recipe.output.itemStack.clone(), recipe.craftingTime)

        // The callback logic for craft completion is now handled within CraftingManager.completeCraft
        // and will send messages and give items directly.
        // We no longer need the lambda here.
    }

    fun onCraftComplete(player: Player) {
        selectedRecipeMap.remove(player.uniqueId)
        guiState.remove(player.uniqueId) // Clear the crafting in progress state
        openGUIs.remove(player.uniqueId)
        openGUI(player) // Reopen RECIPE_LIST
    }

    // Removed cancelCraft from here, it's now handled by CraftingManager

    private fun checkAndConsumeIngredients(player: Player, guiInventory: Inventory, recipe: CookingRecipe): IngredientCheckResult {
        // Strict check for correct placement
        var isCorrectlyPlaced = true
        for ((slot, requiredIngredient) in recipe.ingredients) {
            val playerItem = guiInventory.getItem(slot)
            if (playerItem == null || playerItem.amount < requiredIngredient.amount || getCanonicalIngredientKey(requiredIngredient) != getCanonicalItemStackKey(playerItem)) {
                isCorrectlyPlaced = false
                break
            }
        }
        if (isCorrectlyPlaced) {
            val requiredSlots = recipe.ingredients.keys
            for (slot in ConfigManager.ingredientInputSlots) {
                if (slot !in requiredSlots && guiInventory.getItem(slot) != null) {
                    isCorrectlyPlaced = false
                    break
                }
            }
        }

        if (isCorrectlyPlaced) {
            val droppedExcessItems = mutableListOf<ItemStack>()
            for ((slot, requiredIngredient) in recipe.ingredients) {
                val playerItem = guiInventory.getItem(slot) // Guaranteed not null and correct type/amount by isCorrectlyPlaced
                if (playerItem != null) { // Defensive check, though should be true
                    val requiredAmount = requiredIngredient.amount

                    if (playerItem.amount > requiredAmount) {
                        val excessAmount = playerItem.amount - requiredAmount
                        val excessItem = playerItem.clone()
                        excessItem.amount = excessAmount

                        val leftovers = player.inventory.addItem(excessItem)
                        if (leftovers.isNotEmpty()) {
                            droppedExcessItems.addAll(leftovers.values)
                        }
                        playerItem.amount = requiredAmount // Reduce the amount in the GUI slot to the required amount
                        guiInventory.setItem(slot, playerItem) // Update the item in the GUI
                    } else { // playerItem.amount == requiredAmount
                        guiInventory.setItem(slot, null) // Consume the exact amount by clearing the slot
                    }
                }
            }

            if (droppedExcessItems.isNotEmpty()) {
                droppedExcessItems.forEach { droppedItem ->
                    player.world.dropItemNaturally(player.location, droppedItem)
                }
            }
            player.updateInventory()
            return IngredientCheckResult.SUCCESS
        }

        // Lenient check for wrong slots
        val playerItems = ConfigManager.ingredientInputSlots
            .mapNotNull { guiInventory.getItem(it)?.clone() } // Use clones to avoid modifying original items
            .toMutableList()

        val requiredIngredients = recipe.ingredients.values.toMutableList()

        if (playerItems.size != requiredIngredients.size) {
            return IngredientCheckResult.NOT_ENOUGH_INGREDIENTS
        }

        val foundAllIngredients = requiredIngredients.all { required ->
            val iterator = playerItems.iterator()
            var foundMatch = false
            while (iterator.hasNext()) {
                val placed = iterator.next()
                if (getCanonicalIngredientKey(required) == getCanonicalItemStackKey(placed) && placed.amount >= required.amount) {
                    iterator.remove() // Consume the found item
                    foundMatch = true
                    break
                }
            }
            foundMatch
        }

        return if (foundAllIngredients) {
            IngredientCheckResult.WRONG_SLOTS
        } else {
            IngredientCheckResult.NOT_ENOUGH_INGREDIENTS
        }
    }

    private fun getCanonicalIngredientKey(ingredient: Ingredient): Any {
        return when (ingredient) {
            is VanillaIngredient -> {
                val item = ingredient.itemStack
                val itemMeta = item.itemMeta
                val normalizedDisplayName = if (itemMeta?.hasDisplayName() == true) itemMeta.displayName else null
                val normalizedLore = if (itemMeta?.hasLore() == true && itemMeta.lore?.isNotEmpty() == true) itemMeta.lore else null

                VanillaKey(item.type, normalizedDisplayName, normalizedLore)
            }
            is MMOItemIngredient -> {

                MMOItemKey(ingredient.type.uppercase(Locale.getDefault()), ingredient.id.uppercase(Locale.getDefault()))
            }
            else -> throw IllegalArgumentException("Unknown ingredient type: ${ingredient::class.simpleName}")
        }
    }

    private fun getCanonicalItemStackKey(itemStack: ItemStack): Any {

        if (mmoItemsSupport.isMMOItemsEnabled) {
            val nbt = NBTItem.get(itemStack)
            val mmoItemType = MMOUtils.getType(nbt)?.name
            val mmoItemId = MMOUtils.getID(nbt)

            if (mmoItemType != null && mmoItemId != null) {

                return MMOItemKey(mmoItemType.uppercase(Locale.getDefault()), mmoItemId.uppercase(Locale.getDefault()))
            }
        }

        val itemMeta = itemStack.itemMeta
        val normalizedDisplayName = if (itemMeta?.hasDisplayName() == true) itemMeta.displayName else null
        val normalizedLore = if (itemMeta?.hasLore() == true && itemMeta.lore?.isNotEmpty() == true) itemMeta.lore else null

        return VanillaKey(itemStack.type, normalizedDisplayName, normalizedLore)
    }

    internal fun createItem(material: Material, name: String?, lore: List<String> = emptyList(), itemModelString: String? = null, customModelData: Int? = null, hideTooltip: Boolean? = null): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        name?.let { meta.setDisplayName(ColorUtils.translateColors(it)) }
        if (lore.isNotEmpty()) {
            meta.lore = lore.map { ColorUtils.translateColors(it) }
        }

        if (itemModelString != null) {
            try {
                val key = NamespacedKey.fromString(itemModelString.lowercase(Locale.getDefault()))
                if (key != null) {
                    meta.setItemModel(key)
                }
            } catch (e: IllegalArgumentException) {
                plugin.logger.warning("[CookingWorkshop] Invalid ItemModel format: '$itemModelString'. It should be 'namespace:key'.")
            }
        }

        customModelData?.let { meta.setCustomModelData(it) }

        if (hideTooltip == true) {
            meta.isHideTooltip = true
        }

        item.itemMeta = meta
        return item
    }

    private fun addCraftTimeLore(itemStack: ItemStack, craftingTime: Int): ItemStack {
        if (craftingTime <= 0) return itemStack

        val newItem = itemStack.clone()
        val meta = newItem.itemMeta ?: return newItem

        val lore = meta.lore?.toMutableList() ?: mutableListOf()
        val formattedTimeLore = ConfigManager.craftTimeLoreFormat.replace("{time}", craftingTime.toString())
        lore.add(ColorUtils.translateColors(formattedTimeLore))
        meta.lore = lore
        newItem.itemMeta = meta
        return newItem
    }

    private fun addCraftCostLore(itemStack: ItemStack, cost: Double): ItemStack {
        if (cost <= 0) return itemStack

        val newItem = itemStack.clone()
        val meta = newItem.itemMeta ?: return newItem

        val lore = meta.lore?.toMutableList() ?: mutableListOf()

        val formattedCostLore = ConfigManager.craftCostLoreFormat.replace("{cost}", cost.toString())
        lore.add(ColorUtils.translateColors(formattedCostLore))
        meta.lore = lore
        newItem.itemMeta = meta
        return newItem
    }

    fun startGUIRefreshTask() {
        if (guiRefreshTask == null || guiRefreshTask?.isCancelled == true) {
            guiRefreshTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
                // This runs asynchronously, but GUI updates must be synchronous
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    // Iterate through players who have a GUI open
                    openGUIs.keys.forEach { uuid ->
                        val player = Bukkit.getPlayer(uuid)
                        if (player != null) {
                            val guiStatePair = guiState[uuid]
                            if (guiStatePair != null) {
                                when (guiStatePair.first) {
                                    GUIType.RECIPE_LIST -> {
                                        // Check if this player has *any* active craft
                                        val hasAnyActiveCraft = recipes.any { recipe ->
                                            craftingManager.isCrafting(player, recipe.id)
                                        }
                                        if (hasAnyActiveCraft) {
                                            // Reopen the GUI to refresh the lore
                                            openGUI(player, guiStatePair.second)
                                        }
                                    }
                                    GUIType.CRAFTING_IN_PROGRESS -> {
                                        val recipe = selectedRecipeMap[uuid]
                                        val inventory = player.openInventory.topInventory
                                        if (recipe != null && inventory.holder is CookingGUIHolder) {
                                            val craftingItem = inventory.getItem(ConfigManager.outputSlot)
                                            if (craftingItem != null) {
                                                var metaChanged = false
                                                // Apply global craftingMaterial
                                                Material.matchMaterial(ConfigManager.craftingMaterial)?.let { newMaterial ->
                                                    if (craftingItem.type != newMaterial) {
                                                        craftingItem.type = newMaterial
                                                        metaChanged = true
                                                    }
                                                }

                                                // Apply custom model data
                                                val meta = craftingItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(craftingItem.type)
                                                if (meta != null) {
                                                    if (!meta.hasCustomModelData() || meta.customModelData != CRAFTING_CUSTOM_MODEL_DATA_VALUE) {
                                                        meta.setCustomModelData(CRAFTING_CUSTOM_MODEL_DATA_VALUE)
                                                        metaChanged = true
                                                    }
                                                    if (metaChanged) {
                                                        craftingItem.itemMeta = meta
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    else -> {} // Do nothing for other states
                                }
                            }
                        }
                    }
                })
            }, 0L, 20L) // Run every second (20 ticks)
        }
    }

    fun stopGUIRefreshTask() {
        guiRefreshTask?.cancel()
        guiRefreshTask = null
    }

    // This method should be called by the main plugin's onDisable method
    fun disable() {
        stopGUIRefreshTask()
        // Any other cleanup for CookingWorkshop if needed
    }
}
