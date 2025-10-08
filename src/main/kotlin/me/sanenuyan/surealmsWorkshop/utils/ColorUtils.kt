package me.sanenuyan.surealmsWorkshop.utils

import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit

object ColorUtils {

    private val VERSION = Bukkit.getServer().bukkitVersion.split("-")[0].split(".")[1].toInt()

    fun translateColors(text: String): String {
        return if (VERSION >= 16) {
            ChatColor.translateAlternateColorCodes('&', translateHexColorCodes("&#", "", text))
        } else {
            ChatColor.translateAlternateColorCodes('&', text)
        }
    }

    fun translateColors(lore: List<String>): List<String> {
        return lore.map { translateColors(it) }
    }

    private fun translateHexColorCodes(startTag: String, endTag: String, message: String): String {
        val regex = Regex("${startTag}([0-9a-fA-F]){6}${endTag}")
        var result = message
        for (match in regex.findAll(message)) {
            val hexColor = match.value.replace(startTag, "").replace(endTag, "")
            result = result.replace(match.value, ChatColor.of("#$hexColor").toString())
        }
        return result
    }

    fun stripColor(text: String): String {
        return ChatColor.stripColor(text)
    }
}