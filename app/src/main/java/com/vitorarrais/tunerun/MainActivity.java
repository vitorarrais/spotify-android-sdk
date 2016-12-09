package com.vitorarrais.tunerun;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerEvent;
import com.spotify.sdk.android.player.Spotify;
import com.spotify.sdk.android.player.SpotifyPlayer;
import com.vitorarrais.tunerun.data.HistoryContentProvider;
import com.vitorarrais.tunerun.data.HistoryTable;
import com.vitorarrais.tunerun.data.model.HistoryModel;
import com.vitorarrais.tunerun.data.model.LocationModel;

import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity implements
        SpotifyPlayer.NotificationCallback, ConnectionStateCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {


    // TODO: Replace with your client ID
    private static final String CLIENT_ID = "fb9e34c2d44a4a28b793c6589a0d816b";
    // TODO: Replace with your redirect URI
    private static final String REDIRECT_URI = "com-vitorarrais-tunerun://callback";

    private Player mPlayer;
    private LocationRequest mLocationRequest;

    // Request code that will be used to verify if the result comes from correct activity
    // Can be any integer
    private static final int REQUEST_CODE = 1337;
    private static final String REQUESTING_LOCATION_UPDATES_KEY = "requesting_location_updates_key";
    private static final String LOCATION_KEY = "location_key";
    private static final String LAST_UPDATED_TIME_STRING_KEY = "last_updated_time_string_key";
    private static final String FIRST_LOCATION_KEY = "first_location_key";
    private static final String LAST_LOCATION_KEY = "last_location_key";
    private static final String TOTAL_DISTANCE_KEY = "total_distance_key";
    private static final String CURRENT_USER_ID = "current_user_id";

    private static final double LIMIT_VELOCITY = 3.8 * 1d; // meters per second
    private static final long SPEED_UPDATE_PERIOD = 5000; // milliseconds

    private static final long ACTIVITY_MIN_DURATION = 60; // seconds
    private static final long ACTIVITY_MIN_DISTANCE = 50; // meters

    public static final int RC_SIGN_IN = 1;

    private Boolean mRequestingLocationUpdates = false;
    private Toolbar mToolbar;

    @BindView(R.id.start_button)
    protected LinearLayout mPlayButton;
    @BindView(R.id.play_text)
    protected TextView mPlayText;
    @BindView(R.id.song_name)
    protected TextView mSongName;
    @BindView(R.id.pause)
    protected Button mPauseButton;
    @BindView(R.id.player_wrapper)
    protected LinearLayout mPlayerWrapper;
    @BindView(R.id.distance_wrapper)
    protected LinearLayout mDistanceWrapper;
    @BindView(R.id.distance_text)
    protected TextView mDistanceText;

    private GoogleApiClient mGoogleApiClient;
    private Location mFirstLocation;
    private Location mLastLocation;
    private Location mCurrentLocation;
    private String mLastUpdateTime;
    private Double mTotalDistance = 0d;
    private Double mDistance = 0d;
    private List<LocationModel> mLocations = new ArrayList<>();
    private long mLastSpeedUpdateTime;
    private Location mLastLocationSpeedUpdate;
    private double mSpeed;
    private long mStartTime = 0;

    private enum SpeedState {
        LOW,
        HIGH
    }

    private SpeedState mSpeedState = SpeedState.LOW;

    // Firebase variables
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mFirebaseAuthStateListener;

    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mHistoryDatabaseReference;
    private ChildEventListener mHistoryChildEventListener;

    private String mCurrentUserId;

    private ProgressDialog mProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setTitle(getResources().getString(R.string.spotify_login_loader_title));
        mProgressDialog.setMessage(getResources().getString(R.string.spotify_login_loader_message));
        mProgressDialog.setCancelable(false);

        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();

        mToolbar = (Toolbar) findViewById(R.id.toolbar); // Attaching the layout to the toolbar object
        setSupportActionBar(mToolbar);

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
            createLocationRequest();
        }
        mGoogleApiClient.connect();

        updateValuesFromBundle(savedInstanceState);


        mFirebaseAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    mCurrentUserId = user.getUid();
                    mHistoryDatabaseReference = mFirebaseDatabase.getReference().child(mCurrentUserId).child("history");

                    // signed in
                } else {
                    mCurrentUserId = null;
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setProviders(
                                            AuthUI.EMAIL_PROVIDER)
                                    .build(),
                            RC_SIGN_IN);
                    // signed out
                }
            }
        };

        mHistoryChildEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        };
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_help) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.help_menu_item)
                    .setMessage(R.string.help_text)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .show();
            return true;
        }

        if (id == R.id.action_history) {
            Intent i = new Intent(this, HistoryActivity.class);
            i.putExtra("userId", mCurrentUserId);
            startActivity(i);
            return true;
        }

        if (id == R.id.action_sign_out) {
            FirebaseAuth.getInstance().signOut();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mCurrentUserId != null) {
            mHistoryDatabaseReference.addChildEventListener(mHistoryChildEventListener);
        }
        mFirebaseAuth.addAuthStateListener(mFirebaseAuthStateListener);
    }

    @Override
    protected void onPause() {
        if (mFirebaseAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mFirebaseAuthStateListener);
        }
        if (mHistoryDatabaseReference != null) {
            mHistoryDatabaseReference.removeEventListener(mHistoryChildEventListener);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mGoogleApiClient.disconnect();
        Spotify.destroyPlayer(this);
        super.onDestroy();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                Config playerConfig = new Config(this, response.getAccessToken(), CLIENT_ID);
                Spotify.getPlayer(playerConfig, this, new SpotifyPlayer.InitializationObserver() {
                    @Override
                    public void onInitialized(SpotifyPlayer spotifyPlayer) {
                        mProgressDialog.dismiss();
                        mPlayer = spotifyPlayer;
                        mPlayer.addConnectionStateCallback(MainActivity.this);
                        mPlayer.addNotificationCallback(MainActivity.this);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.e("MainActivity", "Could not initialize player: " + throwable.getMessage());
                        Toast.makeText(MainActivity.this, "Sorry, Spotify login has failed", Toast.LENGTH_SHORT).show();
                        mProgressDialog.dismiss();

                    }
                });
            }
        }

        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {

            } else if (resultCode == RESULT_CANCELED) {
                finish();
            }
        }
    }

    @Override
    public void onPlaybackEvent(PlayerEvent playerEvent) {
        Log.d("MainActivity", "Playback event received: " + playerEvent.name());
        switch (playerEvent.name()) {
            // Handle event type as necessary
            case "kSpPlaybackNotifyTrackChanged":
                updatePlayerUi();
                break;
            case "kSpPlaybackNotifyPlay":
                updatePlayerUi();
                break;
            default:
                break;
        }
    }

    private void updatePlayerUi() {
        if (mPlayer != null) {
            if (mPlayer.getMetadata() != null) {
                if (mPlayer.getMetadata().currentTrack != null) {
                    String artist = mPlayer.getMetadata().currentTrack.artistName;
                    String song = mPlayer.getMetadata().currentTrack.name;
                    String result = artist + " - " + song;
                    mSongName.setText(result);
                }
            } else {
                mSongName.setText(" ");
            }
        }
    }

    @Override
    public void onPlaybackError(Error error) {
        Toast.makeText(MainActivity.this, "Sorry, an error has ocurried", Toast.LENGTH_SHORT).show();
        Log.d("MainActivity", "Playback error received: " + error.name());
        switch (error) {
            // Handle error type as necessary
            default:
                break;
        }
    }

    @Override
    public void onLoggedIn() {
        Log.d("MainActivity", "User logged in");
        Toast.makeText(this, "User logged in", Toast.LENGTH_SHORT).show();
//        mPlayer.playUri(null, "spotify:track:2TpxZ7JUBn3uw46aR7qd6V", 0, 0);
    }

    @Override
    public void onLoggedOut() {
        Log.d("MainActivity", "User logged out");
    }

    @Override
    public void onLoginFailed(int i) {
        Toast.makeText(MainActivity.this, "Sorry, an error has ocurried", Toast.LENGTH_SHORT).show();
        Log.d("MainActivity", "Login failed");
    }

    @Override
    public void onTemporaryError() {
        Toast.makeText(MainActivity.this, "Sorry, an error has ocurried", Toast.LENGTH_SHORT).show();
        Log.d("MainActivity", "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d("MainActivity", "Received connection message: " + message);
    }

    @OnClick(R.id.start_button)
    public void play(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

        if (mPlayer == null) {
            // user is not logged in spotify
            mProgressDialog.show();
            requestSpotifyLogin();
        } else {

            Boolean isPlaying = mPlayer.getPlaybackState().isPlaying;

            if (!isPlaying) {

                mPlayer.playUri(null, "spotify:user:spotifybrazilian:playlist:7xchkWJJELTYDBzffZygz0", 0, 0);


                mPlayText.setText(getResources().getString(R.string.finish_string));
                mPlayerWrapper.setVisibility(View.VISIBLE);
                mPlayButton.setBackgroundColor(getResources().getColor(R.color.red));
                mDistanceWrapper.setVisibility(View.VISIBLE);

                startLocationUpdates();
            } else {
                // finish tracking

                mPlayer.pause(new Player.OperationCallback() {
                    @Override
                    public void onSuccess() {
                        //Toast.makeText(MainActivity.this, "Pause success", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(Error error) {
                        Toast.makeText(MainActivity.this, "Sorry, an error has ocurried", Toast.LENGTH_SHORT).show();
                    }
                });

                mPlayerWrapper.setVisibility(View.GONE);
                mPlayText.setText(getResources().getString(R.string.start_string));
                mPlayButton.setBackgroundColor(getResources().getColor(R.color.green));
                mDistanceWrapper.setVisibility(View.GONE);

                SimpleDateFormat spf = new SimpleDateFormat("MM/dd/yy");

                HistoryModel model = new HistoryModel();
                model.setDate(spf.format(new Date()));
                model.setDistance(formatDistance(mTotalDistance));
                model.setPath(mLocations);

                // add to local database
                ContentValues values = new ContentValues();
                values.put(HistoryTable.COLUMN_DATE, model.getDate());
                values.put(HistoryTable.COLUMN_DISTANCE, model.getDistance());

                Uri uri = getContentResolver().insert(HistoryContentProvider.CONTENT_URI, values);
                long id = ContentUris.parseId(uri);
                model.set_id(id);

                // add to firebase database
                mHistoryDatabaseReference.child(String.valueOf(id)).setValue(model);

                // reset global variables
                mSpeedState = SpeedState.LOW;
                mDistance = 0d;
                mSpeed = 0d;
                mStartTime = 0;
                mTotalDistance = 0d;
                mFirstLocation = null;
                mLocations.clear();
                stopLocationUpdates();
            }
        }
    }

    private void requestSpotifyLogin() {
        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-private", "streaming"});
        AuthenticationRequest request = builder.build();

        AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
    }


    @OnClick(R.id.pause)
    public void pause(View v) {
        if (mPlayer != null && mPlayer.getPlaybackState().isPlaying) {
            mPlayer.pause(new Player.OperationCallback() {
                @Override
                public void onSuccess() {
                    //Toast.makeText(MainActivity.this, "Paused", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(Error error) {
                    Toast.makeText(MainActivity.this, "Sorry, an error has ocurried", Toast.LENGTH_SHORT).show();
                }
            });
            mPauseButton.setText("Play");
        } else if (mPlayer != null && mPlayer.getPlaybackState().isActiveDevice) {
            mPlayer.resume(new Player.OperationCallback() {
                @Override
                public void onSuccess() {
                    //Toast.makeText(MainActivity.this, "Resumed", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(Error error) {
                    Toast.makeText(MainActivity.this, "Sorry, an error has ocurried", Toast.LENGTH_SHORT).show();
                }
            });
            mPauseButton.setText("Pause");
        }
    }

    @OnClick(R.id.next)
    public void next(View v) {
        if (mPlayer != null && mPlayer.getPlaybackState().isPlaying) {
            mPlayer.skipToNext(new Player.OperationCallback() {
                @Override
                public void onSuccess() {
                    //Toast.makeText(MainActivity.this, "Skip to next", Toast.LENGTH_SHORT).show();

                }

                @Override
                public void onError(Error error) {
                    Toast.makeText(MainActivity.this, "Sorry, an error has ocurried", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @OnClick(R.id.prev)
    public void prev(View v) {
        if (mPlayer != null && mPlayer.getPlaybackState().isPlaying) {
            mPlayer.skipToPrevious(new Player.OperationCallback() {
                @Override
                public void onSuccess() {
                    //Toast.makeText(MainActivity.this, "Skip to previous", Toast.LENGTH_SHORT).show();

                }

                @Override
                public void onError(Error error) {
                    Toast.makeText(MainActivity.this, "Sorry, an error has ocurried", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }


    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    protected void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            mRequestingLocationUpdates = false;
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mRequestingLocationUpdates = true;
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

//        if (mRequestingLocationUpdates) {
//            startLocationUpdates();
//        }
    }

    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean(REQUESTING_LOCATION_UPDATES_KEY,
                mRequestingLocationUpdates);
        savedInstanceState.putString(CURRENT_USER_ID, mCurrentUserId);
        savedInstanceState.putParcelable(FIRST_LOCATION_KEY, mFirstLocation);
        savedInstanceState.putParcelable(LOCATION_KEY, mCurrentLocation);
        savedInstanceState.putString(LAST_UPDATED_TIME_STRING_KEY, mLastUpdateTime);
        savedInstanceState.putParcelable(LAST_LOCATION_KEY, mLastLocation);
        savedInstanceState.putDouble(TOTAL_DISTANCE_KEY, mTotalDistance);
        super.onSaveInstanceState(savedInstanceState);
    }

    private void updateValuesFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            // Update the value of mRequestingLocationUpdates from the Bundle, and
            // make sure that the Start Updates and Stop Updates buttons are
            // correctly enabled or disabled.
            if (savedInstanceState.keySet().contains(REQUESTING_LOCATION_UPDATES_KEY)) {
                mRequestingLocationUpdates = savedInstanceState.getBoolean(
                        REQUESTING_LOCATION_UPDATES_KEY);
                //setButtonsEnabledState();
            }

            if (savedInstanceState.keySet().contains(CURRENT_USER_ID)) {
                mCurrentUserId = savedInstanceState.getString(CURRENT_USER_ID);
            }

            if (savedInstanceState.keySet().contains(LAST_LOCATION_KEY)) {
                mLastLocation = savedInstanceState.getParcelable(LAST_LOCATION_KEY);
            }

            if (savedInstanceState.keySet().contains(TOTAL_DISTANCE_KEY)) {
                mTotalDistance = savedInstanceState.getDouble(TOTAL_DISTANCE_KEY);
            }

            if (savedInstanceState.keySet().contains(FIRST_LOCATION_KEY)) {
                mFirstLocation = savedInstanceState.getParcelable(FIRST_LOCATION_KEY);
            }

            // Update the value of mCurrentLocation from the Bundle and update the
            // UI to show the correct latitude and longitude.
            if (savedInstanceState.keySet().contains(LOCATION_KEY)) {
                // Since LOCATION_KEY was found in the Bundle, we can be sure that
                // mCurrentLocationis not null.
                mCurrentLocation = savedInstanceState.getParcelable(LOCATION_KEY);
            }

            // Update the value of mLastUpdateTime from the Bundle and update the UI.
            if (savedInstanceState.keySet().contains(LAST_UPDATED_TIME_STRING_KEY)) {
                mLastUpdateTime = savedInstanceState.getString(
                        LAST_UPDATED_TIME_STRING_KEY);
            }

            updateDistanceUi();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Toast.makeText(MainActivity.this, "Sorry, an error has ocurried", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLocationChanged(Location location) {
        if (mFirstLocation == null) {
            mFirstLocation = location;
            mLastLocationSpeedUpdate = mFirstLocation;
            mLastSpeedUpdateTime = Calendar.getInstance().getTimeInMillis();
        }

        mLastLocation = mCurrentLocation;
        mCurrentLocation = location;

        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());

        mLocations.add(new LocationModel(location.getLatitude(), location.getLongitude()));

        Double distance = 0d;
        if (mLastLocation != null && mCurrentLocation != null) {
            distance = (double) mLastLocation.distanceTo(mCurrentLocation);
        }

        mDistance += distance;
        mTotalDistance = mDistance * 0.001d;

        // measure speed every 5 seconds
        long now = Calendar.getInstance().getTimeInMillis();
        long difference = now - mLastSpeedUpdateTime;

        if (difference > SPEED_UPDATE_PERIOD) {
            mLastSpeedUpdateTime = now;
            long deltaDistance = (long) mLastLocationSpeedUpdate.distanceTo(mCurrentLocation);
            long deltaTime = difference;

            mSpeed = deltaDistance / (double) (deltaTime * 1000d);
            Log.d(MainActivity.class.getSimpleName(), "Speed: " + mSpeed + " State: " + mSpeedState);


            if (mSpeed > LIMIT_VELOCITY && mSpeedState == SpeedState.LOW) {
                mSpeedState = SpeedState.HIGH;
                Log.d(MainActivity.class.getSimpleName(), "turned to HIGH velocity");
                mPlayer.playUri(null, "spotify:user:sonymusicentertainment:playlist:5GiPRvTccToqwOzkoAcDrY", 0, 0);
            } else if (mSpeed < LIMIT_VELOCITY && mSpeedState == SpeedState.HIGH) {
                mSpeedState = SpeedState.LOW;
                mPlayer.playUri(null, "spotify:user:spotifybrazilian:playlist:7xchkWJJELTYDBzffZygz0", 0, 0);
                Log.d(MainActivity.class.getSimpleName(), "turned to LOW velocity");
            }
        }

        updateDistanceUi();
    }

    private void updateDistanceUi() {
        long now = Calendar.getInstance().getTimeInMillis();
        long activityDuration = (mStartTime - now) * 1000;

        // path greater than 50 meters or duration over 1 min
        boolean updateUi = mFirstLocation.distanceTo(mCurrentLocation) > ACTIVITY_MIN_DISTANCE
                || activityDuration > ACTIVITY_MIN_DURATION;

        if (mFirstLocation != null && updateUi) {
            if (mTotalDistance < 0.1) {
                mDistanceText.setText(R.string.distance_placeholder_text);
            } else {
                mDistanceText.setText(formatDistance(mTotalDistance));
            }
        }
    }

    private String formatDistance(double distance) {
        DecimalFormat df = new DecimalFormat("#.#");
        df.setRoundingMode(RoundingMode.FLOOR);
        String distanceString = df.format(distance).replace(",", ".") + " km";
        return distanceString;
    }


    protected void stopLocationUpdates() {
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(
                    mGoogleApiClient, this);
        }
    }
}