## Unreleased

## 2.10.0 (October 12, 2016)

* Catch and handle `CursorWindowAllocationException` thrown when the SDK is querying from the SQLite DB when app memory is low. If the exception is caught during `initialize`, then it is treated as if `initialize` was never called. If the exception is caught during the uploading of unsent events, then the upload is deferred to a later time.
* Block event property and user property dictionaries that have more than 1000 items. This is to block properties that are set unintentionally (for example in a loop). A single call to `logEvent` should not have more than 1000 event properties. Similarly a single call to `setUserProperties` should not have more than 1000 user properties.
* Handle IllegalArgumentException thrown by Android Geocoder for bad lat / lon values.

## 2.9.2 (July 14, 2016)

* Fix bug where `enableLocationListening` and `disableLocationListening` were not being run on background thread. Thanks to @elevenfive for PR.
* Update `Revenue` class to expose public `equals` and `hashCode` methods.

## 2.9.1 (July 11, 2016)

* Fix bug where `setOptOut` was not being run on background thread.
* `productId` is no longer a required field for `Revenue` logged via `logRevenueV2`.
* Fix bug where receipt and receiptSignature were being truncated if they were too long (exceeded 1024 characters).

## 2.9.0 (July 07, 2016)

* Add automatic flushing of unsent events on app close/minimize (through the Activity Lifecycle `onPause` callback). This only works if you call `Amplitude.getInstance().enableForegroundTracking(getApplication());`, which is recommended in the README by default for Setup. To disable you can call `Amplitude.getInstance().setFlushEventsOnClose(false);`

## 2.8.0 (June 29, 2016)

* Run the `initialize` logic on the background thread so that the SQLite database operations do not delay the main thread.
* Add support for Amazon Advertising ID (use in place of Google Advertising ID on Amazon devices). Thanks to @jcomo for the pull request.

## 2.7.2 (May 24, 2016)

