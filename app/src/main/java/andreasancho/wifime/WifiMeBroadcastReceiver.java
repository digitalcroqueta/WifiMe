package andreasancho.wifime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

/**
 * Created by andrea on 7/6/14.
 */
public class WifiMeBroadcastReceiver extends BroadcastReceiver{

    private WifiP2pManager mManager;
    private WifiP2pManager.Channel mChannel;
    private Discover activity;

    /**
     * @param manager WifiP2pManager system service
     * @param channel Wifi p2p channel
     * @param activity activity associated with the receiver
     */
    public WifiMeBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel,
                                   Discover activity) {
        super();
        this.mManager = manager;
        this.mChannel = channel;
        this.activity = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            // Determine if Wifi P2P mode is enabled or not, alert
            // the Activity.
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                activity.setIsWifiP2pEnabled(true);
            } else {
                activity.setIsWifiP2pEnabled(false);
                activity.resetData();
            }
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {

            // The peer list has changed!

            // request available peers from the wifi p2p manager. This is an
            // asynchronous call and the calling activity is notified with a
            // callback on PeerListListener.onPeersAvailable()
            if (mManager != null) {
                mManager.requestPeers(mChannel, (WifiP2pManager.PeerListListener) activity.getFragmentManager()
                        .findFragmentById(R.id.frag_list));
            }
            Log.d(Discover.TAG, "P2P peers changed");

        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {

            // Connection state changed!

            if (mManager == null) {
                return;
            }

            NetworkInfo networkInfo = (NetworkInfo) intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

            if (networkInfo.isConnected()) {
                // we are connected with the other device, request connection
                // info to find group owner IP
                DeviceDetailFragment fragment = (DeviceDetailFragment) activity
                        .getFragmentManager().findFragmentById(R.id.frag_detail);
                mManager.requestConnectionInfo(mChannel, fragment);
            } else {
                // It's a disconnect
                activity.resetData();
            }
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            DeviceListFragment fragment = (DeviceListFragment) activity.getFragmentManager()
                    .findFragmentById(R.id.frag_list);
            WifiP2pDevice d =(WifiP2pDevice) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
            fragment.updateThisDevice(d);

        }
    }
}
