package com.amplitude.api;

import com.amplitude.security.MD5;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

public class AmplitudeClient {

    public static final String TAG = "com.amplitude.api.AmplitudeClient";

    public static final String START_SESSION_EVENT = "session_start";
    public static final String END_SESSION_EVENT = "session_end";
    public static final String REVENUE_EVENT = "revenue_amount";
    public static final String DEVICE_ID_KEY = "device_id";

    protected static AmplitudeClient instance = new AmplitudeClient();

    public static AmplitudeClient getInstance() {
        return instance;
    }

    private static final Amplitude.Listener ANDROID_LOG = new Amplitude.Listener() {
        public void onError(AmplitudeException error) {
            Log.e(TAG, "Amplitude error", error);
        }
    };

    private static final Amplitude.UploadCallback EMPTY = new Amplitude.UploadCallback() {
        @Override public void onComplete() {

        }

        @Override public void onError(AmplitudeException error) {

        }
    };

    protected Context context;
    protected Amplitude.Listener listener = ANDROID_LOG;
    protected OkHttpClient httpClient;
    protected String apiKey;
    protected String userId;
    protected String deviceId;
    private boolean newDeviceIdPerInstall = false;
    private boolean useAdvertisingIdForDeviceId = false;
    private boolean initialized = false;
    private boolean optOut = false;
    private boolean offline = false;

    private DeviceInfo deviceInfo;

    /* VisibleForTesting */
    JSONObject userProperties;

    private long sessionId = -1;
    private int eventUploadThreshold = Constants.EVENT_UPLOAD_THRESHOLD;
    private int eventUploadMaxBatchSize = Constants.EVENT_UPLOAD_MAX_BATCH_SIZE;
    private int eventMaxCount = Constants.EVENT_MAX_COUNT;
    private long eventUploadPeriodMillis = Constants.EVENT_UPLOAD_PERIOD_MILLIS;
    private long minTimeBetweenSessionsMillis = Constants.MIN_TIME_BETWEEN_SESSIONS_MILLIS;
    private long sessionTimeoutMillis = Constants.SESSION_TIMEOUT_MILLIS;
    private boolean backoffUpload = false;
    private int backoffUploadBatchSize = eventUploadMaxBatchSize;
    private boolean usingForegroundTracking = false;
    private boolean trackingSessionEvents = false;
    private boolean inForeground = false;

    private AtomicBoolean updateScheduled = new AtomicBoolean(false);
    private AtomicBoolean uploadingCurrently = new AtomicBoolean(false);

    // Let test classes have access to these properties.
    Throwable lastError;
    String url = Constants.EVENT_LOG_URL;
    WorkerThread logThread = new WorkerThread("logThread");
    WorkerThread httpThread = new WorkerThread("httpThread");

    public AmplitudeClient() {
        logThread.start();
        httpThread.start();
    }

    public AmplitudeClient initialize(Context context, String apiKey) {
        return initialize(context, apiKey, null, null);
    }

    public AmplitudeClient initialize(Context context, String apiKey, Amplitude.Listener listener) {
        return initialize(context, apiKey, null, null, listener);
    }

    public AmplitudeClient initialize(Context context, String apiKey, OkHttpClient okHttpClient, Amplitude.Listener listener) {
        return initialize(context, apiKey, null, okHttpClient, listener);
    }

    public synchronized AmplitudeClient initialize(Context context, String apiKey, String userId, OkHttpClient okHttpClient, Amplitude.Listener listener) {
        if (listener != null){
            this.listener = listener;
        }
        if (context == null) {
            listener.onError(new AmplitudeException("Argument context cannot be null in initialize()"));
            return instance;
        }

        AmplitudeClient.upgradePrefs(context, listener);
        AmplitudeClient.upgradeDeviceIdToDB(context);

        if (TextUtils.isEmpty(apiKey)) {
            Log.e(TAG, "Argument apiKey cannot be null or blank in initialize()");
            return instance;

        }
        if (!initialized) {
            this.context = context.getApplicationContext();
            this.httpClient = okHttpClient == null ? new OkHttpClient(): okHttpClient;
            this.apiKey = apiKey;
            initializeDeviceInfo();
            SharedPreferences preferences = context.getSharedPreferences(
                    getSharedPreferencesName(), Context.MODE_PRIVATE);
            if (userId != null) {
                this.userId = userId;
                preferences.edit().putString(Constants.PREFKEY_USER_ID, userId).commit();
            } else {
                this.userId = preferences.getString(Constants.PREFKEY_USER_ID, null);
            }
            this.optOut = preferences.getBoolean(Constants.PREFKEY_OPT_OUT, false);
            initialized = true;
        }

        return instance;
    }

