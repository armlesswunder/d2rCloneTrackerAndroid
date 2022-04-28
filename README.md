# Diablo 2 Resurrected Clone Tracker for Android

# Credits

Teebling: For making the endpoint the app uses to pull dclone progress data and for the amazing site: https://diablo2.io/

# Contact
abw4v.dev@gmail.com

# Future plans

1) Be able to choose Hardcore and Ladder
2) Improve UI
3) Bug fixes and performance improvements

# FAQ

## What is this app?

It allows you to get notifications whenever the diablo clone event in any region makes progress. 

Example 1: America is at 1/6 of dclone event progress, and it increases to 2/6; you will get an audible notification.
Example 2: America is at 1/6 of dclone event progress, and it stays at 1/6; you will get silent notification. (Just to let you see the timestamps (confirm that the app is still running as expected in the background))

## How can I get notifications?

Start the app and press the start button.

## My notifications stop or I get an error after a while

This is a complicated issue which can be solved with a few simple steps:

1) Disable doze mode for the app.

2) Leave the app open in the background (don't close the app)

3) Connect your phone to a charger (This step is probably not necessary unless you have a bad phone / battery)

4) Ensure Network connection (My app can't fix your poor network conditions)

## Why all these steps?

1) Android OS has a concept of "battery optimization" at the expense of the app. What this means is that Android has no respect for my app's desire to function after a period of time deemed as "acceptable to kill part of or an entire app running in the background." Most of the time this feature is great as it saves your battery and keeps obnoxious apps from using up your precious computational resources, but in the case of this app, it sucks. I'll be investigating better avenues of keeping the app alive in the future.

## Okay, I followed your steps and the app is still broke. HELP!!

1) Close the app and restart it. Follow the steps listed above again and it should work fine. I've tested it running for 16 hours straight without issue using the above steps.

2) Diablo2.io backend may be down. This is where I get my data, so just be patient as it comes back up.

## The app is wrong, it says x/6 status when really the status is y/6

Thats because this app relies on human input to keep track of statuses. If someone enters the wrong status, you will get the wrong status. Be mad at people, not my poor, diligent, and loyal software.

## How do I track progress for Hardcore and/or Ladder

Be patient. I'm working on it.

## Why isn't this app on Play Store?

Because its in beta. I'll release it on playstore once its feature complete.
