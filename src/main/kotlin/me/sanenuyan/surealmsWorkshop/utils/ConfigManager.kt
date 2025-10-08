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
    var guiTitle: String = ""
    var outputSlot: Int = 30
    var backButtonSlot: Int = 0
    var ingredientInputSlots: List<Int> = listOf(18, 19, 27, 28, 36, 37)
    var recipeDisplaySlots: List<Int> = emptyList()
    var cookingGuiRows: Int = 6
    var recipeListTitle: String = ""
    var recipeDetailTitle: String = ""
    var guiClickSound: String = "ui.button.click"
    var guiClickSoundVolume: Float = 1.0f
    var guiClickSoundPitch: Float = 1.0f
    var craftingCustomModelData: Int = 1000

    // GUI Item Configurations - Loaded from config.yml
    var backButtonItem: ItemConfig = ItemConfig("ARROW", "", emptyList())
    var nextPageItem: ItemConfig = ItemConfig("ARROW", "", emptyList())
    var prevPageItem: ItemConfig = ItemConfig("ARROW", "", emptyList())
    var emptySlotItem: ItemConfig = ItemConfig("BLACK_STAINED_GLASS_PANE", " ")
    var ingredientPlaceholderItem: ItemConfig = ItemConfig("GRAY_STAINED_GLASS_PANE", "", emptyList())
    var craftButtonItem: ItemConfig = ItemConfig("ANVIL", "", emptyList())

    // Output Item Lore - Loaded from config.yml
    var outputItemLore: OutputItemLoreConfig = OutputItemLoreConfig(
        confirm = "",
        craft = "",
        success = "",
        failure = ""
    )

    // Dynamic Lore Formats - Loaded from messages.yml
    var craftTimeLoreFormat: String = ""
    var craftCostLoreFormat: String = ""

    // General Messages - Loaded from messages.yml
    var reloadSuccessMessage: String = ""
    var noPermissionMessage: String = ""
    var playerNotFoundMessage: String = ""
    var craftSuccessMessage: String = ""
    var notEnoughIngredientsMessage: String = ""
    var ingredientsInWrongSlotMessage: String = ""
    var craftSuccessWithCostMessage: String = ""
    var notEnoughMoneyMessage: String = ""

    // CraftingManager Messages - Loaded from messages.yml
    var craftingAlreadyCraftingMessage: String = ""
    var craftingStartedMessage: String = ""
    var craftingCancelledPlayerMessage: String = ""
    var craftingNotCraftingMessage: String = ""
    var craftingCancelledServerMessage: String = ""
    var craftingEconomyDisabledMessage: String = ""
    var craftingInProgressLore: String = ""
    var craftingRemainingTimeLore: String = ""
    var craftingCancelledLore: String = ""
    var craftingCancelLore: String = ""
    var craftingCancelledIngredientsReturnedMessage: String = ""
    var craftingCancelledIngredientsDroppedMessage: String = ""

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

        // Load GUI General Settings from config.yml (non-message related)
        outputSlot = config.getInt("cookingWorkshop.outputSlot", outputSlot)
        backButtonSlot = config.getInt("cookingWorkshop.backButtonSlot", backButtonSlot)
        ingredientInputSlots = config.getIntegerList("cookingWorkshop.ingredientInputSlots").ifEmpty { ingredientInputSlots }
        recipeDisplaySlots = config.getIntegerList("cookingWorkshop.recipeDisplaySlots").ifEmpty { recipeDisplaySlots }
        cookingGuiRows = config.getInt("cookingWorkshop.cookingGuiRows", cookingGuiRows)
        guiClickSound = config.getString("cookingWorkshop.guiClickSound", guiClickSound) ?: guiClickSound
        guiClickSoundVolume = config.getDouble("cookingWorkshop.guiClickSoundVolume", guiClickSoundVolume.toDouble()).toFloat()
        guiClickSoundPitch = config.getDouble("cookingWorkshop.guiClickSoundPitch", guiClickSoundPitch.toDouble()).toFloat()
        craftingCustomModelData = config.getInt("cookingWorkshop.craftingCustomModelData", craftingCustomModelData)

        // Load GUI Titles from config.yml
        guiTitle = getString(config, "cookingWorkshop.guiTitle", "&8Cooking Workshop")
        recipeListTitle = getString(config, "cookingWorkshop.recipeListTitle", "&8Cooking Recipes")
        recipeDetailTitle = getString(config, "cookingWorkshop.recipeDetailTitle", "&8Recipe: {recipe_name}")

        // Load GUI Item Configurations from config.yml
        backButtonItem = loadItemConfigFromConfig(config, "cookingWorkshop.backButtonItem", backButtonItem)
        nextPageItem = loadItemConfigFromConfig(config, "cookingWorkshop.nextPageItem", nextPageItem)
        prevPageItem = loadItemConfigFromConfig(config, "cookingWorkshop.prevPageItem", prevPageItem)
        emptySlotItem = loadItemConfigFromConfig(config, "cookingWorkshop.emptySlotItem", emptySlotItem)
        ingredientPlaceholderItem = loadItemConfigFromConfig(config, "cookingWorkshop.ingredientPlaceholderItem", ingredientPlaceholderItem)
        craftButtonItem = loadItemConfigFromConfig(config, "cookingWorkshop.craftButtonItem", craftButtonItem)

        // Load Output Item Lore from config.yml
        outputItemLore = OutputItemLoreConfig(
            confirm = getString(config, "cookingWorkshop.outputItemLore.confirm", "&aClick to confirm recipe and place ingredients!"),
            craft = getString(config, "cookingWorkshop.outputItemLore.craft", "&aClick to Craft!"),
            success = getString(config, "cookingWorkshop.outputItemLore.success", "&aCrafting successful!"),
            failure = getString(config, "cookingWorkshop.outputItemLore.failure", "&cCrafting failed! Check ingredients.")
        )

        // Load Dynamic Lore Formats from messages.yml
        craftTimeLoreFormat = getMessagesString("craft-time-lore-format", "&7Crafting Time: {time}s")
        craftCostLoreFormat = getMessagesString("craft-cost-lore-format", "&eCost: &f{cost}")

        // Load General Messages from messages.yml
        reloadSuccessMessage = getMessagesString("reload-success", "&aSuccessfully reloaded the configuration and recipes.")
        noPermissionMessage = getMessagesString("no-permission", "&cYou do not have permission to use this command.")
        playerNotFoundMessage = getMessagesString("player-not-found", "&cPlayer not found: {player_name}")
        craftSuccessMessage = getMessagesString("craft-success", "&aYou have successfully crafted {item_name}&a!")
        notEnoughIngredientsMessage = getMessagesString("not-enough-ingredients", "&cYou do not have enough ingredients to craft this.")
        ingredientsInWrongSlotMessage = getMessagesString("ingredients-in-wrong-slot", "&cIngredients are in the wrong slots!")
        craftSuccessWithCostMessage = getMessagesString("craft-success-with-cost", "&aYou have successfully crafted {item_name}&a for &e{cost}&a!")
        notEnoughMoneyMessage = getMessagesString("not-enough-money", "&cYou don't have enough money to craft this! You need &e{cost}&c.")

        // Load CraftingManager Messages from messages.yml
        craftingAlreadyCraftingMessage = getMessagesString("crafting-already-crafting", "&cYou are already crafting an item!")
        craftingStartedMessage = getMessagesString("crafting-started", "&aCrafting %item_name% for %time% seconds...")
        craftingCancelledPlayerMessage = getMessagesString("crafting-cancelled-player", "&cCrafting of %item_name% cancelled.")
        craftingNotCraftingMessage = getMessagesString("crafting-not-crafting", "&cYou are not currently crafting anything.")
        craftingCancelledServerMessage = getMessagesString("crafting-cancelled-server", "&cCrafting cancelled due to server shutdown/plugin reload.")
        craftingEconomyDisabledMessage = getMessagesString("crafting-economy-disabled", "&cEconomy features are disabled. Cannot process money costs.")
        craftingInProgressLore = getMessagesString("crafting-in-progress-lore", "&eĐang chế tạo...")
        craftingRemainingTimeLore = getMessagesString("crafting-remaining-time-lore", "&7Thời gian còn lại: &e{remainingTime}s")
        craftingCancelledLore = getMessagesString("crafting-cancelled-lore", "&cĐã hủy chế tạo")
        craftingCancelLore = getMessagesString("crafting-cancel-lore", "&cClick để hủy chế tạo")
        craftingCancelledIngredientsReturnedMessage = getMessagesString("crafting-cancelled-ingredients-returned", "&aCraft cancelled. Ingredients returned to your inventory.")
        craftingCancelledIngredientsDroppedMessage = getMessagesString("crafting-cancelled-ingredients-dropped", "&eCraft cancelled. Some ingredients were dropped on the ground due to full inventory.")
    }

    private fun loadItemConfigFromConfig(config: FileConfiguration, path: String, default: ItemConfig): ItemConfig {
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

    private fun getMessagesStringList(path: String, default: List<String>): List<String> {
        return (messagesConfig.getStringList(path).ifEmpty { default }).map { ColorUtils.translateColors(it) }
    }
}