    public AmplitudeClient enableForegroundTracking(Application app) {
        if (usingForegroundTracking) {
            return instance;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            app.registerActivityLifecycleCallbacks(new AmplitudeCallbacks(instance));
        }

        return instance;
    }

    private void initializeDeviceInfo() {
        deviceInfo = new DeviceInfo(context);
        runOnLogThread(new Runnable() {

            @Override
            public void run() {
                deviceId = initializeDeviceId();
                deviceInfo.prefetch();
            }
        });
    }

    public AmplitudeClient enableNewDeviceIdPerInstall(boolean newDeviceIdPerInstall) {
        this.newDeviceIdPerInstall = newDeviceIdPerInstall;
        return instance;
    }

    public AmplitudeClient useAdvertisingIdForDeviceId() {
        this.useAdvertisingIdForDeviceId = true;
        return instance;
    }

    public AmplitudeClient enableLocationListening() {
        if (deviceInfo == null) {
            throw new IllegalStateException(
                    "Must initialize before acting on location listening.");
        }
        deviceInfo.setLocationListening(true);
        return instance;
    }

    public AmplitudeClient disableLocationListening() {
        if (deviceInfo == null) {
            throw new IllegalStateException(
                    "Must initialize before acting on location listening.");
        }
        deviceInfo.setLocationListening(false);
        return instance;
    }

    public AmplitudeClient setEventUploadThreshold(int eventUploadThreshold) {
        this.eventUploadThreshold = eventUploadThreshold;
        return instance;
    }

    public AmplitudeClient setEventUploadMaxBatchSize(int eventUploadMaxBatchSize) {
        this.eventUploadMaxBatchSize = eventUploadMaxBatchSize;
        this.backoffUploadBatchSize = eventUploadMaxBatchSize;
        return instance;
    }

    public AmplitudeClient setEventMaxCount(int eventMaxCount) {
        this.eventMaxCount = eventMaxCount;
        return instance;
    }

    public AmplitudeClient setEventUploadPeriodMillis(int eventUploadPeriodMillis) {
        this.eventUploadPeriodMillis = eventUploadPeriodMillis;
        return instance;
    }

    public AmplitudeClient setMinTimeBetweenSessionsMillis(long minTimeBetweenSessionsMillis) {
        this.minTimeBetweenSessionsMillis = minTimeBetweenSessionsMillis;
        return instance;
    }

    public AmplitudeClient setSessionTimeoutMillis(long sessionTimeoutMillis) {
        this.sessionTimeoutMillis = sessionTimeoutMillis;
        return instance;
    }

    public AmplitudeClient setOptOut(boolean optOut) {
        this.optOut = optOut;

        SharedPreferences preferences = context.getSharedPreferences(
                getSharedPreferencesName(), Context.MODE_PRIVATE);
        preferences.edit().putBoolean(Constants.PREFKEY_OPT_OUT, optOut).commit();
        return instance;
    }

    public AmplitudeClient setOffline(boolean offline) {
        this.offline = offline;
        return instance;
    }

    public AmplitudeClient trackSessionEvents(boolean trackingSessionEvents) {
        this.trackingSessionEvents = trackingSessionEvents;
        return instance;
    }

    void useForegroundTracking() {
        usingForegroundTracking = true;

    }

    boolean isUsingForegroundTracking() { return usingForegroundTracking; }

    boolean isInForeground() { return inForeground; }

    public void logEvent(String eventType) {
        logEvent(eventType, null);
    }

    public void logEvent(String eventType, JSONObject eventProperties) {
        logEvent(eventType, eventProperties, false);
    }

