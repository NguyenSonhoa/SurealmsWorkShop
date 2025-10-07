package me.sanenuyan.surealmsWorkshop.hooks

import io.lumine.mythic.lib.api.item.NBTItem
import me.sanenuyan.surealmsWorkshop.SurealmsWorkshop
import me.sanenuyan.surealmsWorkshop.workshops.Ingredient
import me.sanenuyan.surealmsWorkshop.workshops.RecipeOutput
import org.bukkit.inventory.ItemStack
import net.Indyuce.mmoitems.MMOItems
import net.Indyuce.mmoitems.util.MMOUtils
import org.bukkit.Material
import org.bukkit.inventory.meta.ItemMeta

class MMOItemIngredient(
    private val plugin: SurealmsWorkshop,
    val type: String,
    val id: String,
    override val amount: Int,
    private val displayItem: ItemStack
) : Ingredient {
    override val itemStack: ItemStack
        get() {
            val mmoItemType = MMOItems.plugin.types.get(type)
            if (mmoItemType == null) {
                plugin.logger.warning("MMOItem type '$type' for recipe ingredient '$id' not found. Please check your MMOItems configuration.")
                return createErrorItem("Invalid MMOItem Type", "Type: $type", "ID: $id")
            }

            val mmoItem = MMOItems.plugin.getItem(mmoItemType, id)
            if (mmoItem == null) {
                plugin.logger.warning("MMOItem ID '$id' for type '$type' not found. Please check your MMOItems configuration.")
                return createErrorItem("Invalid MMOItem ID", "Type: $type", "ID: $id")
            }

            return mmoItem.apply { this.amount = this@MMOItemIngredient.amount }
        }

    override fun matches(item: ItemStack?): Boolean {
        if (item == null) return false
        val nbt = NBTItem.get(item)

        return MMOUtils.getType(nbt)?.name == type && MMOUtils.getID(nbt) == id
    }

    private fun createErrorItem(name: String, vararg lore: String): ItemStack {
        val item = ItemStack(Material.BARRIER)
        val meta: ItemMeta? = item.itemMeta
        meta?.setDisplayName("§c§lERROR: $name")
        meta?.lore = lore.map { "§7$it" } + listOf("§7(Check server console for details)")
        item.itemMeta = meta
        return item
    }
}

class MMOItemOutput(
    private val plugin: SurealmsWorkshop,
    private val type: String,
    private val id: String,
    private val amount: Int,
    private val displayItem: ItemStack
) : RecipeOutput {
    override val itemStack: ItemStack
        get() {
            val mmoItemType = MMOItems.plugin.types.get(type)
            if (mmoItemType == null) {
                plugin.logger.warning("MMOItem type '$type' for recipe output '$id' not found. Please check your MMOItems configuration.")
                return createErrorItem("Invalid MMOItem Type", "Type: $type", "ID: $id")
            }

            val mmoItem = MMOItems.plugin.getItem(mmoItemType, id)
            if (mmoItem == null) {
                plugin.logger.warning("MMOItem ID '$id' for type '$type' not found. Please check your MMOItems configuration.")
                return createErrorItem("Invalid MMOItem ID", "Type: $type", "ID: $id")
            }

            return mmoItem.apply { this.amount = this@MMOItemOutput.amount }
        }

    private fun createErrorItem(name: String, vararg lore: String): ItemStack {
        val item = ItemStack(Material.BARRIER)
        val meta: ItemMeta? = item.itemMeta
        meta?.setDisplayName("§c§lERROR: $name")
        meta?.lore = lore.map { "§7$it" } + listOf("§7(Check server console for details)")
        item.itemMeta = meta
        return item
    }
}

class MMOItemsSupport(private val plugin: SurealmsWorkshop) {

    val isMMOItemsEnabled: Boolean
        get() = plugin.server.pluginManager.getPlugin("MMOItems") != null

    fun createMMOItemIngredient(type: String, id: String, amount: Int, displayItem: ItemStack): MMOItemIngredient {
        return MMOItemIngredient(plugin, type, id, amount, displayItem)
    }

    fun createMMOItemOutput(type: String, id: String, amount: Int, displayItem: ItemStack): MMOItemOutput {
        return MMOItemOutput(plugin, type, id, amount, displayItem)
    }
}