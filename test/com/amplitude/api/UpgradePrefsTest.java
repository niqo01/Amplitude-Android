package com.amplitude.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import android.content.Context;
import android.content.SharedPreferences;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class UpgradePrefsTest extends BaseTest {

    private Amplitude.Listener listener = new Amplitude.Listener() {
        @Override public void onError(AmplitudeException error) {

        }
    };

    @Before
    public void setUp() throws Exception {
        ShadowApplication.getInstance().setPackageName("com.amplitude.test");
        context = ShadowApplication.getInstance().getApplicationContext();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testUpgradeOnInit() {
        Constants.class.getPackage().getName();

        amplitude = new AmplitudeClient();
        amplitude.initialize(context, "KEY");
    }

    @Test
    public void testUpgrade() {
        String sourceName = "com.amplitude.a" + "." + context.getPackageName();
        context.getSharedPreferences(sourceName, Context.MODE_PRIVATE).edit()
                .putLong("com.amplitude.a.previousSessionId", 100L)
                .putString("com.amplitude.a.deviceId", "deviceid")
                .putString("com.amplitude.a.userId", "userid")
                .putBoolean("com.amplitude.a.optOut", true)
                .commit();

        assertTrue(AmplitudeClient.upgradePrefs(context, "com.amplitude.a", null, listener));

        String targetName = Constants.PACKAGE_NAME + "." + context.getPackageName();
        SharedPreferences target = context.getSharedPreferences(targetName, Context.MODE_PRIVATE);
        assertEquals(target.getLong(Constants.PREFKEY_PREVIOUS_SESSION_ID, -1), 100L);
        assertEquals(target.getString(Constants.PREFKEY_DEVICE_ID, null), "deviceid");
        assertEquals(target.getString(Constants.PREFKEY_USER_ID, null), "userid");
        assertEquals(target.getBoolean(Constants.PREFKEY_OPT_OUT, false), true);

        int size = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE).getAll().size();
        assertEquals(size, 0);
    }

    @Test
    public void testUpgradeSelf() {
        assertFalse(AmplitudeClient.upgradePrefs(context, listener));
    }

    @Test
    public void testUpgradeEmpty() {
        assertFalse(AmplitudeClient.upgradePrefs(context, "empty", null, listener));

        String sourceName = "empty" + "." + context.getPackageName();
        context.getSharedPreferences(sourceName, Context.MODE_PRIVATE).edit()
                .commit();

        assertFalse(AmplitudeClient.upgradePrefs(context, "empty", null, listener));
    }

    @Test
    public void testUpgradePartial() {
        String sourceName = "partial" + "." + context.getPackageName();
        context.getSharedPreferences(sourceName, Context.MODE_PRIVATE).edit()
                .putLong("partial.lastEventTime", 100L)
                .putString("partial.deviceId", "deviceid")
                .commit();

        assertTrue(AmplitudeClient.upgradePrefs(context, "partial", null, listener));

        String targetName = Constants.PACKAGE_NAME + "." + context.getPackageName();
        SharedPreferences target = context.getSharedPreferences(targetName, Context.MODE_PRIVATE);
        assertEquals(target.getLong(Constants.PREFKEY_PREVIOUS_SESSION_ID, -1), -1);
        assertEquals(target.getString(Constants.PREFKEY_DEVICE_ID, null), "deviceid");
        assertEquals(target.getString(Constants.PREFKEY_USER_ID, null), null);
        assertEquals(target.getBoolean(Constants.PREFKEY_OPT_OUT, false), false);

        int size = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE).getAll().size();
        assertEquals(size, 0);
    }

    @Test
    public void testUpgradeDeviceIdToDB() {
        String deviceId = "device_id";
        String sourceName = Constants.PACKAGE_NAME + "." + context.getPackageName();
        SharedPreferences prefs = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE);
        prefs.edit().putString(Constants.PREFKEY_DEVICE_ID, deviceId).commit();

        assertTrue(AmplitudeClient.upgradeDeviceIdToDB(context));
        assertEquals(
            DatabaseHelper.getDatabaseHelper(context).getValue(AmplitudeClient.DEVICE_ID_KEY),
            deviceId
        );

        // deviceId should be removed from sharedPrefs after upgrade
        assertNull(prefs.getString(Constants.PREFKEY_DEVICE_ID, null));
    }

    @Test
    public void testUpgradeDeviceIdToDBEmpty() {
        assertTrue(AmplitudeClient.upgradeDeviceIdToDB(context));
        assertNull(
            DatabaseHelper.getDatabaseHelper(context).getValue(AmplitudeClient.DEVICE_ID_KEY)
        );
    }

    @Test
    public void testUpgradeDeviceIdFromLegacyToDB() {
        String deviceId = "device_id";
        String legacyPkgName = "com.amplitude.a";
        String sourceName = legacyPkgName + "." + context.getPackageName();
        context.getSharedPreferences(sourceName, Context.MODE_PRIVATE).edit()
                .putString(legacyPkgName + ".deviceId", deviceId)
                .commit();

        assertTrue(AmplitudeClient.upgradePrefs(context, legacyPkgName, null));
        assertTrue(AmplitudeClient.upgradeDeviceIdToDB(context));

        String targetName = Constants.PACKAGE_NAME + "." + context.getPackageName();
        SharedPreferences target = context.getSharedPreferences(targetName, Context.MODE_PRIVATE);
        assertEquals(
            DatabaseHelper.getDatabaseHelper(context).getValue(AmplitudeClient.DEVICE_ID_KEY),
            deviceId
        );

        // deviceId should be removed from sharedPrefs after upgrade
        assertNull(target.getString(Constants.PREFKEY_DEVICE_ID, null));
    }

    @Test
    public void testUpgradeDeviceIdFromLegacyToDBEmpty() {
        String legacyPkgName = "com.amplitude.a";
        String sourceName = legacyPkgName + "." + context.getPackageName();
        context.getSharedPreferences(sourceName, Context.MODE_PRIVATE).edit()
                .putLong("partial.lastEventTime", 100L)
                .commit();

        assertTrue(AmplitudeClient.upgradePrefs(context, legacyPkgName, null));
        assertTrue(AmplitudeClient.upgradeDeviceIdToDB(context));

        String targetName = Constants.PACKAGE_NAME + "." + context.getPackageName();
        SharedPreferences target = context.getSharedPreferences(targetName, Context.MODE_PRIVATE);
        assertNull(target.getString(Constants.PREFKEY_DEVICE_ID, null));
        assertNull(
            DatabaseHelper.getDatabaseHelper(context).getValue(AmplitudeClient.DEVICE_ID_KEY)
        );
    }
}