    public void logEvent(String eventType, JSONObject eventProperties, boolean outOfSession) {
        if (validateLogEvent(eventType)) {
            logEventAsync(eventType, eventProperties, null, System.currentTimeMillis(), outOfSession);
        }
    }

    public void logEventSync(String eventType, JSONObject eventProperties) {
        if (validateLogEvent(eventType)) {
            logEvent(eventType, eventProperties, null, System.currentTimeMillis(), false);
        }
    }

    protected boolean validateLogEvent(String eventType) {
        if (TextUtils.isEmpty(eventType)) {
            listener.onError(
                new AmplitudeException("Argument eventType cannot be null or blank in logEvent()"));
            return false;
        }

        if (!contextAndApiKeySet("logEvent()")) {
            return false;
        }

        return true;
    }

    protected void logEventAsync(final String eventType, JSONObject eventProperties,
            final JSONObject apiProperties, final long timestamp, final boolean outOfSession) {
        // Clone the incoming eventProperties object before sending over
        // to the log thread. Helps avoid ConcurrentModificationException
        // if the caller starts mutating the object they passed in.
        // Only does a shallow copy, so it's still possible, though unlikely,
        // to hit concurrent access if the caller mutates deep in the object.
        if (eventProperties != null) {
            eventProperties = cloneJSONObject(eventProperties);
        }

        final JSONObject copyEventProperties = eventProperties;
        runOnLogThread(new Runnable() {
            @Override
            public void run() {
                logEvent(eventType, copyEventProperties, apiProperties, timestamp, outOfSession);
            }
        });
    }

    protected long logEvent(String eventType, JSONObject eventProperties,
            JSONObject apiProperties, long timestamp, boolean outOfSession) {
        Log.d(TAG, "Logged event to Amplitude: " + eventType);

        if (optOut) {
            return -1;
        }

        // skip session check if logging start_session or end_session events
        boolean loggingSessionEvent = trackingSessionEvents &&
                (eventType.equals(START_SESSION_EVENT) || eventType.equals(END_SESSION_EVENT));

        if (!loggingSessionEvent && !outOfSession) {
            // default case + corner case when async logEvent between onPause and onResume
            if (!inForeground){
                startNewSessionIfNeeded(timestamp);
            } else {
                refreshSessionTime(timestamp);
            }
        }

        JSONObject event = new JSONObject();
        try {
            event.put("event_type", replaceWithJSONNull(eventType));
            event.put("timestamp", timestamp);
            event.put("user_id", replaceWithJSONNull(userId));
            event.put("device_id", replaceWithJSONNull(deviceId));
            event.put("session_id", outOfSession ? -1 : sessionId);
            event.put("version_name", replaceWithJSONNull(deviceInfo.getVersionName()));
            event.put("os_name", replaceWithJSONNull(deviceInfo.getOsName()));
            event.put("os_version", replaceWithJSONNull(deviceInfo.getOsVersion()));
            event.put("device_brand", replaceWithJSONNull(deviceInfo.getBrand()));
            event.put("device_manufacturer", replaceWithJSONNull(deviceInfo.getManufacturer()));
            event.put("device_model", replaceWithJSONNull(deviceInfo.getModel()));
            event.put("carrier", replaceWithJSONNull(deviceInfo.getCarrier()));
            event.put("country", replaceWithJSONNull(deviceInfo.getCountry()));
            event.put("language", replaceWithJSONNull(deviceInfo.getLanguage()));
            event.put("platform", Constants.PLATFORM);

            JSONObject library = new JSONObject();
            library.put("name", Constants.LIBRARY);
            library.put("version", Constants.VERSION);
            event.put("library", library);

            apiProperties = (apiProperties == null) ? new JSONObject() : apiProperties;
            Location location = deviceInfo.getMostRecentLocation();
            if (location != null) {
                JSONObject locationJSON = new JSONObject();
                locationJSON.put("lat", location.getLatitude());
                locationJSON.put("lng", location.getLongitude());
                apiProperties.put("location", locationJSON);
            }
            if (deviceInfo.getAdvertisingId() != null) {
                apiProperties.put("androidADID", deviceInfo.getAdvertisingId());
            }
            apiProperties.put("limit_ad_tracking", deviceInfo.isLimitAdTrackingEnabled());

            event.put("api_properties", apiProperties);
            event.put("event_properties", (eventProperties == null) ? new JSONObject()
                    : eventProperties);
            event.put("user_properties", (userProperties == null) ? new JSONObject()
                    : userProperties);
        } catch (JSONException e) {
            listener.onError(
                new AmplitudeException(e));
        }

        return saveEvent(event);
    }

