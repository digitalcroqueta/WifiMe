package andreasancho.wifime;

/**
 * Created by andrea on 7/6/14.
 */

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;


/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment implements WifiP2pManager.ConnectionInfoListener {

    protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    public static final String IP_GO = "192.168.49.1";
    public static final String IP_NGO = "192.168.49.162";
    public static int PORT = 8988;
    private View mContentView = null;
    private WifiP2pDevice device;
    private WifiP2pInfo info;
    ProgressDialog progressDialog = null;
    private static boolean server_openned = false;
    private static boolean connected = false;
    private static int gn;


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mContentView = inflater.inflate(R.layout.device_detail, null);
        gn = -1;

        mContentView.findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                config.wps.setup = WpsInfo.PBC;
                config.groupOwnerIntent = gn;

                Log.d(Discover.TAG, "GROUP OWNER CONFIG = " + gn);

                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel",
                        "Connecting to :" + device.deviceAddress, true, true,
                        new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                ((DeviceListFragment.DeviceActionListener) getActivity()).cancelDisconnect();
                            }
                        });
                ((DeviceListFragment.DeviceActionListener) getActivity()).connect(config);
            }
        });
        mContentView.findViewById(R.id.btn_disconnect).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((DeviceListFragment.DeviceActionListener) getActivity()).disconnect();
                        mContentView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
                    }
                });
        mContentView.findViewById(R.id.btn_get_file).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Allow user to pick an (image) File from sd or other
                        // registered apps
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("*/*");
                        startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE);
                    }
                });
        mContentView.findViewById(R.id.btn_receive_file).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(connected) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                            builder.setTitle("Connected user trying to send you a file");
                            builder.setMessage("Do you want to save the file?")
                                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            //Yes button clicked
                                            if (!server_openned){
                                                if(!isExternalStorageWritable()) {
                                                    Toast.makeText(getActivity(), R.string.external_storage,
                                                            Toast.LENGTH_SHORT).show();
                                                }
                                                new FileServerAsyncTask(getActivity()).execute();
                                                server_openned = true;
                                            }
                                        }
                                    })
                                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            //No button clicked
                                        }
                                    })
                                    .show();
                        }
                    }
                });
        mContentView.findViewById(R.id.btn_chat).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                    }
                });
        return mContentView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        String localIP = UtilsIP.getLocalIPAddress();
        // User has picked an image. Transfer it to group owner i.e peer using
        // FileTransferService.
        if (requestCode == CHOOSE_FILE_RESULT_CODE){
            if(resultCode == Activity.RESULT_OK) {
                Uri uri = data.getData();
                String dataPath = getPath(getActivity().getApplicationContext(), uri);
                Log.d(Discover.TAG, "************  File path = " + dataPath);
                Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
                serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
                serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, dataPath);

                //IP_SERVER is the default IP address of the group owner
                if(localIP.equals(IP_NGO)){
                    serviceIntent.putExtra(FileTransferService.EXTRAS_ADDRESS, IP_GO);
                }else if(localIP.equals(IP_GO)){
                    serviceIntent.putExtra(FileTransferService.EXTRAS_ADDRESS, IP_NGO);
                }
                serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, PORT);
                getActivity().startService(serviceIntent);
            }
        }
        else {
            Log.d(Discover.TAG, "there was a problem picking the file");
        }
    }

    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        this.info = info;
        this.getView().setVisibility(View.VISIBLE);
        connected = true;
        // hide the connect button after the connection has been established
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.GONE);
        // Show the options
        mContentView.findViewById(R.id.btn_get_file).setVisibility(View.VISIBLE);
        mContentView.findViewById(R.id.btn_receive_file).setVisibility(View.VISIBLE);
        mContentView.findViewById(R.id.btn_chat).setVisibility(View.VISIBLE);

    }

    /**
     * Updates the UI with device data
     *
     * @param device the device to be displayed
     */
    public void showDetails(WifiP2pDevice device) {
        this.device = device;
        this.getView().setVisibility(View.VISIBLE);
    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    public void resetViews() {
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
        mContentView.findViewById(R.id.btn_get_file).setVisibility(View.GONE);
        mContentView.findViewById(R.id.btn_receive_file).setVisibility(View.GONE);
        mContentView.findViewById(R.id.btn_chat).setVisibility(View.GONE);
        this.getView().setVisibility(View.GONE);
    }

    /**
     * A simple server socket that accepts connection and writes some data on
     * the stream.
     */
    public static class FileServerAsyncTask extends AsyncTask<Void, Void, String> {

        private Context context;
        private ProgressDialog receiving = null;
        private boolean cancel = false;
        /**
         * @param context
         */
        public FileServerAsyncTask(Context context) {
            this.context = context;
            if (receiving != null && receiving.isShowing()) {
                receiving.dismiss();
            }
            receiving = ProgressDialog.show(context, "Press back to cancel", "Waiting to receive files", true,
                    true, new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            cancel = true;
                        }
                    });
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                if(!cancel) {
                    ServerSocket serverSocket = new ServerSocket(8988);
                    Log.d(Discover.TAG, "Server: Socket opened");
                    Socket client = serverSocket.accept();
                    Log.d(Discover.TAG, "Server: connection done");

                    InputStream inputstream = client.getInputStream();
                    BufferedInputStream in = new BufferedInputStream(inputstream);
                    DataInputStream d = new DataInputStream(in);
                    String fileName = Long.toString(System.currentTimeMillis());
                    Log.d(Discover.TAG, "FileName: " + fileName);
                    final File f = new File(Environment.getExternalStorageDirectory().toString() + "/wifime/" + fileName);
                    File dirs = new File(f.getParent());
                    if (!dirs.exists()) {
                        dirs.mkdirs();
                    }
                    f.createNewFile();
                    Log.d(Discover.TAG, "Server: copying files " + f.toString());
                    FileOutputStream ou = new FileOutputStream(f);
                    DataOutputStream o = new DataOutputStream(ou);
                    String newName = d.readUTF();
                    copyFile(d, o);
                    f.renameTo(new File(Environment.getExternalStorageDirectory().toString() + "/wifime/" + newName));
                    serverSocket.close();
                    server_openned = false;
                    return f.getAbsolutePath();
                }else{
                    return null;
                }
            } catch (IOException e) {
                Log.e(Discover.TAG, e.getMessage());
                return null;
            }
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(String result) {
            if(result==null){
                Toast.makeText(context, "No files received",
                        Toast.LENGTH_SHORT).show();
            }
            else if (result != null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("File Copied");
                builder.setMessage("Path: " + result).show();
            }
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
        }

    }

    public static boolean copyFile(DataInputStream inputStream, DataOutputStream out) {
        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            Log.d(Discover.TAG, e.toString());
            return false;
        }
        return true;
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    // The following method are part from an external open source library, aFileChooser (author: paulburke)
    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     *
     */
    @SuppressLint("NewApi")
    public static String getPath(final Context context, final Uri uri) {
        // DocumentProvider
        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }


    // The following method are part from an external open source library, aFileChooser
    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @param selection (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    // The following method are part from an external open source library, aFileChooser
    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    // The following method are part from an external open source library, aFileChooser
    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    // The following method are part from an external open source library, aFileChooser
    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }
}

