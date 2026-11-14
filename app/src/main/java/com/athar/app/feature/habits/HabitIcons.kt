package com.athar.app.feature.habits

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SmokeFree
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Book
import androidx.compose.ui.graphics.vector.ImageVector

fun HabitIcon.vector(): ImageVector = when (this) {
    HabitIcon.Quran -> Icons.AutoMirrored.Rounded.MenuBook
    HabitIcon.Prayer -> Icons.Rounded.Mosque
    HabitIcon.Dhikr -> Icons.Rounded.AutoAwesome
    HabitIcon.Walk -> Icons.AutoMirrored.Rounded.DirectionsWalk
    HabitIcon.Sport -> Icons.Rounded.FitnessCenter
    HabitIcon.Water -> Icons.Rounded.WaterDrop
    HabitIcon.Sleep -> Icons.Rounded.Bedtime
    HabitIcon.Read -> Icons.Rounded.Book
    HabitIcon.Study -> Icons.Rounded.School
    HabitIcon.NoSmoking -> Icons.Rounded.SmokeFree
    HabitIcon.Charity -> Icons.Rounded.Favorite
    HabitIcon.Star -> Icons.Rounded.Star
}
