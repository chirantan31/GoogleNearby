package com.example.v_mahich.googlenearby;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ToggleButton;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.Connections;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

  public static final String TAG = "MainActivity";
  private static final long CONNECTION_TIME_OUT = 10000L;
  private static int[] NETWORK_TYPES = {ConnectivityManager.TYPE_WIFI,
    ConnectivityManager.TYPE_ETHERNET};
  GoogleApiClient mGoogleApiClient;
  ToggleButton discover;
  ToggleButton advertise;
  Button send;

  String role = "Nothing";
  boolean isConnected = false;
  String endpointId = "";
  String fileName = "Algorithms.mp4";

  String receivedFileName = null;
  File receivedFile;
  MainActivity app;


  ConnectionLifecycleCallback mConnectionLifecycleCallback = new ConnectionLifecycleCallback() {
    @Override
    public void onConnectionInitiated(String endPointId, ConnectionInfo connectionInfo) {
      Log.i(TAG, "onConnectionInitiated" + endPointId + ": " + connectionInfo.getEndpointName());
      // Automatically accept the connection on both sides.
      Nearby.Connections.acceptConnection(mGoogleApiClient, endPointId, new PayloadCallback() {
        @Override
        public void onPayloadReceived(String s, Payload payload) {
          Log.i(TAG, "onPayloadReceived \n Payload is: ");
          if (payload.getType() == Payload.Type.BYTES) {
            receivedFileName = new String(payload.asBytes());
          } else if (payload.getType() == Payload.Type.FILE) {
            receivedFile = payload.asFile().asJavaFile();
          }
        }

        @Override
        public void onPayloadTransferUpdate(String s, PayloadTransferUpdate payloadTransferUpdate) {
          Log.i(TAG, "onPayloadTransferUpdate");
          switch (payloadTransferUpdate.getStatus()) {
            case PayloadTransferUpdate.Status.SUCCESS:
              Log.i(TAG, "Payload Transfer Success");
              renameReceivedFile();
              break;
            case PayloadTransferUpdate.Status.FAILURE:
              Log.i(TAG, "Payload Transfer Failure");
              break;
            case PayloadTransferUpdate.Status.IN_PROGRESS:
              Log.i(TAG,
                "Payload Transfer In Progress: " + payloadTransferUpdate.getBytesTransferred() + "/"
                  + payloadTransferUpdate.getTotalBytes());
              break;
          }
        }
      });

    }


    void renameReceivedFile() {
      if (receivedFile != null && receivedFileName != null) {
        receivedFile
          .renameTo(new File(Environment.getExternalStorageDirectory(), receivedFileName));
      }
    }

    @Override
    public void onConnectionResult(String endPointId, ConnectionResolution connectionResolution) {
      switch (connectionResolution.getStatus().getStatusCode()) {
        case ConnectionsStatusCodes.STATUS_OK:
          // We're connected! Can now start sending and receiving data.
          Log.i(TAG, "Status Ok");
          isConnected = true;
          endpointId = endPointId;
          Log.i(TAG, "Connected to Endpoint: " + endPointId);
          WifiManager wm = (WifiManager) app.getApplicationContext().getSystemService(WIFI_SERVICE);
          String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
          Log.i(TAG, "IP Address is: " + ip);
          //Nearby.Connections.sendPayload(mGoogleApiClient, s, new Payload())
          break;
        case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
          // The connection was rejected by one or both sides.
          Log.i(TAG, "Status Connection Rejected");
          break;
        case ConnectionsStatusCodes.STATUS_ERROR:
          Log.i(TAG, "Status Error");
          // The connection broke before it was able to be accepted.
          break;
      }
    }

    @Override
    public void onDisconnected(String s) {
      Log.i(TAG, "onDisconnected");
      isConnected = false;
    }
  };
  private final EndpointDiscoveryCallback mEndpointDiscoveryCallback =
    new EndpointDiscoveryCallback() {
      @Override
      public void onEndpointFound(
        String endpointId, DiscoveredEndpointInfo discoveredEndpointInfo) {
        Log.i(TAG, "endpoint found: " + endpointId + ": EndPointName: " + discoveredEndpointInfo
          .getEndpointName() +
          "  : Service Id: " + discoveredEndpointInfo.getServiceId());
        Nearby.Connections
          .requestConnection(mGoogleApiClient, role, endpointId, mConnectionLifecycleCallback)
          .setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull Status status) {
              if (status.isSuccess()) {
                Log.i(TAG, "onRequest Success");
              } else {

                Log.i(TAG, "onRequest Failure, " + status.getStatusMessage());
              }
            }
          });
      }

      @Override
      public void onEndpointLost(String endpointId) {
        // A previously discovered endpoint has gone away.
        Log.i(TAG, "endpoint lost: " + endpointId);
      }
    };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    app = this;
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    discover = (ToggleButton) findViewById(R.id.discover);
    advertise = (ToggleButton) findViewById(R.id.advertise);

    discover.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if (b) {
          startDiscovery();
        } else {
          stopDiscovery();
        }
      }
    });
    advertise.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if (b) {
          startAdvertising();
        } else {
          stopAdvertising();
        }
      }
    });

    send = (Button) findViewById(R.id.send);
    send.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (isConnected) {
          //Nearby.Connections.sendPayload(mGoogleApiClient, endpointId, Payload.fromBytes(new byte[]{1, 0, 0, 0, 1}));
          try {
            String temp = "test-" + Calendar.getInstance().getTime().toString() + ".mp4";
            File file = new File(Environment.getExternalStorageDirectory(), fileName);
            Payload filePayload = Payload.fromFile(file);
            Nearby.Connections.sendPayload(mGoogleApiClient, endpointId, filePayload);
            Nearby.Connections
              .sendPayload(mGoogleApiClient, endpointId, Payload.fromBytes(temp.getBytes()));
          } catch (FileNotFoundException e) {
            e.printStackTrace();
          }
        }
      }
    });

    mGoogleApiClient = new GoogleApiClient.Builder(this)
      .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
        @Override
        public void onConnected(@Nullable Bundle bundle) {
          Log.i(TAG, "GoogleApiCient Connected");
        }

        @Override
        public void onConnectionSuspended(int i) {
          Log.i(TAG, "GoogleApiCient Suspended");
        }
      })
      .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

          Log.i(TAG, "GoogleApiCient Connection Failed" + connectionResult.getErrorMessage()
            + ": Error Code: " + connectionResult.getErrorCode());
        }
      })
      .addApi(Nearby.CONNECTIONS_API)
      .build();
  }

  @Override
  protected void onStart() {
    super.onStart();
    mGoogleApiClient.connect();
  }

  @Override
  protected void onStop() {
    super.onStop();
    if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
      mGoogleApiClient.disconnect();
    }
  }

  private void startAdvertising() {
        /*if( !isConnectedToNetwork() )
            return;*/
    role = ":Advertiser:";
    discover.setChecked(false);
    Nearby.Connections.startAdvertising(
      mGoogleApiClient,
      role,
      "service", mConnectionLifecycleCallback
      ,
      new AdvertisingOptions(Strategy.P2P_STAR))
      .setResultCallback(new ResultCallback<Connections.StartAdvertisingResult>() {
        @Override
        public void onResult(@NonNull Connections.StartAdvertisingResult startAdvertisingResult) {
          Log.i(TAG, "Advertising Started");
        }
      });
  }

  private boolean isConnectedToNetwork() {
    ConnectivityManager connManager =
      (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    for (int networkType : NETWORK_TYPES) {
      NetworkInfo info = connManager.getNetworkInfo(networkType);
      if (info != null && info.isConnectedOrConnecting()) {
        return true;
      }
    }
    return false;
  }

  private void startDiscovery() {
    advertise.setChecked(false);
    role = ":Discoverer:";
    Nearby.Connections.startDiscovery(
      mGoogleApiClient,
      "service",
      mEndpointDiscoveryCallback,
      new DiscoveryOptions(Strategy.P2P_STAR))
      .setResultCallback(new ResultCallback<Status>() {
        @Override
        public void onResult(@NonNull Status status) {
          if (status.isSuccess()) {
            Log.i(TAG, "Discovery Status Success");
          } else {
            Log.i(TAG, "Discovery Status Failure, " + status.getStatusMessage());
          }

        }
      });
  }

  private void stopDiscovery() {
    Log.i(TAG, "stopDiscovery");
    Nearby.Connections.stopDiscovery(mGoogleApiClient);
    Nearby.Connections.stopAllEndpoints(mGoogleApiClient);
  }

  private void stopAdvertising() {
    Log.i(TAG, "stopAdvertising");
    Nearby.Connections.stopAdvertising(mGoogleApiClient);
    Nearby.Connections.stopAllEndpoints(mGoogleApiClient);
  }
}
