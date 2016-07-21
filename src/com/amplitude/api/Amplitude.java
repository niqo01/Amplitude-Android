package com.amplitude.api;

import android.content.Context;

import org.json.JSONObject;

public class Amplitude {

    public interface UploadCallback {
        void onComplete();
        void onError(AmplitudeException error);
    }

    public static AmplitudeClient getInstance() {
        return AmplitudeClient.getInstance();
    }

    @Deprecated
    public static void initialize(Context context, String apiKey) {
        getInstance().initialize(context, apiKey, null);
    }

    @Deprecated
    public static void initialize(Context context, String apiKey, String userId) {
        getInstance().initialize(context, apiKey, userId, null);
    }

    @Deprecated
    public static void enableNewDeviceIdPerInstall(boolean newDeviceIdPerInstall) {
        getInstance().enableNewDeviceIdPerInstall(newDeviceIdPerInstall);
    }

    @Deprecated
    public static void useAdvertisingIdForDeviceId() {
        getInstance().useAdvertisingIdForDeviceId();
    }

    @Deprecated
    public static void enableLocationListening() {
        getInstance().enableLocationListening();
    }

    @Deprecated
    public static void disableLocationListening() {
        getInstance().disableLocationListening();
    }

    @Deprecated
    public static void setSessionTimeoutMillis(long sessionTimeoutMillis) {
        getInstance().setSessionTimeoutMillis(sessionTimeoutMillis);
    }

    @Deprecated
    public static void setOptOut(boolean optOut) {
        getInstance().setOptOut(optOut);
    }

    @Deprecated
    public static void logEvent(String eventType) {
        getInstance().logEvent(eventType);
    }

    @Deprecated
    public static void logEvent(String eventType, JSONObject eventProperties) {
        getInstance().logEvent(eventType, eventProperties);
    }

    @Deprecated
    public static void uploadEvents() {
        getInstance().uploadEvents();
    }

    @Deprecated
    public static void startSession() { return; }

    @Deprecated
    public static void endSession() { return; }

    @Deprecated
    public static void logRevenue(double amount) {
        getInstance().logRevenue(amount);
    }

    @Deprecated
    public static void logRevenue(String productId, int quantity, double price) {
        getInstance().logRevenue(productId, quantity, price);
    }

    @Deprecated
    public static void logRevenue(String productId, int quantity, double price, String receipt,
            String receiptSignature) {
        getInstance().logRevenue(productId, quantity, price, receipt, receiptSignature);
    }

    @Deprecated
    public static void setUserProperties(JSONObject userProperties) {
        getInstance().setUserProperties(userProperties);
    }

    @Deprecated
    public static void setUserProperties(JSONObject userProperties, boolean replace) {
        getInstance().setUserProperties(userProperties, replace);
    }

    @Deprecated
    public static void setUserId(String userId) {
        getInstance().setUserId(userId);
    }

    @Deprecated
    public static String getDeviceId() {
        return getInstance().getDeviceId();
    }
}
