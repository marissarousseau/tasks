/*
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */

package com.todoroo.astrid.timers;

import static org.tasks.time.DateTimeUtils.currentTimeMillis;

import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import androidx.core.app.NotificationCompat;
import com.todoroo.andlib.sql.Criterion;
import com.todoroo.andlib.sql.QueryTemplate;
import com.todoroo.andlib.utility.DateUtilities;
import com.todoroo.astrid.api.Filter;
import com.todoroo.astrid.dao.TaskDao;
import com.todoroo.astrid.data.Task;
import com.todoroo.astrid.utility.Constants;
import io.reactivex.Completable;
import io.reactivex.schedulers.Schedulers;
import javax.inject.Inject;
import org.tasks.R;
import org.tasks.intents.TaskIntents;
import org.tasks.notifications.NotificationManager;

public class TimerPlugin {

  private final Context context;
  private final NotificationManager notificationManager;
  private final TaskDao taskDao;

  @Inject
  public TimerPlugin(
      Application context,
      NotificationManager notificationManager,
      TaskDao taskDao) {
    this.context = context;
    this.notificationManager = notificationManager;
    this.taskDao = taskDao;
  }

  public void startTimer(Task task) {
    updateTimer(task, true);
  }

  public void stopTimer(Task task) {
    updateTimer(task, false);
  }

  /**
   * toggles timer and updates elapsed time.
   *
   * @param start if true, start timer. else, stop it
   */
  private void updateTimer(Task task, boolean start) {
    if (task == null) {
      return;
    }

    if (start) {
      if (task.getTimerStart() == 0) {
        task.setTimerStart(DateUtilities.now());
      }
    } else {
      if (task.getTimerStart() > 0) {
        int newElapsed = (int) ((DateUtilities.now() - task.getTimerStart()) / 1000L);
        task.setTimerStart(0L);
        task.setElapsedSeconds(task.getElapsedSeconds() + newElapsed);
      }
    }

    Completable.fromAction(
            () -> {
              taskDao.save(task);
              updateNotifications();
            })
        .subscribeOn(Schedulers.io())
        .subscribe();
  }

  public void updateNotifications() {
    int count = taskDao.activeTimers();
    if (count == 0) {
      notificationManager.cancel(Constants.NOTIFICATION_TIMER);
    } else {
      Filter filter = createFilter(context);
      Intent notifyIntent = TaskIntents.getTaskListIntent(context, filter);
      notifyIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      PendingIntent pendingIntent =
          PendingIntent.getActivity(context, Constants.NOTIFICATION_TIMER, notifyIntent, 0);

      Resources r = context.getResources();
      String appName = r.getString(R.string.app_name);
      String text =
          r.getString(
              R.string.TPl_notification, r.getQuantityString(R.plurals.Ntasks, count, count));
      NotificationCompat.Builder builder =
          new NotificationCompat.Builder(context, NotificationManager.NOTIFICATION_CHANNEL_TIMERS)
              .setContentIntent(pendingIntent)
              .setContentTitle(appName)
              .setContentText(text)
              .setWhen(currentTimeMillis())
              .setSmallIcon(R.drawable.ic_timer_white_24dp)
              .setAutoCancel(false)
              .setOngoing(true);
      notificationManager.notify(Constants.NOTIFICATION_TIMER, builder, false, false, false);
    }
  }

  public static Filter createFilter(Context context) {
    Filter filter =
        new Filter(
            context.getString(R.string.TFE_workingOn),
            new QueryTemplate()
                .where(Criterion.and(Task.TIMER_START.gt(0), Task.DELETION_DATE.eq(0))));
    filter.icon = R.drawable.ic_outline_timer_24px;
    return filter;
  }
}
