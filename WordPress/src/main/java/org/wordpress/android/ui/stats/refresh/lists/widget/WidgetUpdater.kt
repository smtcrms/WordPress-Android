package org.wordpress.android.ui.stats.refresh.lists.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import org.wordpress.android.ui.stats.refresh.lists.widget.alltime.AllTimeWidgetUpdater
import org.wordpress.android.ui.stats.refresh.lists.widget.views.ViewsWidgetUpdater
import javax.inject.Inject

interface WidgetUpdater {
    fun updateAppWidget(
        context: Context,
        appWidgetId: Int,
        appWidgetManager: AppWidgetManager? = null
    )

    fun updateAllWidgets(context: Context)
    fun delete(appWidgetId: Int)

    class StatsWidgetUpdaters
    @Inject constructor(viewsWidgetUpdater: ViewsWidgetUpdater, allTimeWidgetUpdater: AllTimeWidgetUpdater) {
        private val widgetUpdaters = listOf(viewsWidgetUpdater, allTimeWidgetUpdater)
        fun update(context: Context) {
            widgetUpdaters.forEach {
                it.updateAllWidgets(context)
            }
        }
    }
}
