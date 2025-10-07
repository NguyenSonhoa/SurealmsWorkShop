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

class CookingWorkshop(internal val plugin: SurealmsWorkshop, private val craftingManager: CraftingManager, private val economy: Economy?) {

    internal val recipes = mutableListOf<CookingRecipe>()
    internal val openGUIs = mutableMapOf<UUID, Inventory>()
    internal val guiState = mutableMapOf<UUID, Pair<GUIType, Int>>()
    internal val selectedRecipeMap = mutableMapOf<UUID, CookingRecipe?>()

    private val mmoItemsSupport: MMOItemsSupport = MMOItemsSupport(plugin)
    internal val RECIPE_ID_KEY = NamespacedKey(plugin, "recipe_id")
    internal val RECIPE_LIST_GUI_KEY = NamespacedKey(plugin, "recipe_list_gui")

    internal val nextPageItem: ItemStack
    internal val prevPageItem: ItemStack
    internal val craftButtonItem: ItemStack
    internal val backButtonItem: ItemStack
    internal val fillerItem: ItemStack
    internal val requiredIngredientItem: ItemStack

    internal val craftButtonSlot = 22
    internal val backButtonSlot = ConfigManager.backButtonSlot
    internal val nextPageSlot = 26
    internal val prevPageSlot = 18
    internal val recipesPerPage = 28

    internal val ingredientInputSlots = ConfigManager.ingredientInputSlots

    enum class GUIType { RECIPE_LIST, SELECTED_RECIPE_SHOW_GHOSTS, SELECTED_RECIPE_PLACE_INGREDIENTS }

    private data class VanillaKey(val material: Material, val displayName: String?, val lore: List<String>?)
    private data class MMOItemKey(val type: String, val id: String)

