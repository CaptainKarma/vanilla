Vanilla ICE - Forked from Vanilla Music
=======================================
Original Source;
https://github.com/vanilla-music/vanilla/

Experimental features and changes to improve operability on an in car Android Headunit

Activities and Roadmap
======================
1) Gradle updates to latest (DONE)
2) Fix for depreciated getExternalStorageDirectory for dumping database (DONE)
3) Vanilla throws an error that file not available, add a wait cycle to enable external SDCard to be mounted (In Testing)
4) Make the database portable between devices to save in car long scan times (In Testing)
   Database is now stored at;/storage/emulated/0/Documents/vanilla-media-library.db
   This enables scanning on another phone/device 
   Remember song paths need to be updated, so use the following sqlite command to update the database e.g.
   UPDATE songs SET path = replace( path, '/storage/9C33-6BBD/Music/', '/storage/USB1/media/');
   Then copy the database to another phone/device and start app
   Note; if the app is uninstalled the database may remain and need manual deletion
5) Changed Text Sizing to increase visibility on larger screen (DONE)
6) Changed Button Sizing to improve usability on car Android Headunit (DONE)
