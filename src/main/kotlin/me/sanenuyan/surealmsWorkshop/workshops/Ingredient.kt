package me.sanenuyan.surealmsWorkshop.workshops

import org.bukkit.inventory.ItemStack

interface Ingredient {
    val itemStack: ItemStack
    val amount: Int
    fun matches(item: ItemStack?): Boolean
}