    init {

        nextPageItem = createItem(Material.matchMaterial(ConfigManager.nextPageItem.material) ?: Material.ARROW, ConfigManager.nextPageItem.name, ConfigManager.nextPageItem.lore)
        prevPageItem = createItem(Material.matchMaterial(ConfigManager.prevPageItem.material) ?: Material.ARROW, ConfigManager.prevPageItem.name, ConfigManager.prevPageItem.lore)
        craftButtonItem = createItem(Material.matchMaterial(ConfigManager.craftButtonItem.material) ?: Material.ANVIL, ConfigManager.craftButtonItem.name, ConfigManager.craftButtonItem.lore)
        backButtonItem = createItem(Material.matchMaterial(ConfigManager.backButtonItem.material) ?: Material.BARRIER, ConfigManager.backButtonItem.name, ConfigManager.backButtonItem.lore)
        fillerItem = createItem(Material.matchMaterial(ConfigManager.emptySlotItem.material) ?: Material.GRAY_STAINED_GLASS_PANE, ConfigManager.emptySlotItem.name, ConfigManager.emptySlotItem.lore)
        requiredIngredientItem = createItem(
            Material.matchMaterial(ConfigManager.ingredientPlaceholderItem.material) ?: Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            ConfigManager.ingredientPlaceholderItem.name,
            ConfigManager.ingredientPlaceholderItem.lore
        )

        loadRecipes()
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

                recipes.add(CookingRecipe(key, ingredients, output, cost, sound, craftingTime, priority))
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

        val materialName = section.getString("material")
        val mmoItemSection = section.getConfigurationSection("mmoitem")

        val displayItem = when {
            mmoItemSection != null -> {
                createItem(Material.PAPER, name ?: "MMOItem", lore)
            }
            materialName != null -> {
                val material = Material.matchMaterial(materialName) ?: throw IllegalArgumentException("Invalid material: $materialName")
                createItem(material, name, lore)
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

            val totalPages = if (recipes.isEmpty()) 1 else (recipes.size - 1) / recipesPerPage + 1
            val guiHolder = CookingGUIHolder(RECIPE_LIST_GUI_KEY, ConfigManager.cookingGuiRows * 9, ConfigManager.recipeListTitle)
            val inventory = guiHolder.inventory

            for (i in 0 until ConfigManager.cookingGuiRows * 9) inventory.setItem(i, fillerItem)

            val startIndex = page * recipesPerPage
            val endIndex = (startIndex + recipesPerPage).coerceAtMost(recipes.size)

            for (i in startIndex until endIndex) {
                val recipe = recipes[i]
                val displayIndex = i - startIndex
                if (displayIndex < ConfigManager.recipeDisplaySlots.size) {
                    val guiSlot = ConfigManager.recipeDisplaySlots[displayIndex]
                    var itemWithLore = addCraftTimeLore(recipe.output.itemStack, recipe.craftingTime)
                    itemWithLore = addCraftCostLore(itemWithLore, recipe.cost)

                    val itemMeta = itemWithLore.itemMeta
                    if (itemMeta != null) {
                        itemMeta.persistentDataContainer.set(RECIPE_ID_KEY, PersistentDataType.STRING, recipe.id)
                        itemWithLore.itemMeta = itemMeta
                    }

                    inventory.setItem(guiSlot, itemWithLore)
                }
            }

            if (page > 0) {
                inventory.setItem(prevPageSlot, prevPageItem)
            }
            if (page < totalPages - 1) {
                inventory.setItem(nextPageSlot, nextPageItem)
            }

            openGUIs[player.uniqueId] = inventory
            guiState[player.uniqueId] = Pair(GUIType.RECIPE_LIST, page)
            player.openInventory(inventory)
        } else {

            when (currentGuiState?.first) {
                GUIType.SELECTED_RECIPE_PLACE_INGREDIENTS -> {
                    createSelectedRecipeGUI(player, selectedRecipe, false)
                    guiState[player.uniqueId] = Pair(GUIType.SELECTED_RECIPE_PLACE_INGREDIENTS, 0)
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

        if (!checkAndConsumeIngredients(player, guiInventory, recipe)) {
            player.sendMessage(ConfigManager.notEnoughIngredientsMessage)

            return
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

        craftingManager.startCrafting(player, recipe.output.itemStack, recipe.craftingTime, recipe.id) {

            val itemName = recipe.output.itemStack.itemMeta?.displayName ?: recipe.id

            if (recipe.cost > 0) {
                player.sendMessage(ConfigManager.craftSuccessWithCostMessage
                    .replace("{item_name}", itemName, ignoreCase = true)
                    .replace("{cost}", recipe.cost.toString(), ignoreCase = true))
            } else {
                player.sendMessage(ConfigManager.craftSuccessMessage.replace("{item_name}", itemName, ignoreCase = true))
            }

            recipe.sound?.let { soundName ->
                if (soundName.contains(":")) {

                    try {
                        player.playSound(player.location, soundName, 1.0f, 1.0f)
                    } catch (e: Exception) {
                        plugin.logger.warning("Failed to play custom sound '$soundName' for recipe '${recipe.id}': ${e.message}")
                    }
                }
                else {

                    try {
                        val sound = Sound.valueOf(soundName.uppercase())
                        player.playSound(player.location, sound, 1.0f, 1.0f)
                    } catch (e: IllegalArgumentException) {
                        plugin.logger.warning("Invalid built-in sound name '$soundName' in recipe '${recipe.id}'. Please check Spigot Sound enums.")
                    }
                }
            }

            player.closeInventory()
        }
    }

    private fun checkAndConsumeIngredients(player: Player, guiInventory: Inventory, recipe: CookingRecipe): Boolean {

        for ((slot, requiredIngredient) in recipe.ingredients) {
            val playerItem = guiInventory.getItem(slot)

            if (playerItem == null || playerItem.type == Material.AIR) {
                return false
            }

            if (playerItem.amount < requiredIngredient.amount) {
                return false
            }

            val requiredKey = getCanonicalIngredientKey(requiredIngredient)
            val playerItemKey = getCanonicalItemStackKey(playerItem)
            if (requiredKey != playerItemKey) {
                return false
            }
        }

        val requiredSlots = recipe.ingredients.keys
        for (slot in ConfigManager.ingredientInputSlots) {
            if (slot !in requiredSlots) {
                val itemInSlot = guiInventory.getItem(slot)
                if (itemInSlot != null && itemInSlot.type != Material.AIR) {
                    return false
                }
            }
        }

        for ((slot, requiredIngredient) in recipe.ingredients) {
            val playerItem = guiInventory.getItem(slot)!!
            playerItem.amount -= requiredIngredient.amount
            if (playerItem.amount <= 0) {
                guiInventory.setItem(slot, null)
            } else {
                guiInventory.setItem(slot, playerItem)
            }
        }

        player.updateInventory()
        return true
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

    internal fun createItem(material: Material, name: String?, lore: List<String> = emptyList()): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        name?.let { meta.setDisplayName(ColorUtils.translateColors(it)) }
        if (lore.isNotEmpty()) {
            meta.lore = lore.map { ColorUtils.translateColors(it) }
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
}