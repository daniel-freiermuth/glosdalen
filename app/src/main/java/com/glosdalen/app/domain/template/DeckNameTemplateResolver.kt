package com.glosdalen.app.domain.template

import com.glosdalen.app.backend.deepl.SearchContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeckNameTemplateResolver @Inject constructor() {
    
    fun resolveDeckName(
        templateString: String,
        searchContext: SearchContext
    ): String {
        var resolved = templateString
        
        // Foreign language name templates - lowercase
        resolved = resolved.replace("{foreign_native}", searchContext.foreignLanguage.nativeName.lowercase())
        resolved = resolved.replace("{foreign_english}", searchContext.foreignLanguage.displayName.lowercase())
        resolved = resolved.replace("{foreign_local}", 
            searchContext.foreignLanguage.getNameInLanguage(searchContext.nativeLanguage).lowercase())
        
        // Foreign language name templates - uppercase (capitalized)
        resolved = resolved.replace("{Foreign_native}", searchContext.foreignLanguage.nativeName)
        resolved = resolved.replace("{Foreign_english}", searchContext.foreignLanguage.displayName)
        resolved = resolved.replace("{Foreign_local}", 
            searchContext.foreignLanguage.getNameInLanguage(searchContext.nativeLanguage))
        
        // Language code templates
        resolved = resolved.replace("{foreign_code_native}", searchContext.foreignLanguage.code)
        resolved = resolved.replace("{foreign_code_english}", searchContext.foreignLanguage.code)
        
        // Date templates
        val now = LocalDate.now()
        resolved = resolved.replace("{day}", String.format("%02d", now.dayOfMonth))
        resolved = resolved.replace("{month}", String.format("%02d", now.monthValue))
        resolved = resolved.replace("{year}", now.year.toString())
        resolved = resolved.replace("{week}", now.get(WeekFields.of(Locale.getDefault()).weekOfYear()).toString())
        
        return resolved.trim()
    }
    
    fun getAvailableTemplates(): List<TemplateInfo> {
        return listOf(
            // Foreign language names - lowercase
            TemplateInfo("{foreign_native}", "Language in its native form", "deutsch, français, svenska"),
            TemplateInfo("{foreign_english}", "Language name in English", "german, french, swedish"),
            TemplateInfo("{foreign_local}", "Language name in your native language", "tyska, allemand, schwedisch"),
            
            // Foreign language names - uppercase
            TemplateInfo("{Foreign_native}", "Language in its native form (capitalized)", "Deutsch, Français, Svenska"),
            TemplateInfo("{Foreign_english}", "Language name in English (capitalized)", "German, French, Swedish"),
            TemplateInfo("{Foreign_local}", "Language name in your native language (capitalized)", "Tyska, Allemand, Schwedisch"),
            
            // Language codes
            TemplateInfo("{foreign_code_native}", "Foreign language code", "DE, FR, SV"),
            TemplateInfo("{foreign_code_english}", "Foreign language code", "DE, FR, SV"),
            
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
