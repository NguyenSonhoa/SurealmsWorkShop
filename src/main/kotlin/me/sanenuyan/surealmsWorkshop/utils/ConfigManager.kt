package me.sanenuyan.surealmsWorkshop.utils

import me.sanenuyan.surealmsWorkshop.SurealmsWorkshop
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

object ConfigManager {

    // Data classes for nested configurations
    data class ItemConfig(
        val material: String,
        val name: String,
        val lore: List<String> = emptyList()
    )

    data class OutputItemLoreConfig(
        val confirm: String,
        val craft: String,
        val success: String,
        val failure: String
    )

    // GUI General Settings
    var guiSize: Int = 54
    var guiTitle: String = "&8Cooking Workshop"
    var outputSlot: Int = 30
    var backButtonSlot: Int = 0
    var ingredientInputSlots: List<Int> = listOf(18, 19, 27, 28, 36, 37)
    var recipeDisplaySlots: List<Int> = emptyList()
    var cookingGuiRows: Int = 6 // Added
    var recipeListTitle: String = "&8Cooking Recipes" // Added
    var recipeDetailTitle: String = "&8Recipe: {recipe_name}" // Added
    var craftTimeLoreFormat: String = "&7Crafting Time: {time}s" // Added
    var craftCostLoreFormat: String = "&eCost: &f{cost}"
    var guiClickSound: String = "ui.button.click"
    var guiClickSoundVolume: Float = 1.0f
    var guiClickSoundPitch: Float = 1.0f

    // GUI Item Configurations
    var backButtonItem: ItemConfig = ItemConfig("ARROW", "&aBack to Recipes", listOf("&7Click to return to the recipe list."))
    var nextPageItem: ItemConfig = ItemConfig("ARROW", "&aNext Page", listOf("&7Click to view the next page of recipes."))
    var prevPageItem: ItemConfig = ItemConfig("ARROW", "&aPrevious Page", listOf("&7Click to view the previous page of recipes."))
    var emptySlotItem: ItemConfig = ItemConfig("BLACK_STAINED_GLASS_PANE", " ")
    var ingredientPlaceholderItem: ItemConfig = ItemConfig("GRAY_STAINED_GLASS_PANE", "&7Required Ingredient", listOf("&7Place your ingredients here."))
    var craftButtonItem: ItemConfig = ItemConfig("ANVIL", "&aCraft", listOf("&7Click to craft the selected recipe.")) // Added

    // Output Item Lore
    var outputItemLore: OutputItemLoreConfig = OutputItemLoreConfig(
        confirm = "&aClick to confirm recipe and place ingredients!",
        craft = "&aClick to Craft!",
        success = "&aCrafting successful!",
        failure = "&cCrafting failed! Check ingredients."
    )

    // General Messages
    var reloadSuccessMessage: String = "&aSuccessfully reloaded the configuration and recipes."
    var noPermissionMessage: String = "&cYou do not have permission to use this command."
    var playerNotFoundMessage: String = "&cPlayer not found: {player_name}"
    var craftSuccessMessage: String = "&aYou have successfully crafted {item_name}&a!"
    var notEnoughIngredientsMessage: String = "&cYou do not have enough ingredients to craft this."
    var craftSuccessWithCostMessage: String = "&aYou have successfully crafted {item_name}&a for &e{cost}&a!"
    var notEnoughMoneyMessage: String = "&cYou don't have enough money to craft this! You need &e{cost}&c."

    // CraftingManager Messages
    var craftingAlreadyCraftingMessage: String = "&cYou are already crafting an item!"
    var craftingStartedMessage: String = "&aCrafting %item_name% for %time% seconds..."
    var craftingCancelledPlayerMessage: String = "&cCrafting of %item_name% cancelled."
    var craftingNotCraftingMessage: String = "&cYou are not currently crafting anything."
    var craftingCancelledServerMessage: String = "&cCrafting cancelled due to server shutdown/plugin reload."
    var craftingEconomyDisabledMessage: String = "&cEconomy features are disabled. Cannot process money costs."

    private lateinit var messagesConfig: FileConfiguration

