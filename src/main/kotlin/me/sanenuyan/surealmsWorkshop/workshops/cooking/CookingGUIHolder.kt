package me.sanenuyan.surealmsWorkshop.workshops.cooking

import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.NamespacedKey
import me.sanenuyan.surealmsWorkshop.workshops.cooking.CookingRecipe // Import CookingRecipe

class CookingGUIHolder(
    val guiIdentifier: NamespacedKey,
    private val size: Int,
    private val title: String,
    val recipe: CookingRecipe? = null
) : InventoryHolder {
    private val _inventory: Inventory = Bukkit.createInventory(this, size, title)

    override fun getInventory(): Inventory = _inventory
}