    protected long saveEvent(JSONObject event) {
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        long eventId = dbHelper.addEvent(event.toString());
        setLastEventId(eventId);
        long eventCount = dbHelper.getEventCount();

        if (eventCount >= eventMaxCount) {
            dbHelper.removeEvents(dbHelper.getNthEventId(Constants.EVENT_REMOVE_BATCH_SIZE));
        }

        if ((eventCount % eventUploadThreshold) == 0 && eventCount >= eventUploadThreshold) {
            updateServer(null);
        } else {
            updateServerLater(eventUploadPeriodMillis);
        }

        return eventId;
    }

    long getLastEventTime() {
        SharedPreferences preferences = context.getSharedPreferences(
                getSharedPreferencesName(), Context.MODE_PRIVATE);
        return preferences.getLong(Constants.PREFKEY_LAST_EVENT_TIME, -1);
    }

    void setLastEventTime(long timestamp) {
        SharedPreferences preferences = context.getSharedPreferences(
                getSharedPreferencesName(), Context.MODE_PRIVATE);
        preferences.edit().putLong(Constants.PREFKEY_LAST_EVENT_TIME, timestamp).commit();
    }

    long getLastEventId() {
        SharedPreferences preferences = context.getSharedPreferences(
                getSharedPreferencesName(), Context.MODE_PRIVATE);
        return preferences.getLong(Constants.PREFKEY_LAST_EVENT_ID, -1);
    }

    void setLastEventId(long eventId) {
        SharedPreferences preferences = context.getSharedPreferences(
                getSharedPreferencesName(), Context.MODE_PRIVATE);
        preferences.edit().putLong(Constants.PREFKEY_LAST_EVENT_ID, eventId).commit();
    }

    long getPreviousSessionId() {
        SharedPreferences preferences = context.getSharedPreferences(
                getSharedPreferencesName(), Context.MODE_PRIVATE);
        return preferences.getLong(Constants.PREFKEY_PREVIOUS_SESSION_ID, -1);
    }

    void setPreviousSessionId(long timestamp) {
        SharedPreferences preferences = context.getSharedPreferences(
                getSharedPreferencesName(), Context.MODE_PRIVATE);
        preferences.edit().putLong(Constants.PREFKEY_PREVIOUS_SESSION_ID, timestamp).commit();
    }

    boolean startNewSessionIfNeeded(long timestamp) {
        if (inSession()) {

            if (isWithinMinTimeBetweenSessions(timestamp)) {
                refreshSessionTime(timestamp);
                return false;
            }

            startNewSession(timestamp);
            return true;
        }

        // no current session - check for previous session
        if (isWithinMinTimeBetweenSessions(timestamp)) {
            long previousSessionId = getPreviousSessionId();
            if (previousSessionId == -1) {
                startNewSession(timestamp);
                return true;
            }

            // extend previous session
            setSessionId(previousSessionId);
            refreshSessionTime(timestamp);
            return false;
        }

        startNewSession(timestamp);
        return true;
    }

    private void startNewSession(long timestamp) {
        // end previous session
        if (trackingSessionEvents) {
            sendSessionEvent(END_SESSION_EVENT);
        }

        // start new session
        setSessionId(timestamp);
        refreshSessionTime(timestamp);
        if (trackingSessionEvents) {
            sendSessionEvent(START_SESSION_EVENT);
        }
    }

    private boolean inSession() {
        return sessionId >= 0;
    }

    private boolean isWithinMinTimeBetweenSessions(long timestamp) {
        long lastEventTime = getLastEventTime();
        long sessionLimit = usingForegroundTracking ?
                minTimeBetweenSessionsMillis : sessionTimeoutMillis;
        return (timestamp - lastEventTime) < sessionLimit;
    }

    private void setSessionId(long timestamp) {
        sessionId = timestamp;
        setPreviousSessionId(timestamp);
    }