    fun load(plugin: SurealmsWorkshop) {
        // Load config.yml
        plugin.saveDefaultConfig()
        plugin.reloadConfig()
        val config = plugin.config

        // Load messages.yml
        val messagesFile = File(plugin.dataFolder, "messages.yml")
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false)
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile)

        // Load GUI General Settings
        guiSize = config.getInt("cookingWorkshop.guiSize", guiSize)
        guiTitle = getString(config, "cookingWorkshop.guiTitle", guiTitle)
        outputSlot = config.getInt("cookingWorkshop.outputSlot", outputSlot)
        backButtonSlot = config.getInt("cookingWorkshop.backButtonSlot", backButtonSlot)
        ingredientInputSlots = config.getIntegerList("cookingWorkshop.ingredientInputSlots").ifEmpty { ingredientInputSlots }
        recipeDisplaySlots = config.getIntegerList("cookingWorkshop.recipeDisplaySlots").ifEmpty { recipeDisplaySlots }
        cookingGuiRows = config.getInt("cookingWorkshop.cookingGuiRows", cookingGuiRows) // Added
        recipeListTitle = getString(config, "cookingWorkshop.recipeListTitle", recipeListTitle) // Added
        recipeDetailTitle = getString(config, "cookingWorkshop.recipeDetailTitle", recipeDetailTitle) // Added
        craftTimeLoreFormat = getString(config, "cookingWorkshop.craftTimeLoreFormat", craftTimeLoreFormat) // Added
        craftCostLoreFormat = getString(config, "cookingWorkshop.craftCostLoreFormat", craftCostLoreFormat)
        guiClickSound = config.getString("cookingWorkshop.guiClickSound", guiClickSound) ?: guiClickSound
        guiClickSoundVolume = config.getDouble("cookingWorkshop.guiClickSoundVolume", guiClickSoundVolume.toDouble()).toFloat()
        guiClickSoundPitch = config.getDouble("cookingWorkshop.guiClickSoundPitch", guiClickSoundPitch.toDouble()).toFloat()


        // Load GUI Item Configurations
        backButtonItem = loadItemConfig(config, "cookingWorkshop.backButtonItem", backButtonItem)
        nextPageItem = loadItemConfig(config, "cookingWorkshop.nextPageItem", nextPageItem)
        prevPageItem = loadItemConfig(config, "cookingWorkshop.prevPageItem", prevPageItem)
        emptySlotItem = loadItemConfig(config, "cookingWorkshop.emptySlotItem", emptySlotItem)
        ingredientPlaceholderItem = loadItemConfig(config, "cookingWorkshop.ingredientPlaceholderItem", ingredientPlaceholderItem)
        craftButtonItem = loadItemConfig(config, "cookingWorkshop.craftButtonItem", craftButtonItem) // Added

        // Load Output Item Lore
        outputItemLore = OutputItemLoreConfig(
            confirm = getString(config, "cookingWorkshop.outputItemLore.confirm", outputItemLore.confirm),
            craft = getString(config, "cookingWorkshop.outputItemLore.craft", outputItemLore.craft),
            success = getString(config, "cookingWorkshop.outputItemLore.success", outputItemLore.success),
            failure = getString(config, "cookingWorkshop.outputItemLore.failure", outputItemLore.failure)
        )

        // Load General Messages
        reloadSuccessMessage = getString(config, "messages.reload_success", reloadSuccessMessage)
        noPermissionMessage = getString(config, "messages.no_permission", noPermissionMessage)
        playerNotFoundMessage = getString(config, "messages.player_not_found", playerNotFoundMessage)
        craftSuccessMessage = getString(config, "messages.craft_success", craftSuccessMessage)
        notEnoughIngredientsMessage = getString(config, "messages.not_enough_ingredients", notEnoughIngredientsMessage)
        craftSuccessWithCostMessage = getString(config, "messages.craft_success_with_cost", craftSuccessWithCostMessage)
        notEnoughMoneyMessage = getString(config, "messages.not_enough_money", notEnoughMoneyMessage)

        // Load CraftingManager Messages from messages.yml
        craftingAlreadyCraftingMessage = getMessagesString("crafting-already-crafting", craftingAlreadyCraftingMessage)
        craftingStartedMessage = getMessagesString("crafting-started", craftingStartedMessage)
        craftingCancelledPlayerMessage = getMessagesString("crafting-cancelled-player", craftingCancelledPlayerMessage)
        craftingNotCraftingMessage = getMessagesString("crafting-not-crafting", craftingNotCraftingMessage)
        craftingCancelledServerMessage = getMessagesString("crafting-cancelled-server", craftingCancelledServerMessage)
        craftingEconomyDisabledMessage = getMessagesString("crafting-economy-disabled", craftingEconomyDisabledMessage)
    }

    private fun loadItemConfig(config: FileConfiguration, path: String, default: ItemConfig): ItemConfig {
        val material = config.getString("$path.material", default.material) ?: default.material
        val name = getString(config, "$path.name", default.name)
        val lore = getStringList(config, "$path.lore", default.lore)
        return ItemConfig(material, name, lore)
    }

    private fun getString(config: FileConfiguration, path: String, default: String): String {
        return ColorUtils.translateColors(config.getString(path, default) ?: default)
    }

    private fun getStringList(config: FileConfiguration, path: String, default: List<String>): List<String> {
        return (config.getStringList(path).ifEmpty { default }).map { ColorUtils.translateColors(it) }
    }

    private fun getMessagesString(path: String, default: String): String {
        return ColorUtils.translateColors(messagesConfig.getString(path, default) ?: default)
    }
}