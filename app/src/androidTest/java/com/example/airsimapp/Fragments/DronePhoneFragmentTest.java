package com.example.airsimapp.Fragments;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.example.airsimapp.PixhawkMavlinkUsb;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.lang.reflect.Field;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.spy;

import android.net.wifi.p2p.WifiP2pDevice;

import androidx.test.core.app.ApplicationProvider;

import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import android.widget.ArrayAdapter;

@RunWith(RobolectricTestRunner.class)
public class DronePhoneFragmentTest {

    private DronePhoneFragment fragment;
    private FragmentActivity activity;
    private TextView outputView;

    @Mock
    private PixhawkMavlinkUsb mockPixhawk;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        ActivityController<FragmentActivity> controller =
                Robolectric.buildActivity(FragmentActivity.class).setup();
        activity = controller.get();

        fragment = new DronePhoneFragment();

        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(fragment, "test-fragment")
                .commitNow();

        outputView = new TextView(activity);

        setPrivateField(fragment, "pixhawk", mockPixhawk);
        setPrivateField(fragment, "output", outputView);
    }

    @Test
    public void onMessageReceived_validCtrlPacket_updatesPixhawkAndOutput() {
        when(mockPixhawk.isArmed()).thenReturn(false);

        fragment.onMessageReceived("CTRL,10,20,30,40,1\n");

        verify(mockPixhawk).setRoll(10);
        verify(mockPixhawk).setPitch(20);
        verify(mockPixhawk).setYaw(30);
        verify(mockPixhawk).setThrottle(40);
        verify(mockPixhawk).arm();

        String text = outputView.getText().toString();
        assertEquals(
                "CONTROL PACKET\n\n" +
                        "Roll: 10\n" +
                        "Pitch: 20\n" +
                        "Yaw: 30\n" +
                        "Throttle: 40\n" +
                        "Armed: YES",
                text
        );
    }

    @Test
    public void onMessageReceived_armPacket_whenAlreadyArmed_doesNotArmAgain() {
        when(mockPixhawk.isArmed()).thenReturn(true);

        fragment.onMessageReceived("CTRL,1,2,3,4,1\n");

        verify(mockPixhawk).setRoll(1);
        verify(mockPixhawk).setPitch(2);
        verify(mockPixhawk).setYaw(3);
        verify(mockPixhawk).setThrottle(4);
        verify(mockPixhawk, never()).arm();
    }

    @Test
    public void onMessageReceived_disarmPacket_whenArmed_setsThrottleZeroAndDisarms() {
        when(mockPixhawk.isArmed()).thenReturn(true);

        fragment.onMessageReceived("CTRL,10,20,30,40,0\n");

        verify(mockPixhawk).setRoll(10);
        verify(mockPixhawk).setPitch(20);
        verify(mockPixhawk).setYaw(30);
        verify(mockPixhawk).setThrottle(40);

        // extra throttle reset before disarm
        verify(mockPixhawk).setThrottle(0);
        verify(mockPixhawk).disarm();

        String text = outputView.getText().toString();
        assertEquals(
                "CONTROL PACKET\n\n" +
                        "Roll: 10\n" +
                        "Pitch: 20\n" +
                        "Yaw: 30\n" +
                        "Throttle: 40\n" +
                        "Armed: NO",
                text
        );
    }

    @Test
    public void onMessageReceived_disarmPacket_whenAlreadyDisarmed_doesNotDisarmAgain() {
        when(mockPixhawk.isArmed()).thenReturn(false);

        fragment.onMessageReceived("CTRL,10,20,30,40,0\n");

        verify(mockPixhawk).setRoll(10);
        verify(mockPixhawk).setPitch(20);
        verify(mockPixhawk).setYaw(30);
        verify(mockPixhawk).setThrottle(40);
        verify(mockPixhawk, never()).disarm();
    }

    @Test
    public void onMessageReceived_shortPacket_ignored() {
        fragment.onMessageReceived("CTRL,10,20,30\n");

        verify(mockPixhawk, never()).setRoll(org.mockito.ArgumentMatchers.anyInt());
        verify(mockPixhawk, never()).setPitch(org.mockito.ArgumentMatchers.anyInt());
        verify(mockPixhawk, never()).setYaw(org.mockito.ArgumentMatchers.anyInt());
        verify(mockPixhawk, never()).setThrottle(org.mockito.ArgumentMatchers.anyInt());
        assertEquals("", outputView.getText().toString());
    }

    @Test
    public void onMessageReceived_nonCtrlPacket_ignored() {
        fragment.onMessageReceived("TEL,1,2,3,4,5\n");

        verify(mockPixhawk, never()).setRoll(org.mockito.ArgumentMatchers.anyInt());
        verify(mockPixhawk, never()).arm();
        verify(mockPixhawk, never()).disarm();
        assertEquals("", outputView.getText().toString());
    }

    @Test
    public void onMessageReceived_blankLines_ignored() {
        when(mockPixhawk.isArmed()).thenReturn(false);

        fragment.onMessageReceived("\n\nCTRL,5,6,7,8,1\n\n");

        verify(mockPixhawk).setRoll(5);
        verify(mockPixhawk).setPitch(6);
        verify(mockPixhawk).setYaw(7);
        verify(mockPixhawk).setThrottle(8);
        verify(mockPixhawk).arm();
    }

    @Test
    public void onMessageReceived_multiplePackets_processesEachOne() {
        when(mockPixhawk.isArmed()).thenReturn(false, true);

        fragment.onMessageReceived(
                "CTRL,1,2,3,4,1\n" +
                        "CTRL,5,6,7,8,0\n"
        );

        verify(mockPixhawk).setRoll(1);
        verify(mockPixhawk).setPitch(2);
        verify(mockPixhawk).setYaw(3);
        verify(mockPixhawk).setThrottle(4);
        verify(mockPixhawk).arm();

        verify(mockPixhawk).setRoll(5);
        verify(mockPixhawk).setPitch(6);
        verify(mockPixhawk).setYaw(7);
        verify(mockPixhawk).setThrottle(8);
        verify(mockPixhawk).setThrottle(0);
        verify(mockPixhawk).disarm();
    }

    @Test(expected = NumberFormatException.class)
    public void onMessageReceived_invalidInteger_throwsForNow() {
        fragment.onMessageReceived("CTRL,a,2,3,4,1\n");
    }
    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }



    @Test
    public void getSnapshotJpeg_returnsNullInitially() {
        DronePhoneFragment fragment = new DronePhoneFragment();

        assertNull(fragment.getSnapshotJpeg());
    }

    @Test
    public void getSnapshotJpeg_returnsSnapshotAfterSet() {
        DronePhoneFragment fragment = new DronePhoneFragment();
        byte[] expected = new byte[]{1, 2, 3, 4};

        fragment.setLatestSnapshot(expected);

        assertArrayEquals(expected, fragment.getSnapshotJpeg());
    }

    @Test
    public void getSnapshotJpeg_returnsSameReferenceAfterSet() {
        DronePhoneFragment fragment = new DronePhoneFragment();
        byte[] expected = new byte[]{10, 20, 30};

        fragment.setLatestSnapshot(expected);

        assertSame(expected, fragment.getSnapshotJpeg());
    }

    @Test
    public void getSnapshotJpeg_returnsEmptyArrayWhenEmptyArrayWasSet() {
        DronePhoneFragment fragment = new DronePhoneFragment();
        byte[] expected = new byte[]{};

        fragment.setLatestSnapshot(expected);

        assertArrayEquals(expected, fragment.getSnapshotJpeg());
        assertSame(expected, fragment.getSnapshotJpeg());
    }

    @Test
    public void setLatestSnapshot_overwritesPreviousSnapshot() {
        DronePhoneFragment fragment = new DronePhoneFragment();
        byte[] first = new byte[]{1, 2, 3};
        byte[] second = new byte[]{9, 8, 7};

        fragment.setLatestSnapshot(first);
        fragment.setLatestSnapshot(second);

        assertArrayEquals(second, fragment.getSnapshotJpeg());
        assertSame(second, fragment.getSnapshotJpeg());
    }

    @Test
    public void setLatestSnapshot_allowsNullAfterValueWasSet() {
        DronePhoneFragment fragment = new DronePhoneFragment();
        byte[] expected = new byte[]{1, 2, 3};

        fragment.setLatestSnapshot(expected);
        fragment.setLatestSnapshot(null);

        assertNull(fragment.getSnapshotJpeg());
    }

    @Test
    public void getSnapshotJpeg_reflectsChangesToReturnedArrayBecauseSameReferenceIsReturned() {
        DronePhoneFragment fragment = new DronePhoneFragment();
        byte[] expected = new byte[]{1, 2, 3};

        fragment.setLatestSnapshot(expected);

        byte[] result = fragment.getSnapshotJpeg();
        result[0] = 99;

        assertEquals(99, fragment.getSnapshotJpeg()[0]);
    }


    @org.junit.jupiter.api.Test
    void captureFrameForP2p() {
        DronePhoneFragment fragment = new DronePhoneFragment();

        // here we test that null image should do nothing when snapshot is null
        fragment.setLatestSnapshot(null);
        fragment.captureFrameForP2p(null);
        assertNull(fragment.getSnapshotJpeg());

        // we also need to make sure that null image should not overwrite existing snapshot
        byte[] original = new byte[]{1, 2, 3};
        fragment.setLatestSnapshot(original);

        fragment.captureFrameForP2p(null);

        assertArrayEquals(original, fragment.getSnapshotJpeg());
        assertSame(original, fragment.getSnapshotJpeg());
    }


    @Test
    public void onPeersUpdated_updatesPeerNamesAndNotifiesAdapter() throws Exception {
        ActivityController<FragmentActivity> controller =
                Robolectric.buildActivity(FragmentActivity.class).setup();
        FragmentActivity activity = controller.get();

        DronePhoneFragment fragment = new DronePhoneFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(fragment, null)
                .commitNow();

        ArrayAdapter<String> adapter = spy(new ArrayAdapter<>(
                activity,
                android.R.layout.simple_list_item_1,
                new ArrayList<>()
        ));

        setField(fragment, "peerAdapter", adapter);

        List<WifiP2pDevice> peers = new ArrayList<>();

        WifiP2pDevice d1 = new WifiP2pDevice();
        d1.deviceName = "PhoneA";
        d1.deviceAddress = "AA:BB:CC:DD:EE:01";

        WifiP2pDevice d2 = new WifiP2pDevice();
        d2.deviceName = "PhoneB";
        d2.deviceAddress = "AA:BB:CC:DD:EE:02";

        peers.add(d1);
        peers.add(d2);

        fragment.onPeersUpdated(peers);
        Shadows.shadowOf(activity.getMainLooper()).idle();

        List<String> peerNames = getPeerNames(fragment);

        assertEquals(2, peerNames.size());
        assertEquals("PhoneA\nAA:BB:CC:DD:EE:01", peerNames.get(0));
        assertEquals("PhoneB\nAA:BB:CC:DD:EE:02", peerNames.get(1));

        verify(adapter).notifyDataSetChanged();
    }
    @Test
    public void onPeersUpdated_clearsOldPeerNamesBeforeAddingNewOnes() throws Exception {
        ActivityController<FragmentActivity> controller =
                Robolectric.buildActivity(FragmentActivity.class).setup();
        FragmentActivity activity = controller.get();

        DronePhoneFragment fragment = new DronePhoneFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(fragment, null)
                .commitNow();

        ArrayAdapter<String> adapter = spy(new ArrayAdapter<>(
                activity,
                android.R.layout.simple_list_item_1,
                new ArrayList<>()
        ));

        setField(fragment, "peerAdapter", adapter);

        List<String> peerNames = getPeerNames(fragment);
        peerNames.add("OldDevice\n11:11:11:11:11:11");

        WifiP2pDevice d1 = new WifiP2pDevice();
        d1.deviceName = "NewDevice";
        d1.deviceAddress = "22:22:22:22:22:22";

        fragment.onPeersUpdated(Arrays.asList(d1));
        Shadows.shadowOf(activity.getMainLooper()).idle();

        assertEquals(1, peerNames.size());
        assertEquals("NewDevice\n22:22:22:22:22:22", peerNames.get(0));

        verify(adapter).notifyDataSetChanged();
    }
    @Test
    public void onPeersUpdated_handlesEmptyPeerList() throws Exception {
        ActivityController<FragmentActivity> controller =
                Robolectric.buildActivity(FragmentActivity.class).setup();
        FragmentActivity activity = controller.get();

        DronePhoneFragment fragment = new DronePhoneFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(fragment, null)
                .commitNow();

        ArrayAdapter<String> adapter = spy(new ArrayAdapter<>(
                activity,
                android.R.layout.simple_list_item_1,
                new ArrayList<>()
        ));

        setField(fragment, "peerAdapter", adapter);

        List<String> peerNames = getPeerNames(fragment);
        peerNames.add("SomethingOld\n00:00:00:00:00:00");

        fragment.onPeersUpdated(new ArrayList<>());
        Shadows.shadowOf(activity.getMainLooper()).idle();

        assertTrue(peerNames.isEmpty());
        verify(adapter).notifyDataSetChanged();
    }
    @Test
    public void onPeersUpdated_doesNothingWhenFragmentNotAdded() throws Exception {
        DronePhoneFragment fragment = new DronePhoneFragment();

        ArrayAdapter<String> adapter = spy(new ArrayAdapter<>(
                androidx.test.core.app.ApplicationProvider.getApplicationContext(),
                android.R.layout.simple_list_item_1,
                new ArrayList<>()
        ));

        setField(fragment, "peerAdapter", adapter);

        List<String> peerNames = getPeerNames(fragment);
        peerNames.add("OldDevice\n11:11:11:11:11:11");

        WifiP2pDevice d1 = new WifiP2pDevice();
        d1.deviceName = "PhoneA";
        d1.deviceAddress = "AA:BB:CC:DD:EE:01";

        fragment.onPeersUpdated(Arrays.asList(d1));

        assertEquals(1, peerNames.size());
        assertEquals("OldDevice\n11:11:11:11:11:11", peerNames.get(0));
        verify(adapter, never()).notifyDataSetChanged();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private List<String> getPeerNames(DronePhoneFragment fragment) throws Exception {
        Field field = DronePhoneFragment.class.getDeclaredField("peerNames");
        field.setAccessible(true);
        return (List<String>) field.get(fragment);
    }


    @Test
    public void onConnectionStatusChanged_updatesTextViewWhenAdded() throws Exception {
        ActivityController<FragmentActivity> controller =
                Robolectric.buildActivity(FragmentActivity.class).setup();
        FragmentActivity activity = controller.get();

        DronePhoneFragment fragment = new DronePhoneFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(fragment, null)
                .commitNow();

        TextView statusView = new TextView(activity);
        setField(fragment, "connectionStatus", statusView);

        fragment.onConnectionStatusChanged("Connected");
        Shadows.shadowOf(activity.getMainLooper()).idle();

        assertEquals("Connected", statusView.getText().toString());
    }

    @Test
    public void onConnectionStatusChanged_handlesNullTextViewWithoutCrash() throws Exception {
        ActivityController<FragmentActivity> controller =
                Robolectric.buildActivity(FragmentActivity.class).setup();
        FragmentActivity activity = controller.get();

        DronePhoneFragment fragment = new DronePhoneFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(fragment, null)
                .commitNow();

        setField(fragment, "connectionStatus", null);

        fragment.onConnectionStatusChanged("Disconnected");
        Shadows.shadowOf(activity.getMainLooper()).idle();

        assertNull(getField(fragment, "connectionStatus"));
    }

    @Test
    public void onConnectionStatusChanged_doesNothingWhenFragmentNotAdded() throws Exception {
        DronePhoneFragment fragment = new DronePhoneFragment();

        TextView statusView = new TextView(
                androidx.test.core.app.ApplicationProvider.getApplicationContext()
        );
        statusView.setText("Old Status");

        setField(fragment, "connectionStatus", statusView);

        fragment.onConnectionStatusChanged("New Status");

        assertEquals("Old Status", statusView.getText().toString());
    }


    private Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }



    @Test
    public void onError_updatesStatusAndSetsConnectionFalse() throws Exception {
        ActivityController<FragmentActivity> controller =
                Robolectric.buildActivity(FragmentActivity.class).setup();
        FragmentActivity activity = controller.get();

        DronePhoneFragment fragment = new DronePhoneFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(fragment, null)
                .commitNow();

        TextView statusView = new TextView(activity);
        setField(fragment, "connectionStatus", statusView);
        setField(fragment, "p2pConnected", true);

        fragment.onError("Connection Failed");
        Shadows.shadowOf(activity.getMainLooper()).idle();

        assertEquals("Connection Failed", statusView.getText().toString());

        boolean connected = (boolean) getField(fragment, "p2pConnected");
        assertFalse(connected);
    }

    @Test
    public void onError_handlesNullTextView() throws Exception {
        ActivityController<FragmentActivity> controller =
                Robolectric.buildActivity(FragmentActivity.class).setup();
        FragmentActivity activity = controller.get();

        DronePhoneFragment fragment = new DronePhoneFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(fragment, null)
                .commitNow();

        setField(fragment, "connectionStatus", null);
        setField(fragment, "p2pConnected", true);

        fragment.onError("Error");
        Shadows.shadowOf(activity.getMainLooper()).idle();

        boolean connected = (boolean) getField(fragment, "p2pConnected");
        assertFalse(connected);
    }

    @Test
    public void onError_doesNothingWhenFragmentNotAdded() throws Exception {
        DronePhoneFragment fragment = new DronePhoneFragment();

        TextView statusView = new TextView(
                androidx.test.core.app.ApplicationProvider.getApplicationContext()
        );
        statusView.setText("Old");

        setField(fragment, "connectionStatus", statusView);
        setField(fragment, "p2pConnected", true);

        fragment.onError("New Error");

        // text should NOT change because fragment isn't added
        assertEquals("Old", statusView.getText().toString());

        boolean connected = (boolean) getField(fragment, "p2pConnected");
        assertFalse(connected); // still should change
    }


}