    void refreshSessionTime(long timestamp) {
        if (!inSession()) {
            return;
        }

        setLastEventTime(timestamp);
    }

    private void sendSessionEvent(final String sessionEvent) {
        if (!contextAndApiKeySet(String.format("sendSessionEvent('%s')", sessionEvent))) {
            return;
        }

        if (!inSession()) {
            return;
        }

        JSONObject apiProperties = new JSONObject();
        try {
            apiProperties.put("special", sessionEvent);
        } catch (JSONException e) {
            return;
        }

        long timestamp = getLastEventTime();
        logEvent(sessionEvent, null, apiProperties, timestamp, false);
    }

    void onExitForeground(long timestamp) {
        refreshSessionTime(timestamp);
        inForeground = false;
    }

    void onEnterForeground(long timestamp) {
        startNewSessionIfNeeded(timestamp);
        inForeground = true;
    }

    public void logRevenue(double amount) {
        // Amount is in dollars
        // ex. $3.99 would be pass as logRevenue(3.99)
        logRevenue(null, 1, amount);
    }

    public void logRevenue(String productId, int quantity, double price) {
        logRevenue(productId, quantity, price, null, null);
    }

    public void logRevenue(String productId, int quantity, double price, String receipt,
            String receiptSignature) {
        if (!contextAndApiKeySet("logRevenue()")) {
            return;
        }

        // Log revenue in events
        JSONObject apiProperties = new JSONObject();
        try {
            apiProperties.put("special", REVENUE_EVENT);
            apiProperties.put("productId", productId);
            apiProperties.put("quantity", quantity);
            apiProperties.put("price", price);
            apiProperties.put("receipt", receipt);
            apiProperties.put("receiptSig", receiptSignature);
        } catch (JSONException e) {
            listener.onError(
                new AmplitudeException(e));
        }

        logEvent(REVENUE_EVENT, null, apiProperties, System.currentTimeMillis(), false);
    }

    public void setUserProperties(JSONObject userProperties) {
        setUserProperties(userProperties, false);
    }

    public void setUserProperties(final JSONObject userProperties, final boolean replace) {
        runOnLogThread(new Runnable() {
            @Override
            public void run() {
                AmplitudeClient instance = AmplitudeClient.this;
                if (userProperties == null) {
                    if (replace) {
                        instance.userProperties = null;
                    }
                    return;
                }

                // Create deep copy to try and prevent ConcurrentModificationException
                JSONObject copy;
                try {
                    copy = new JSONObject(userProperties.toString());
                } catch (JSONException e) {
                    Log.e(TAG, e.toString());
                    return; // could not create copy, cannot merge
                } // catch (ConcurrentModificationException e) {}

                JSONObject currentUserProperties = instance.userProperties;
                if (replace || currentUserProperties == null) {
                    instance.userProperties = copy;
                    return;
                }

                Iterator<?> keys = copy.keys();
                while (keys.hasNext()) {
                    String key = (String) keys.next();
                    try {
                        currentUserProperties.put(key, copy.get(key));
                    } catch (JSONException e) {
                        listener.onError(
                            new AmplitudeException(e));
                    }
                }
            }
        });
    }


    /**
     * @return The developer specified identifier for tracking within the analytics system.
     *         Can be null.
     */
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        if (!contextAndApiKeySet("setUserId()")) {
            return;
        }

