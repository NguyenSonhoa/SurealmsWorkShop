package me.sanenuyan.surealmsWorkshop.workshops

import org.bukkit.inventory.ItemStack

class VanillaIngredient(
    override val itemStack: ItemStack,
    override val amount: Int
) : Ingredient {
    override fun matches(item: ItemStack?): Boolean {
        if (item == null) return false

        return item.type == itemStack.type && item.itemMeta?.displayName == itemStack.itemMeta?.displayName
    }
}