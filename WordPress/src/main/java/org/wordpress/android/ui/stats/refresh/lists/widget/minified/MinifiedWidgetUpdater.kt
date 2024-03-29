package org.wordpress.android.ui.stats.refresh.lists.widget.minified

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.wordpress.android.R
import org.wordpress.android.analytics.AnalyticsTracker
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.store.SiteStore
import org.wordpress.android.fluxc.store.stats.insights.TodayInsightsStore
import org.wordpress.android.ui.prefs.AppPrefsWrapper
import org.wordpress.android.ui.stats.StatsTimeframe.INSIGHTS
import org.wordpress.android.ui.stats.refresh.lists.widget.WidgetUpdater
import org.wordpress.android.ui.stats.refresh.lists.widget.configuration.StatsColorSelectionViewModel.Color.DARK
import org.wordpress.android.ui.stats.refresh.lists.widget.configuration.StatsColorSelectionViewModel.Color.LIGHT
import org.wordpress.android.ui.stats.refresh.lists.widget.configuration.StatsDataTypeSelectionViewModel.DataType
import org.wordpress.android.ui.stats.refresh.lists.widget.configuration.StatsDataTypeSelectionViewModel.DataType.COMMENTS
import org.wordpress.android.ui.stats.refresh.lists.widget.configuration.StatsDataTypeSelectionViewModel.DataType.LIKES
import org.wordpress.android.ui.stats.refresh.lists.widget.configuration.StatsDataTypeSelectionViewModel.DataType.VIEWS
import org.wordpress.android.ui.stats.refresh.lists.widget.configuration.StatsDataTypeSelectionViewModel.DataType.VISITORS
import org.wordpress.android.ui.stats.refresh.lists.widget.utils.WidgetUtils
import org.wordpress.android.ui.stats.refresh.utils.MILLION
import org.wordpress.android.ui.stats.refresh.utils.ONE_THOUSAND
import org.wordpress.android.ui.stats.refresh.utils.toFormattedString
import org.wordpress.android.ui.stats.refresh.utils.trackMinifiedWidget
import org.wordpress.android.util.NetworkUtilsWrapper
import org.wordpress.android.util.analytics.AnalyticsTrackerWrapper
import org.wordpress.android.viewmodel.ResourceProvider
import javax.inject.Inject

class MinifiedWidgetUpdater
@Inject constructor(
    private val appPrefsWrapper: AppPrefsWrapper,
    private val siteStore: SiteStore,
    private val networkUtilsWrapper: NetworkUtilsWrapper,
    private val resourceProvider: ResourceProvider,
    private val todayInsightsStore: TodayInsightsStore,
    private val widgetUtils: WidgetUtils,
    private val analyticsTrackerWrapper: AnalyticsTrackerWrapper
) : WidgetUpdater {
    override fun updateAppWidget(
        context: Context,
        appWidgetId: Int,
        appWidgetManager: AppWidgetManager?
    ) {
        val widgetManager = appWidgetManager ?: AppWidgetManager.getInstance(context)
        val isWideView = widgetUtils.isWidgetWiderThanLimit(widgetManager, appWidgetId)
        val colorMode = appPrefsWrapper.getAppWidgetColor(appWidgetId) ?: LIGHT
        val siteId = appPrefsWrapper.getAppWidgetSiteId(appWidgetId)
        val dataType = appPrefsWrapper.getAppWidgetDataType(appWidgetId)
        val siteModel = siteStore.getSiteBySiteId(siteId)
        val networkAvailable = networkUtilsWrapper.isNetworkAvailable()
        val layout = when (colorMode) {
            LIGHT -> R.layout.stats_widget_minified_light
            DARK -> R.layout.stats_widget_minified_dark
        }
        val views = RemoteViews(context.packageName, layout)
        widgetUtils.setSiteIcon(siteModel, context, views, appWidgetId)
        if (networkAvailable && siteModel != null && dataType != null) {
            views.setOnClickPendingIntent(R.id.widget_container,
                    widgetUtils.getPendingSelfIntent(context, siteModel.id, INSIGHTS)
            )
            showValue(widgetManager, appWidgetId, views, siteModel, dataType, isWideView)
        } else {
            widgetUtils.showError(widgetManager, views, appWidgetId, networkAvailable, resourceProvider, context)
        }
    }

    override fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val viewsWidget = ComponentName(context, StatsMinifiedWidget::class.java)
        val allWidgetIds = appWidgetManager.getAppWidgetIds(viewsWidget)
        for (appWidgetId in allWidgetIds) {
            updateAppWidget(context, appWidgetId, appWidgetManager)
        }
    }

    private fun showValue(
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        views: RemoteViews,
        site: SiteModel,
        dataType: DataType,
        isWideView: Boolean
    ) {
        loadValue(appWidgetManager, appWidgetId, site, views, dataType, isWideView)
        GlobalScope.launch {
            runBlocking {
                todayInsightsStore.fetchTodayInsights(site)
            }
            loadValue(appWidgetManager, appWidgetId, site, views, dataType, isWideView)
        }
    }

    private fun loadValue(
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        site: SiteModel,
        views: RemoteViews,
        dataType: DataType,
        isWideView: Boolean
    ) {
        val todayInsights = todayInsightsStore.getTodayInsights(site)
        val (key, value) = when (dataType) {
            VIEWS -> R.string.stats_views to todayInsights?.views
            VISITORS -> R.string.stats_visitors to todayInsights?.visitors
            COMMENTS -> R.string.stats_comments to todayInsights?.comments
            LIKES -> R.string.stats_likes to todayInsights?.likes
        }
        views.setTextViewText(R.id.name, resourceProvider.getString(key))
        val startValue = if (isWideView) MILLION else ONE_THOUSAND
        views.setTextViewText(R.id.value, value?.toFormattedString(startValue) ?: "-")
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun delete(appWidgetId: Int) {
        analyticsTrackerWrapper.trackMinifiedWidget(AnalyticsTracker.Stat.STATS_WIDGET_REMOVED)
        appPrefsWrapper.removeAppWidgetColorModeId(appWidgetId)
        appPrefsWrapper.removeAppWidgetSiteId(appWidgetId)
    }
}