        this.userId = userId;
        SharedPreferences preferences = context.getSharedPreferences(
                getSharedPreferencesName(), Context.MODE_PRIVATE);
        preferences.edit().putString(Constants.PREFKEY_USER_ID, userId).commit();
    }

    public void uploadEvents() {
        uploadEvents(null);
    }

    public void uploadEvents(final Amplitude.UploadCallback callback) {
        if (!contextAndApiKeySet("uploadEvents()")) {
            return;
        }

        logThread.post(new Runnable() {
            @Override
            public void run() {
                updateServer(callback);
            }
        });
    }

    private void updateServerLater(long delayMillis) {
        if (updateScheduled.getAndSet(true)) {
            return;
        }

        logThread.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateScheduled.set(false);
                updateServer(null);
            }
        }, delayMillis);
    }

    protected void updateServer(Amplitude.UploadCallback callback) {
        callback = callback == null? EMPTY: callback;
        updateServer(true, callback);
    }

    // Always call this from logThread
    protected void updateServer(boolean limit, final Amplitude.UploadCallback callback) {
        if (optOut || offline) {
            callback.onComplete();
            return;
        }

        if (!uploadingCurrently.getAndSet(true)) {
            DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
            try {
                long lastEventId = getLastEventId();
                int batchLimit = limit ? (backoffUpload ? backoffUploadBatchSize : eventUploadMaxBatchSize) : -1;
                Pair<Long, JSONArray> pair = dbHelper.getEvents(lastEventId, batchLimit);
                final long maxId = pair.first;
                final JSONArray events = pair.second;
                httpThread.post(new Runnable() {
                    @Override
                    public void run() {
                        makeEventUploadPostRequest(httpClient, events.toString(), maxId, callback);
                    }
                });
            } catch (JSONException e) {
                uploadingCurrently.set(false);
                AmplitudeException aE = new AmplitudeException(e);
                listener.onError(aE);
                callback.onError(aE);
            }
        } else {
            callback.onComplete();
        }
    }

    protected void makeEventUploadPostRequest(OkHttpClient client, String events, final long maxId, final Amplitude.UploadCallback callback) {
        String apiVersionString = "" + Constants.API_VERSION;
        String timestampString = "" + System.currentTimeMillis();

        String checksumString = "";
        try {
            String preimage = apiVersionString + apiKey + events + timestampString;

            // MessageDigest.getInstance(String) is not threadsafe on Android.
            // See https://code.google.com/p/android/issues/detail?id=37937
            // Use MD5 implementation from http://org.rodage.com/pub/java/security/MD5.java
            // This implementation does not throw NoSuchAlgorithm exceptions.
            MessageDigest messageDigest = new MD5();
            checksumString = bytesToHexString(messageDigest.digest(preimage.getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            // According to
            // http://stackoverflow.com/questions/5049524/is-java-utf-8-charset-exception-possible,
            // this will never be thrown
            listener.onError(new AmplitudeException(e));
        }

        RequestBody body = new FormEncodingBuilder()
            .add("v", apiVersionString)
            .add("client", apiKey)
            .add("e", events)
            .add("upload_time", timestampString)
            .add("checksum", checksumString)
            .build();

        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .build();

        boolean uploadSuccess = false;

        try {
            Response response = client.newCall(request).execute();
            String stringResponse = response.body().string();
            if (stringResponse.equals("success")) {
                uploadSuccess = true;
                logThread.post(new Runnable() {
                    @Override
                    public void run() {
                        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
                        dbHelper.removeEvents(maxId);
                        uploadingCurrently.set(false);
                        if (dbHelper.getEventCount() > eventUploadThreshold) {
                            logThread.post(new Runnable() {
                                @Override
                                public void run() {
                                    updateServer(backoffUpload, callback);
                                }
                            });
                        } else {
                            backoffUpload = false;
                            backoffUploadBatchSize = eventUploadMaxBatchSize;
                            callback.onComplete();
                        }
                    }
                });
            } else if (stringResponse.equals("invalid_api_key")) {
                throw
                    new AmplitudeException(
                        "Invalid API key, make sure your API key is correct in initialize()");
            } else if (stringResponse.equals("bad_checksum")) {
                throw
                    new AmplitudeException(
                        "Bad checksum, post request was mangled in transit, will attempt to reupload later");
            } else if (stringResponse.equals("request_db_write_failed")) {
                throw
                    new AmplitudeException(
                        "Couldn't write to request database on server, will attempt to reupload later");
            } else if (response.code() == 413) {

                // If blocked by one massive event, drop it
                DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
                if (backoffUpload && backoffUploadBatchSize == 1) {
                    dbHelper.removeEvent(maxId);
                    // maybe we want to reset backoffUploadBatchSize after dropping massive event
                }

                // Server complained about length of request, backoff and try again
                backoffUpload = true;
                int numEvents = Math.min((int)dbHelper.getEventCount(), backoffUploadBatchSize);
                backoffUploadBatchSize = (int)Math.ceil(numEvents / 2.0);
                Log.w(TAG, "Request too large, will decrease size and attempt to reupload");
                logThread.post(new Runnable() {
                   @Override
                    public void run() {
                       uploadingCurrently.set(false);
                       updateServer(true, callback);
                   }
                });
            } else {
                throw 
                    new AmplitudeException("Upload failed, " + stringResponse
                        + ", will attempt to reupload later");
            }
        } catch (AmplitudeException e){
            listener.onError(e);
            callback.onError(e);
            lastError = e;
        } catch (Throwable e) {
            AmplitudeException aE = new AmplitudeException(e);
            listener.onError(aE);
            callback.onError(aE);
            lastError = e;
        } finally {
            if (!uploadSuccess) {
                uploadingCurrently.set(false);
            }
        }

    }

    /**
     * @return A unique identifier for tracking within the analytics system. Can be null if
     *         deviceId hasn't been initialized yet;
     */
    public String getDeviceId() {
        return deviceId;
    }

    private String initializeDeviceId() {
        Set<String> invalidIds = new HashSet<String>();
        invalidIds.add("");
        invalidIds.add("9774d56d682e549c");
        invalidIds.add("unknown");
        invalidIds.add("000000000000000"); // Common Serial Number
        invalidIds.add("Android");
        invalidIds.add("DEFACE");

        // see if device id already stored in db
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        String deviceId = dbHelper.getValue(DEVICE_ID_KEY);
        if (!(TextUtils.isEmpty(deviceId) || invalidIds.contains(deviceId))) {
            return deviceId;
        }

        if (!newDeviceIdPerInstall && useAdvertisingIdForDeviceId) {
            // Android ID is deprecated by Google.
            // We are required to use Advertising ID, and respect the advertising ID preference

            String advertisingId = deviceInfo.getAdvertisingId();
            if (!(TextUtils.isEmpty(advertisingId) || invalidIds.contains(advertisingId))) {
                dbHelper.insertOrReplaceKeyValue(DEVICE_ID_KEY, advertisingId);
                return advertisingId;
            }
        }

        // If this still fails, generate random identifier that does not persist
        // across installations. Append R to distinguish as randomly generated
        String randomId = deviceInfo.generateUUID() + "R";
        dbHelper.insertOrReplaceKeyValue(DEVICE_ID_KEY, randomId);
        return randomId;
    }

    private void runOnLogThread(Runnable r) {
        if (Thread.currentThread() != logThread) {
            logThread.post(r);
        } else {
            r.run();
        }
    }

    protected Object replaceWithJSONNull(Object obj) {
        return obj == null ? JSONObject.NULL : obj;
    }

    protected synchronized boolean contextAndApiKeySet(String methodName) {
        if (context == null) {
            listener.onError(
                new AmplitudeException("context cannot be null, set context with initialize() before calling "
                    + methodName));
            return false;
        }
        if (TextUtils.isEmpty(apiKey)) {
            listener.onError(
                new AmplitudeException("apiKey cannot be null or empty, set apiKey with initialize() before calling "
                            + methodName));
            return false;
        }
        return true;
    }

    protected String getSharedPreferencesName() {
        return Constants.SHARED_PREFERENCES_NAME_PREFIX + "." + context.getPackageName();
    }

    protected String bytesToHexString(byte[] bytes) {
        final char[] hexArray = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b',
                'c', 'd', 'e', 'f' };
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Do a shallow copy of a JSONObject. Takes a bit of code to avoid
     * stringify and reparse given the API.
     */
    private JSONObject cloneJSONObject(final JSONObject obj) {
        if (obj == null) {
            return null;
        }

        // obj.names returns null if the json obj is empty.
        JSONArray nameArray = obj.names();
        int len = (nameArray != null ? nameArray.length() : 0);

        String[] names = new String[len];
        for (int i = 0; i < len; i++) {
            names[i] = nameArray.optString(i);
        }

        try {
            return new JSONObject(obj, names);
        } catch (JSONException e) {
            listener.onError(
                new AmplitudeException(e));
            return null;
        }
    }

    /**
     * Move all preference data from the legacy name to the new, static name if needed.
     *
     * Constants.PACKAGE_NAME used to be set using "Constants.class.getPackage().getName()"
     * Some aggressive proguard optimizations broke the reflection and caused apps
     * to crash on startup.
     *
     * Now that Constants.PACKAGE_NAME is changed, old data on devices needs to be
     * moved over to the new location so that device ids remain consistent.
     *
     * This should only happen once -- the first time a user loads the app after updating.
     * This logic needs to remain in place for quite a long time. It was first introduced in
     * April 2015 in version 1.6.0.
     */
    static boolean upgradePrefs(Context context, Amplitude.Listener listener) {
        return upgradePrefs(context, null, null, listener);
    }

    static boolean upgradePrefs(Context context, String sourcePkgName, String targetPkgName, Amplitude.Listener listener) {
        try {
            if (sourcePkgName == null) {
                // Try to load the package name using the old reflection strategy.
                sourcePkgName = Constants.PACKAGE_NAME;
                try {
                    sourcePkgName = Constants.class.getPackage().getName();
                } catch (Exception e) { }
            }

            if (targetPkgName == null) {
                targetPkgName = Constants.PACKAGE_NAME;
            }

            // No need to copy if the source and target are the same.
            if (targetPkgName.equals(sourcePkgName)) {
                return false;
            }

            // Copy over any preferences that may exist in a source preference store.
            String sourcePrefsName = sourcePkgName + "." + context.getPackageName();
            SharedPreferences source =
                    context.getSharedPreferences(sourcePrefsName, Context.MODE_PRIVATE);

            // Nothing left in the source store to copy
            if (source.getAll().size() == 0) {
                return false;
            }

            String prefsName = targetPkgName + "." + context.getPackageName();
            SharedPreferences targetPrefs =
                    context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
            SharedPreferences.Editor target = targetPrefs.edit();

            // Copy over all existing data.
            if (source.contains(sourcePkgName + ".previousSessionId")) {
                target.putLong(Constants.PREFKEY_PREVIOUS_SESSION_ID,
                        source.getLong(sourcePkgName + ".previousSessionId", -1));
            }
            if (source.contains(sourcePkgName + ".deviceId")) {
                target.putString(Constants.PREFKEY_DEVICE_ID,
                        source.getString(sourcePkgName + ".deviceId", null));
            }
            if (source.contains(sourcePkgName + ".userId")) {
                target.putString(Constants.PREFKEY_USER_ID,
                        source.getString(sourcePkgName + ".userId", null));
            }
            if (source.contains(sourcePkgName + ".optOut")) {
                target.putBoolean(Constants.PREFKEY_OPT_OUT,
                        source.getBoolean(sourcePkgName + ".optOut", false));
            }

            // Commit the changes and clear the source store so we don't recopy.
            target.apply();
            source.edit().clear().apply();

            Log.i(TAG, "Upgraded shared preferences from " + sourcePrefsName + " to " + prefsName);
            return true;

        } catch (Exception e) {
            listener.onError(
                new AmplitudeException("Error upgrading shared preferences", e));
            return false;
        }
    }

    /*
     * Move device ID from sharedPrefs to new sqlite key value store.
     *
     * This should only happen once -- the first time a user loads the app after updating.
     * This should happen only after moving the preference data from legacy to new static name.
     * This logic needs to remain in place for quite a long time. It was first introduced in
     * August 2015 in version 1.8.0.
     */
    static boolean upgradeDeviceIdToDB(Context context) {
        return upgradeDeviceIdToDB(context, null);
    }

    static boolean upgradeDeviceIdToDB(Context context, String sourcePkgName) {
        if (sourcePkgName == null) {
            sourcePkgName = Constants.PACKAGE_NAME;
        }

        String prefsName = sourcePkgName + "." + context.getPackageName();
        SharedPreferences preferences =
                context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);

        String deviceId = preferences.getString(Constants.PREFKEY_DEVICE_ID, null);
        if (!TextUtils.isEmpty(deviceId)) {
            DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
            dbHelper.insertOrReplaceKeyValue(DEVICE_ID_KEY, deviceId);

            // remove device id from sharedPrefs so that this upgrade occurs only once
            preferences.edit().remove(Constants.PREFKEY_DEVICE_ID).apply();
        }

        return true;
    }
}
