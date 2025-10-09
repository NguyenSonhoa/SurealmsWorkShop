package me.sanenuyan.surealmsWorkshop.workshops.cooking

import me.sanenuyan.surealmsWorkshop.workshops.Ingredient
import me.sanenuyan.surealmsWorkshop.workshops.RecipeOutput

data class CookingRecipe(
    val id: String,
    val ingredients: Map<Int, Ingredient>,
    val output: RecipeOutput,
    val cost: Double,
    val sound: String?,
    val craftingTime: Int,
    val priority: Int,
    val craftingMaterial: String?,
    val permissions: List<String>
)