/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.src.app_package

import com.android.tools.idea.wizard.template.renderIf

fun appWidgetJava(applicationPackage: String?, className: String, configurable: Boolean, layoutName: String, packageName: String): String {
  val widgetTextBlock =
    if (configurable) "CharSequence widgetText = ${className}ConfigureActivity.loadTitlePref(context, appWidgetId);"
    else "CharSequence widgetText = context.getString(R.string.appwidget_text);"

  return """
package ${packageName};

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;
${renderIf(applicationPackage != null) { "import ${applicationPackage}.R;" }}

/**
 * Implementation of App Widget functionality.
${renderIf(configurable) {
    """
 * App Widget Configuration implemented in {@link ${className}ConfigureActivity ${className}ConfigureActivity}
"""
  }}
 */
public class ${className} extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }
${renderIf(configurable) {
    """
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // When the user deletes the widget, delete the preference associated with it.
        for (int appWidgetId : appWidgetIds) {
            ${className}ConfigureActivity.deleteTitlePref(context, appWidgetId);
        }
    }
"""
  }}

    @Override
    public void onEnabled(Context context) {
        // Enter relevant functionality for when the first widget is created
    }

    @Override
    public void onDisabled(Context context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
            int appWidgetId) {

        $widgetTextBlock
        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.${layoutName});
        views.setTextViewText(R.id.appwidget_text, widgetText);

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}

"""
}
