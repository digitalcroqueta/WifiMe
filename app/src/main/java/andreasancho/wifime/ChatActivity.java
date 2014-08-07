package andreasancho.wifime;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;



public class ChatActivity extends Activity {

    public String mServiceName = "WifiMeChat";
    public NsdManager mNsdManager;
    private NsdManager.ResolveListener mResolveListener;
    private NsdManager.DiscoveryListener mDiscoveryListener;
    private NsdManager.RegistrationListener mRegistrationListener;
    private NsdServiceInfo mService;
    private int mLocalPort;
    private static final String TAG = "-------------WifiMe.ChatActivity";
    public static final String SERVICE_TYPE = "_http._tcp.";
    public static final String SERVICE_NAME = "WifiMeChat";
    public ChatConnection mConnection;
    private Handler mUpdateHandler;
    private TextView mStatusView;
    private static Boolean isServiceRegistered;
    private static Boolean isServiceFound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        mStatusView = (TextView) findViewById(R.id.conversation);

        mUpdateHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                String chatLine = msg.getData().getString("msg");
                addChatLine(chatLine);
            }
        };

        this.mConnection = new ChatConnection(mUpdateHandler, getApplicationContext());
        this.mNsdManager = (NsdManager) getApplicationContext().getSystemService(Context.NSD_SERVICE);

        isServiceRegistered = false;
        isServiceFound = false;

        initializeResolveListener();
        initializeDiscoveryListener();
        initializeRegistrationListener();

