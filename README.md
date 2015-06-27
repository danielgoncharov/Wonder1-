# Wonder1-

Codebase for droidcon hackathon (http://droidcon.de/hackathon) project of Zerg Hatchery team.

We developed "light manager" which allows to save energy power. If it is bright outside we switch lamp off, otherwise switch it on (not discrete but continual). We played with https://www.relayr.io/products-services/wunderbar/. Consumed light sensor data and managed Philips Hue lamps (https://www.youtube.com/watch?v=IT5W_Mjuz5I). Also we added noise trigger – if it is higher than threshold, show another random color for hue lamps. Relayr stuff is cool, it has nice dashboard and Android SDK based on RxJava.

The code is extremely dirty, but you know, it was hackathon ;)
