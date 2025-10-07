package me.sanenuyan.surealmsWorkshop.workshops.cooking

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.NamespacedKey
import org.bukkit.Bukkit

class RecipeListGUIHolder(val guiIdentifier: NamespacedKey, private val size: Int, private val title: String) : InventoryHolder {
    private val inventory: Inventory = Bukkit.createInventory(this, size, title)

    override fun getInventory(): Inventory {
        return inventory
    }
}
