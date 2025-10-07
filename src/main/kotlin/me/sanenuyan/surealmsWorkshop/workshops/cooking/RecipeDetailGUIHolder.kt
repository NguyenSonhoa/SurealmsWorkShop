package me.sanenuyan.surealmsWorkshop.workshops.cooking

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.NamespacedKey
import org.bukkit.Bukkit
import me.sanenuyan.surealmsWorkshop.workshops.cooking.CookingRecipe

class RecipeDetailGUIHolder(val guiIdentifier: NamespacedKey, private val size: Int, private val title: String, val recipe: CookingRecipe) : InventoryHolder {

    private val inventory: Inventory = Bukkit.createInventory(this, size, title)

    override fun getInventory(): Inventory {
        return inventory
    }
}