* Add documentation for SDK functions. You can take a look [here](https://rawgit.com/amplitude/Amplitude-Android/master/javadoc/index.html). A link has also been added to the Readme.
* Fix bug where fetching the user's location on select devices throws a SecurityException, causing a crash.

## 2.7.1 (April 19, 2016)

* RevenueProperties is a confusing name and should actually be eventProperties. Deprecating Revenue.setRevenueProperties and replacing it with Revenue.setEventProperties, and clarified in Readme.

## 2.7.0 (April 19, 2016)

* Add support setting groups for users and events. See [Readme](https://github.com/amplitude/Amplitude-Android#setting-groups) for more information.
* Add helper method `getSessionId` to expose the current sessionId value.
* Add `logRevenueV2` and new `Revenue` class to support logging revenue events with properties, revenue type, and verified. See [Readme](https://github.com/amplitude/Amplitude-Android#tracking-revenue) for more info.
* Fix crash when trying to enableForegroundTracking with the PinnedAmplitudeClient. AmplitudeClient methods should be using `this` instead of static `instance` variable.

## 2.6.0 (March 29, 2016)

* Update to OKHttp v3.0.1.
* Add support for prepend user property operation.
* Fix bug where merging events for upload causes array index out of bounds exception.
* Migrate shared preferences (userId and event meta data) to Sqlite db to support apps with multiple processes.

## 2.5.1 (March 14, 2016)

* Fix bug where updateServer sets the wrong batchLimit when limit is false.

## 2.5.0 (January 15, 2016)

* Add ability to clear all user properties.
* Check that SDK is initialized when user calls enableForegroundTracking, identify, setUserProperties.

## 2.4.0 (December 15, 2015)

* Add support for append user property operation.

## 2.3.0 (November 30, 2015)

* Log if Google Play Services is enabled for the application.

## 2.2.0 (October 20, 2015)

* Removed all references to Apache HTTPClient to support Android M.
* Handle exceptions when fetching last known location from LocationManager.
* Add ability to set custom deviceId.
* Handle exception when cloning JSON object.
* Maintain only one instance of OKHttpClient.
* Add AmplitudeLog helper class that supports enabling and disabling of logging as well as setting of the log level.
* Fix bug where event and identify queues are not truncated if eventMaxCount is less than eventRemoveBatchSize.

## 2.1.0 (October 04, 2015)

* Add support for user properties operations (set, setOnce, add, unset).
* Fix bug where end session event was not being sent upon app reopen.

## 2.0.4 (September 23, 2015)

* Fix bug where deviceInfo was trying to use Geocoder if none present.

## 2.0.3 (September 22, 2015)

* Fix bug where deviceId was being fetched on main thread.

## 2.0.2 (August 24, 2015)

* Fix Maven jar, fixed build file.

## 2.0.1 (August 21, 2015)

* Catch all exceptions thrown by Android TelephonyManager and NullPointerExceptions thrown by geocoder during country lookup.

## 2.0.0 (August 20, 2015)

* Expose user ID with getUserId.
* Simplified session tracking. No longer need to call startSession and endSession. No longer send start/end session events by default. Added foreground tracking for sessions that uses Android activity lifecycles.
* The minimum supported API level is 9. API level 14 is required for foreground tracking.
* Always track Android advertising ID (ADID) regardless of limit ad tracking enabled.
* Track if limit ad tracking enabled as an API property for each logged event.
* Database upgraded to version 2: added a new store table for key value pairs.
* Device ID is now saved to and reloaded from the SQLite database (instead of SharedPrefs because SharedPrefs currently does not support multiple processes).
* MessageDigest.getInstance(String) is not threadsafe (known Android issue). Replaced with alternate MD5 implementation from http://org.rodage.com/pub/java/security/MD5.java.
* Create a copy of input userProperties JSONObject in setUserProperties to try and prevent ConcurrentModificationException.

## 1.7.0 (May 29, 2015)

* Enable configuration of eventUploadThreshold, eventMaxCount,
  eventUploadMaxBatchSize, eventUploadPeriodSeconds, minTimeBetweenSessionsMillis,
  and sessionTimeoutMillis.

## 1.6.3 (May 06, 2015)

* Add offline mode to turn off server uploading for a time.
* Add synchronous logging. Logs events to the DB synchronously to guarantee event persistence.

## 1.6.2 (April 17, 2015)

* Change protection on AmplitudeClient to public.

## 1.6.1 (April 13, 2015)

* Fix double class inclusion in jar distribution

## 1.6.0 (April 08, 2015)

* Fix crash under aggressive proguard optimizations.
* Fix device id being lost occasionally on app update.
* Fix exception when calling logEvent with empty JSONObject.
* Log a DEBUG message on each event.

## 1.5.0 (March 24, 2015)

* Add PinnedAmplitudeClient to support SSL pinning.
* Deprecate static methods on Amplitude. Switch to using Amplitude.getInstance().
* Upgrade HTTP client to okhttp.

## 1.4.6 (March 16, 2015)

* Fix bug when initializing with user id. Api key was not set properly.

## 1.4.4 (March 11, 2015)

* Expose setUserProperties(JSONObject, boolean) as a static
* Handle null edge cases in location request
* Add user opt out support
* Merge user properties in setUserProperties by default
* Refactor Amplitude to be a singleton to support tests
* Add option to disable fine-grained location tracking
* Fix crash: ConcurrentModificationException in HashMap
* Fix crash: CursorWindowAllocationException in SQLite

## 1.4.3 (November 13, 2014)

* Update field names, split platform and os, and send library information

## 1.4.2 (November 7, 2014)

* Don't log end session event if session isn't open
* Fix creating a new session id when the previous session id is invalid or non existant

## 1.4.1 (July 16, 2014)

* Hotfix extra class file in jar.

## 1.4.0 (July 1, 2014)

* Send androidADID with events
* Use Google Play Advertising ID instead of Android ID, if set. Default / fall back on using a random UUID
* Pull country from reverse geocode, then telephony network country, then locale

## 1.3.0 (June 4, 2014)

* Add getDeviceId to unity plugin
* Add additional logRevenue methods for receipt validation
* Make device ID public
* Fix bug where first event was getting skipped from upload
* Catch SQLiteExceptions
* Catch exceptions through by Apache HTTPClient

## 1.0.0 (May 1, 2014)

* Initial packaged release
