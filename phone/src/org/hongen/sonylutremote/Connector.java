package org.hongen.sonylutremote;

import android.net.Network;

/** Common surface of the two Wi-Fi connector implementations. The API 29
 * implementation is loaded reflectively so older Android releases never
 * resolve its classes. */
interface Connector {
    interface Callback {
        void onWifiReady(Network network);
        void onWifiLost(String reason);
        void onWifiFailed(String reason);
    }

    void connect(String ssid, String psk, Callback callback);
    void disconnect();
}