//        boolean registrationSucceed = mRegistrationListener != null;
//        Log.d(TAG, "registrationListener: " + registrationSucceed);
//        boolean discoverySucceed = mDiscoveryListener != null;
//        Log.d(TAG, "discoveryListener: " + discoverySucceed);
//        boolean resolutionSucceed = mResolveListener != null;
//        Log.d(TAG, "resolverListener: " + resolutionSucceed);

    }

    public void initializeResolveListener() {
        mResolveListener = new NsdManager.ResolveListener() {

            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Called when the resolve fails.  Use the error code to debug.
                Log.e(TAG, "Resolve failed: " + errorCode);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Resolve Succeeded. ServiceInfo: " + serviceInfo);
                if (serviceInfo.getServiceName().equals(mServiceName)) {
                    Log.d(TAG, "Same IP.");
                    return;
                }
                mService = serviceInfo;
                Log.d(TAG, ">>>>>>   Actual mService (onServiceResolve): " + mService);
            }
        };
    }

    public void initializeDiscoveryListener() {

        // Instantiate a new DiscoveryListener
        mDiscoveryListener = new NsdManager.DiscoveryListener() {

            //  Called as soon as service discovery begins.
            @Override
            public void onDiscoveryStarted(String regType) {
                Log.d(TAG, "Service discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                // A service was found!  Do something with it.
                Log.d(TAG, "Service discovery success" + service);
                if (!service.getServiceType().equals(SERVICE_TYPE)) {
                    // Service type is the string containing the protocol and
                    // transport layer for this service.
                    Log.d(TAG, "Unknown Service Type: " + service.getServiceType());
                } else if (service.getServiceName().equals(mServiceName)) {
                    // The name of the service tells the user what they'd be
                    // connecting to. It could be "Bob's Chat App".
                    Log.d(TAG, "Same machine: " + mServiceName);
                } else if (service.getServiceName().contains(mServiceName)) {
                    Log.d(TAG, "Other machine: " + service.getServiceName());
                    isServiceFound = true;
                    mNsdManager.resolveService(service, mResolveListener);
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                // When the network service is no longer available.
                // Internal bookkeeping code goes here.
                if (mService == service) {
                    mService = null;
                }
                Log.e(TAG, "Service lost" + service);
                isServiceFound = false;
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.i(TAG, "Discovery stopped: " + serviceType);
                isServiceFound = false;
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed onStart: Error code:" + errorCode);
                mNsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed onStop: Error code:" + errorCode);
                mNsdManager.stopServiceDiscovery(this);
            }
        };
    }

    public void initializeRegistrationListener() {
        mRegistrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo serviceReg) {
                // Save the service name.  Android may have changed it in order to
                // resolve a conflict, so update the name you initially requested
                // with the name Android actually used.
                mServiceName = serviceReg.getServiceName();
                Log.d(TAG, "Service registered: " + mServiceName);
                isServiceRegistered = true;
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceReg, int errorCode) {
                // Registration failed!  Put debugging code here to determine why.
                isServiceRegistered = false;
                Log.d(TAG, "Registration Failed");
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo serviceReg) {
                // Service has been unregistered.  This only happens when you call
                // NsdManager.unregisterService() and pass in this listener.
                isServiceRegistered = false;
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceReg, int errorCode) {
                // Unregistration failed.  Put debugging code here to determine why.
            }
        };
    }


    public void clickStart(View v) {
        mLocalPort = mConnection.getLocalPort();
        //Log.d(TAG, "Local Port (onclickStart): " + mLocalPort);
        if(!isServiceRegistered) mAdvertise();
        if(!isServiceFound) discoverServices();
        if(isServiceFound) mResolve();

        if(mConnection.getConnectionEstablished()) {
            findViewById(R.id.start_btn).setVisibility(View.GONE);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Chat Connection Established").show();

//            Toast t = Toast.makeText(this,"Chat connection established" ,
//                    Toast.LENGTH_SHORT);
//            t.setGravity(Gravity.CENTER, 0, 0);
//            t.show();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Chat Connection Not Established");
            builder.setMessage("Click again in [Start Chat Connection]").show();
//            Toast t = Toast.makeText(this, "Chat connection not yet established, click again in [Start Chat Connection]",
//                    Toast.LENGTH_SHORT);
//            t.setGravity(Gravity.CENTER, 0, 0);
//            t.show();
        }
    }

    public void mAdvertise() {
        //Log.d(TAG, "Local Port (in Advertise): " + mLocalPort);
        if (mLocalPort > -1) {
            registerService(mLocalPort);
        } else {
            Log.d(TAG, "ServerSocket isn't bound.");
        }
    }


    public void mResolve() {
        Log.d(TAG, "Actual mService (mResolve): " + mService);
        if (mService != null) {
            Log.d(TAG, "Connecting to: " + mService.getHost() + "  "+ mService.getPort() );
            this.mConnection.connectToServer(mService.getHost(), mService.getPort());
        } else {
            Log.d(TAG, "No service to connect to!");
        }
    }

    public void clickSend(View v) {
        InputMethodManager inputManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
                InputMethodManager.HIDE_NOT_ALWAYS);
        EditText messageView = (EditText) this.findViewById(R.id.chatInput);
        if (messageView != null) {
            String messageString = messageView.getText().toString();
            if (!messageString.isEmpty()) {
                mConnection.sendMessage(messageString);
            }
            messageView.setText("");
        }
    }


    public void registerService(int port) {
        // Create the NsdServiceInfo object, and populate it.
        NsdServiceInfo serviceInfo = new NsdServiceInfo();

        // The name is subject to change based on conflicts
        // with other services advertised on the same network.
        serviceInfo.setServiceName(SERVICE_NAME);
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(port);

        Log.d(TAG, "Trying to Register Service (int port): " + port + "  " + serviceInfo);
        this.mNsdManager.registerService(
                serviceInfo, NsdManager.PROTOCOL_DNS_SD, mRegistrationListener);
    }



    public void discoverServices() {
        if (mNsdManager != null) {
            mNsdManager.discoverServices(
                    SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
        }
    }


    public void tearDown() {
        if (mRegistrationListener != null && isServiceRegistered)
            mNsdManager.unregisterService(mRegistrationListener);
        if (mDiscoveryListener != null && isServiceFound)
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mNsdManager.discoverServices(
                SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    @Override
    protected void onDestroy() {
        tearDown();
        mConnection.tearDown();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        mNsdManager.stopServiceDiscovery(mDiscoveryListener);
        super.onPause();
    }

    public void addChatLine(String line) {
        mStatusView.append("\n" + line);
    }
}