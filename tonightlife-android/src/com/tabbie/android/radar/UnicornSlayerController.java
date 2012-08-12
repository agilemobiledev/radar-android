package com.tabbie.android.radar;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.util.Log;

/**
 *  UnicornSlayerController.java
 * 
 *  Created on: Aug 5, 2012
 *      Author: Justin Knutson
 * 
 *  What we do to Tabbie virgins
 */

public class UnicornSlayerController {
  private final AlertDialog.Builder builder;

  public UnicornSlayerController(AlertDialog.Builder builder) {
    this.builder = builder;
  }

  public interface TabsCallback {
    public void openFeaturedTab();

    public void openEventsTab();

    public void openRadarTab();
  }

  public void showTabsTutorial(final TabsCallback tabs, final SharedPreferences.Editor editor) {
    builder.setMessage("Is this your first time using TonightLife?")
        .setCancelable(false)
        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {

          @Override
          public void onClick(DialogInterface dialog, int which) {
            builder
                .setMessage(
                    "Awesome! TonightLife lets you discover all the best events going on in your area tonight. "
                        + "It's quick and easy to use; how about a quick tour?")
                .setCancelable(false)
                .setPositiveButton("Sounds good",
                    new DialogInterface.OnClickListener() {

                      @Override
                      public void onClick(DialogInterface dialog, int which) {
                        tabs.openRadarTab();
                        builder
                            .setMessage(
                                "This is your radar - events will show up here in chronological order when you add them. We'll do that in a second - for now, think of this as your own personal planner")
                            .setCancelable(false)
                            .setPositiveButton("Got it",
                                new DialogInterface.OnClickListener() {

                                  @Override
                                  public void onClick(DialogInterface dialog,
                                      int which) {
                                    tabs.openEventsTab();

                                    // WE MUST GO DEEPER

                                    builder
                                        .setMessage(
                                            "This is a list of everything going on after 5:00 PM today. We update it daily with new content.")
                                        .setCancelable(false)
                                        .setPositiveButton(
                                            "Sweet!",
                                            new DialogInterface.OnClickListener() {

                                              @Override
                                              public void onClick(
                                                  DialogInterface dialog,
                                                  int which) {
                                                tabs.openFeaturedTab();

                                                /*
                                                 * EVEN DEEPER
                                                 */
                                                builder
                                                    .setMessage(
                                                        "Featured events are curated by the TonightLife team every day for your enjoyment. Go ahead and select one now...")
                                                    .setCancelable(true)
                                                    .setPositiveButton(
                                                        "Alright", null)
                                                    .create().show();
                                              }
                                            }).create().show();
                                  }
                                }).create().show();
                      }
                    })
                .setNegativeButton("No, thanks",
                    new DialogInterface.OnClickListener() {

                      @Override
                      public void onClick(DialogInterface dialog, int which) {
                        // TODO Auto-generated method stub
                      }
                    }).create().show();
          }
        }).setNegativeButton("No", new DialogInterface.OnClickListener() {

          @Override
          public void onClick(DialogInterface dialog, int which) {
            editor.putBoolean("virgin", false);
            editor.commit();
          }
        }).create().show();
    Log.d(this.getClass().getName(), "IM RETURNING");
  }

  public void showDetailsTutorial() {
    builder
        .setMessage(
            "The event details page allows you to see more information about a specific event. You can also add it to your radar by clicking the radar button on the right. That's all for now!")
        .setCancelable(true).setPositiveButton("Go forth", null).create()
        .show();
  }
}
