# AppSearch

Launch ant Android app by typing a few letters of the name.

This is an Android app meant to launch other apps by typing their names, rather than browsing your app drawer, home screen(s), folders, etc. It is meant to find apps as quickly and conveniently as possible, and usually it just takes just a few keystrokes to open _any_ app that's installed on your phone. Usually you just need to tap the icon of _this_ app, type the one or two letters of the app you're looking for and tap "go" on your keyboard.

AppSearch will remember which apps you're most likely to open at the time of the day/day of the week, so it can put the matching apps that you're most likely to look for first, and even show you a list of the most likely apps to launch by default. For examply, if you use your Mail app often throughout the day, it will show you "Mail" as the first match when you type the letter "M". But at 7 'o clock this might change to "Maps" if you use that app always at that time for your daily commute (or select it as the default app even before you start typing).

The app is completed by a "Smart Icon" widget that looks like a normal home screen shortcut, but changes during the day to the most likely app for the moment to launch. You can use multiple widgets on your homescreen.

## Status

At the moment this project is in beta status. The launcher part of the app works fine, but the home screen widgets get stalled after some time and the learning algorithm needs some tweaking.

## FAQ

* **I don't get the point of this app: first I have to open it, and then it will open other apps?**
  
  This app is useful when you place its icon somewhere where you can easiy reach it, like your home screen. Then, when you want to open any app, you can tap this icon, type a few letters and press the "Go" button. It should be seen as an alternative to the app drawer, folders etc. that you normally browse to from your home screen.
  
* **Can I use this app as the default action for long-pressings the home button?**
  
  Yes. Open your phone settings, go to "Apps", click in the menu on "Default apps" and choose "Assistant app". There you can select this app.

* **Is this a launcher? Does this replace my normal home screen?**
  
  No, this app is designed to integrate with your normal home screen.

* **What are/how do I use these Smart Icon widgets?**
  
  The Smart Icon widgets try to mimic normal app shortcuts on your home screen, but will change during the day to represent the app you're most likely to open at the moment. You can place more than one of them, the first one will represent the most likely app, the second one the second most likely, and so on.
  
  Unfortunately, Smart Icon widgets don't look like normal app shortcuts at all when you first add them. You'll have to configure the look to match how normal app shortcuts look on your phone; there's no way to read this automatically. To configure the look:
  * place a normal app shortcut near the bottom of your screen
  * place a Smart Icon next to it
  * open the app, go to the app menu and choose "Customize smart icons"
  
  You'll now see a window floating on your home screen where you can drag the icon and text position, pinch on the icon and text to set their size, and adjust style parameters. The changes are directly reflected in the actual Smart Icon on your home screen, so you can compare the result with the normal app shortcut. 
  
* **Aren't there already solutions that do this?**
  
  Yes, the default Google search bar on your home screen does this, the app drawers in most phones allow you to do this, and there are several very nice home screen replacements like [KISS](https://kisslauncher.com/) that do this. But they are not ideal, in my opinion. I wanted two things:
  1. Focus on launching _apps_ as efficiently as possible
  2. Integrate well with a normal homescreen
  None of the existing solutions (that I know of) tick both boxes. See the Q/A's below for further information.
  
* **Isn't this the same as the Google search bar on your home screen?**
  
  The Google search bar is primarily a web search tool, plus it matches other content on your phone as well, like contacts. The result is that you have to do more typing to launch an app.

* **Isn't this the same as the search box in your app drawer?**
  
  Kind of, but your app drawer isn't primarily focussed on searching. You typically have to open the app drawer from the bottom of your screen first, then click on the search box at the top of the screen, then start typing at the keyboard on the bottom of the screen and then click the icon at the top of the screen again.
  
  This app is really focussed on launching-by-typing, with one hand if needed: you normally click the icon, start typing right away and can press the "Go" button on your keyboard.

* **Isn't this the same as KISS or similar launchers?**
  
  Kind of, but these launchers dedicate the home screen to searching, leaving no room for widgets (especially with an opened keyboard). This app is meant to integrate with your normal/favorite launcher; there's just a shortcut and/or some widgets that live next to the other content on the home screen.

* **Does this app collect personal data?**
  
  Nope.
  
  Okay, technically it _does_ collect data on your app usage because that how it works, but it's only stored on your phone can't send it anywhere. I wouldn't know what to do whit it anyway.

## License

This app is licensed according to the [Gnu General Public License version 3 (GPLv3)](https://www.gnu.org/licenses/gpl-3.0.en.html).
