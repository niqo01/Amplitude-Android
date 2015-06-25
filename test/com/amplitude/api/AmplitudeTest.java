package com.amplitude.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowLooper;

import android.content.Context;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class AmplitudeTest extends BaseTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testSetUserId() {
        String sharedPreferences = Constants.SHARED_PREFERENCES_NAME_PREFIX + "."
                + context.getPackageName();
        assertEquals(sharedPreferences, "com.amplitude.api.com.amplitude.test");
        String userId = "user_id";
        amplitude.setUserId(userId);
        assertEquals(
                userId,
                context.getSharedPreferences(sharedPreferences, Context.MODE_PRIVATE).getString(
                        Constants.PREFKEY_USER_ID, null));
    }

    @Test
    public void testSetUserProperties() throws JSONException {
        amplitude.setUserProperties(null);
        assertNull(amplitude.userProperties);

        JSONObject userProperties;
        JSONObject userProperties2;
        JSONObject expected;

        userProperties = new JSONObject();
        userProperties.put("key1", "value1");
        userProperties.put("key2", "value2");
        amplitude.setUserProperties(userProperties);
        assertEquals(amplitude.userProperties, userProperties);

        amplitude.setUserProperties(null);
        assertEquals(amplitude.userProperties, userProperties);

        userProperties2 = new JSONObject();
        userProperties.put("key2", "value3");
        userProperties.put("key3", "value4");
        amplitude.setUserProperties(userProperties2);
        expected = new JSONObject();
        expected.put("key1", "value1");
        expected.put("key2", "value3");
        expected.put("key3", "value4");
        // JSONObject doesn't have a proper equals method, so we compare strings
        // instead
        assertEquals(expected.toString(), amplitude.userProperties.toString());
    }

    @Test
    public void testGetDeviceIdWithoutAdvertisingId() {
        assertNull(amplitude.getDeviceId());
        ShadowLooper looper = Shadows.shadowOf(amplitude.logThread.getLooper());
        looper.getScheduler().advanceToLastPostedRunnable();
        assertNotNull(amplitude.getDeviceId());
        assertEquals(37, amplitude.getDeviceId().length());
        assertTrue(amplitude.getDeviceId().endsWith("R"));
    }

    @Test
    public void testOptOut() {
        ShadowLooper looper = Shadows.shadowOf(amplitude.logThread.getLooper());
        ShadowLooper httplooper = Shadows.shadowOf(amplitude.httpThread.getLooper());

        amplitude.setOptOut(true);
        RecordedRequest request = sendEvent(amplitude, "test_opt_out", null);
        assertNull(request);

        // Event shouldn't be sent event once opt out is turned off.
        amplitude.setOptOut(false);
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        httplooper.runToEndOfTasks();
        assertNull(request);

        request = sendEvent(amplitude, "test_opt_out", null);
        assertNotNull(request);
    }

    @Test
    public void testOffline() {
        ShadowLooper looper = Shadows.shadowOf(amplitude.logThread.getLooper());
        ShadowLooper httplooper = Shadows.shadowOf(amplitude.httpThread.getLooper());

        amplitude.setOffline(true);
        RecordedRequest request = sendEvent(amplitude, "test_offline", null);
        assertNull(request);

        // Events should be sent after offline is turned off.
        amplitude.setOffline(false);
        amplitude.uploadEvents();
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        httplooper.runToEndOfTasks();

        try {
            request = server.takeRequest(1, SECONDS);
        } catch (InterruptedException e) {
        }
        assertNotNull(request);
    }

    @Test
    public void testLogEvent() {
        RecordedRequest request = sendEvent(amplitude, "test_event", null);
        assertNotNull(request);
    }

    @Test
    public void testLogRevenue() {
        Shadows.shadowOf(amplitude.logThread.getLooper()).runToEndOfTasks();

        JSONObject event, apiProps;

        amplitude.logRevenue(10.99);
        Shadows.shadowOf(amplitude.logThread.getLooper()).runToEndOfTasks();
        Shadows.shadowOf(amplitude.logThread.getLooper()).runToEndOfTasks();

        event = getLastUnsentEvent();
        apiProps = event.optJSONObject("api_properties");
        assertEquals(AmplitudeClient.REVENUE_EVENT, event.optString("event_type"));
        assertEquals(AmplitudeClient.REVENUE_EVENT, apiProps.optString("special"));
        assertEquals(1, apiProps.optInt("quantity"));
        assertNull(apiProps.optString("productId", null));
        assertEquals(10.99, apiProps.optDouble("price"), .01);
        assertNull(apiProps.optString("receipt", null));
        assertNull(apiProps.optString("receiptSig", null));

        amplitude.logRevenue("ID1", 2, 9.99);
        Shadows.shadowOf(amplitude.logThread.getLooper()).runToEndOfTasks();
        Shadows.shadowOf(amplitude.logThread.getLooper()).runToEndOfTasks();

        event = getLastUnsentEvent();
        apiProps = event.optJSONObject("api_properties");;
        assertEquals(AmplitudeClient.REVENUE_EVENT, event.optString("event_type"));
        assertEquals(AmplitudeClient.REVENUE_EVENT, apiProps.optString("special"));
        assertEquals(2, apiProps.optInt("quantity"));
        assertEquals("ID1", apiProps.optString("productId"));
        assertEquals(9.99, apiProps.optDouble("price"), .01);
        assertNull(apiProps.optString("receipt", null));
        assertNull(apiProps.optString("receiptSig", null));

        amplitude.logRevenue("ID2", 3, 8.99, "RECEIPT", "SIG");
        Shadows.shadowOf(amplitude.logThread.getLooper()).runToEndOfTasks();
        Shadows.shadowOf(amplitude.logThread.getLooper()).runToEndOfTasks();

        event = getLastUnsentEvent();
        apiProps = event.optJSONObject("api_properties");
        assertEquals(AmplitudeClient.REVENUE_EVENT, event.optString("event_type"));
        assertEquals(AmplitudeClient.REVENUE_EVENT, apiProps.optString("special"));
        assertEquals(3, apiProps.optInt("quantity"));
        assertEquals("ID2", apiProps.optString("productId"));
        assertEquals(8.99, apiProps.optDouble("price"), .01);
        assertEquals("RECEIPT", apiProps.optString("receipt"));
        assertEquals("SIG", apiProps.optString("receiptSig"));

        assertNotNull(runRequest());
    }

    @Test
    public void testLogEventSync() {
        ShadowLooper looper = Shadows.shadowOf(amplitude.logThread.getLooper());
        looper.runToEndOfTasks();

        amplitude.logEventSync("test_event_sync", null);

        // Event should be in the database synchronously.
        JSONObject event = getLastEvent();
        assertEquals("test_event_sync", event.optString("event_type"));

        looper.runToEndOfTasks();

        server.enqueue(new MockResponse().setBody("success"));
        ShadowLooper httplooper = Shadows.shadowOf(amplitude.httpThread.getLooper());
        httplooper.runToEndOfTasks();

        try {
            assertNotNull(server.takeRequest(1, SECONDS));
        } catch (InterruptedException e) {
            fail(e.toString());
        }
    }

    /**
     * Test for not excepting on empty event properties.
     * See https://github.com/amplitude/Amplitude-Android/issues/35
     */
    @Test
    public void testEmptyEventProps() {
        RecordedRequest request = sendEvent(amplitude, "test_event", new JSONObject());
        assertNotNull(request);
    }
}
