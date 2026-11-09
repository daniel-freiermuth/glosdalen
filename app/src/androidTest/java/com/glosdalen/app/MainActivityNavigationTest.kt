package com.glosdalen.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The back stack must survive Activity recreation (rotation, dark mode, locale, ...),
 * otherwise every configuration change sends the user back to the DeepL screen.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // The screen title; the drawer item with the same label is clickable.
    private fun screenTitle(text: String) = hasText(text) and !hasClickAction()

    @Test
    fun drawerDestinationSurvivesRecreation() {
        composeRule.onNodeWithContentDescription("Open menu").performClick()
        composeRule.onNode(hasText("General Knowledge") and hasClickAction()).performClick()
        composeRule.onNode(screenTitle("General Knowledge")).assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNode(screenTitle("General Knowledge")).assertIsDisplayed()
    }

    @Test
    fun settingsPushedOnTopSurvivesRecreation() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNode(screenTitle("Settings")).assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNode(screenTitle("Settings")).assertIsDisplayed()
    }
}
