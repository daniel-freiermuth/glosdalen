package com.glosdalen.app.domain.template

import com.glosdalen.app.backend.deepl.Language
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeckNameTemplateResolver @Inject constructor() {
    
    fun resolveDeckName(
        templateString: String,
        foreignLanguage: Language,
        today: LocalDate = LocalDate.now(),
        locale: Locale = Locale.getDefault()
    ): String {
        var resolved = templateString
        
        // Foreign language name templates - lowercase
        resolved = resolved.replace("{foreign_native}", foreignLanguage.nativeName.lowercase())
        resolved = resolved.replace("{foreign_english}", foreignLanguage.displayName.lowercase())
        
        // Foreign language name templates - uppercase (capitalized)
        resolved = resolved.replace("{Foreign_native}", foreignLanguage.nativeName)
        resolved = resolved.replace("{Foreign_english}", foreignLanguage.displayName)
        
        // Date templates
        resolved = resolved.replace("{day}", String.format("%02d", today.dayOfMonth))
        resolved = resolved.replace("{month}", String.format("%02d", today.monthValue))
        resolved = resolved.replace("{year}", today.year.toString())
        // Count weeks within the calendar year so {week} stays consistent with {year};
        // the partial week before the locale's first full week (week 0) counts as week 1
        resolved = resolved.replace("{week}", maxOf(1, today.get(WeekFields.of(locale).weekOfYear())).toString())
        
        return resolved.trim()
    }
    
    fun getAvailableTemplates(): List<TemplateInfo> {
        return listOf(
            // Foreign language names - lowercase
            TemplateInfo("{foreign_native}", "Language in its native form", "deutsch, français, svenska"),
            TemplateInfo("{foreign_english}", "Language name in English", "german, french, swedish"),
            
            // Foreign language names - uppercase
            TemplateInfo("{Foreign_native}", "Language in its native form (capitalized)", "Deutsch, Français, Svenska"),
            TemplateInfo("{Foreign_english}", "Language name in English (capitalized)", "German, French, Swedish"),
            
            // Date templates
            TemplateInfo("{day}", "Current day (01-31)", "01, 15, 31"),
            TemplateInfo("{month}", "Current month (01-12)", "01, 06, 12"),
            TemplateInfo("{year}", "Current year", "2025"),
            TemplateInfo("{week}", "Week of year", "1, 26, 52")
        )
    }
}

data class TemplateInfo(
    val template: String,
    val description: String,
    val example: String
)
