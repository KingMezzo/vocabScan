package de.vocabscan.learn

import android.content.Context

object LearningGoal {
    private const val KEY = "daily_goal"
    private const val DEFAULT = 10

    fun read(context: Context): Int = context.getSharedPreferences("learning", Context.MODE_PRIVATE)
        .getInt(KEY, DEFAULT).coerceIn(1, 100)

    fun write(context: Context, value: Int) {
        require(value in 1..100)
        context.getSharedPreferences("learning", Context.MODE_PRIVATE).edit().putInt(KEY, value).apply()
    }
}
