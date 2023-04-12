Vanilla ICE (Internal Combustion Engine/Edition)
<h4>Forked from Vanilla Music</h4>
=======================================
Original Source;
https://github.com/vanilla-music/vanilla/

Experimental features and changes to improve operability on an in car Android Headunit

Implemented Changes
===================
 1) Gradle updates to latest 
 2) Fix for depreciated getExternalStorageDirectory for dumping database
 3) Vanilla throws an error that file not available, add a wait cycle to enable external SDCard to be mounted
 4) Make the database portable between devices to save in car long scan times
    Database is now stored at;/storage/emulated/0/Documents/vanilla-media-library.db
    This enables scanning on another phone/device and then copying the database to the car
    Remember song paths need to be updated, so use the following sqlite command to update the database e.g.
    UPDATE songs SET path = replace( path, '/storage/9C33-6BBD/Music/', '/storage/USB1/media/');
    Then copy the database to another phone/device and start app
    Note; if the app is uninstalled the database may remain and need manual deletion
 5) Changed Text Sizing to increase visibility on larger screen
 6) Changed Button Sizing to improve usability on car Android Headunit
 7) Fixes for deprecated setAudioStreamType() error message
 8) Fixes for deprecated use of stream types error message
 9) Added additional SD Card wait for the initial file load on startup
10) More sizing changes to assist with touching buttons in a moving vehicle
11) Third Party Framework to enable reaching out to other services to determine which track to play next

Future Roadmap
==============
Fix stuff that doesn't work or I broke :p