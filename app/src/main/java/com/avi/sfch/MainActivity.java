package com.avi.sfch;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.avi.sfch.model.ChatMessage;
import com.firebase.ui.storage.images.FirebaseImageLoader;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.firebase.database.FirebaseDatabase;

import de.hdodenhof.circleimageview.CircleImageView;
import com.bumptech.glide.Glide;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements  GoogleApiClient.OnConnectionFailedListener{

    private static final String DEFAULT_NAME = "Vel";
    private DatabaseReference mSimpleFirechatDatabaseReference;
    private FirebaseRecyclerAdapter<ChatMessage, FirechatMsgViewHolder>
            mFirebaseAdapter;
    private RecyclerView mMessageRecyclerView;
    private LinearLayoutManager mLinearLayoutManager;
    private ProgressBar mProgressBar;
    private Button mSendButton;
    private EditText mMsgEditText;
    private String mUsername;
    private String mPhotoUrl;

    private GoogleApiClient mGoogleApiClient;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseUser mFirechatUser;
    private FirebaseRemoteConfig mFirebaseRemoteConfig;

    public static class FirechatMsgViewHolder extends RecyclerView.ViewHolder {
        public TextView msgTextView;
        public TextView userTextView;
        public CircleImageView userImageView;

        public FirechatMsgViewHolder(View v) {
            super(v);
            msgTextView = (TextView) itemView.findViewById(R.id.msgTextView);
            userTextView = (TextView) itemView.findViewById(R.id.userTextView);
            userImageView = (CircleImageView) itemView.findViewById(R.id.userImageView);
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = DEFAULT_NAME;

//ini** auth
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */, (GoogleApiClient.OnConnectionFailedListener) this /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API)
                .build();

        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirechatUser = mFirebaseAuth.getCurrentUser();
        if (mFirechatUser == null) {
            startActivity(new Intent(this, AuthorizationActivity.class));
            finish();
            return;
        } else {
            mUsername = mFirechatUser.getDisplayName();
            if (mFirechatUser.getPhotoUrl() != null) {
                mPhotoUrl = mFirechatUser.getPhotoUrl().toString();
            }
        }
//ini--
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageRecyclerView = (RecyclerView) findViewById(R.id.messageRecyclerView);
        mLinearLayoutManager = new LinearLayoutManager(this);
        mLinearLayoutManager.setStackFromEnd(true);
        mMessageRecyclerView.setLayoutManager(mLinearLayoutManager);

        mMsgEditText = (EditText) findViewById(R.id.msgEditText);

        mSimpleFirechatDatabaseReference = FirebaseDatabase.getInstance().getReference();
        mFirebaseAdapter = new FirebaseRecyclerAdapter<ChatMessage,
                FirechatMsgViewHolder>(
                ChatMessage.class,
                R.layout.chat_message,
                FirechatMsgViewHolder.class,
                mSimpleFirechatDatabaseReference.child("messages")) {

            @Override
            protected void populateViewHolder(FirechatMsgViewHolder viewHolder, ChatMessage friendlyMessage, int position) {
                mProgressBar.setVisibility(ProgressBar.INVISIBLE);
                viewHolder.msgTextView.setText(friendlyMessage.getText());
                viewHolder.userTextView.setText(friendlyMessage.getName());
                if (friendlyMessage.getPhotoUrl() == null) {
                    viewHolder.userImageView
                            .setImageDrawable(ContextCompat
                                    .getDrawable(MainActivity.this,
                                            R.drawable.ic_account_circle_black_36dp));
                } else {
                    StorageReference storageRef = FirebaseStorage.getInstance().getReferenceFromUrl("gs://simplefirechat-6ff2e.appspot.com");
                    StorageReference logoFromStorege = storageRef.child("logo.jpg");
                    Glide.with(MainActivity.this)//download from firebase storage
                            .using(new FirebaseImageLoader())
                            .load(logoFromStorege)
                            .into(viewHolder.userImageView);
                }
            }
        };

        mFirebaseAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                int chatMessageCount = mFirebaseAdapter.getItemCount();
                int lastVisiblePosition =
                        mLinearLayoutManager.findLastCompletelyVisibleItemPosition();
                if (lastVisiblePosition == -1 ||
                        (positionStart >= (chatMessageCount - 1) &&
                                lastVisiblePosition == (positionStart - 1))) {
                    mMessageRecyclerView.scrollToPosition(positionStart);
                }
            }
        });

        mMessageRecyclerView.setLayoutManager(mLinearLayoutManager);
        mMessageRecyclerView.setAdapter(mFirebaseAdapter);

        mSendButton = (Button) findViewById(R.id.sendButton);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ChatMessage friendlyMessage = new
                        ChatMessage(mMsgEditText.getText().toString(),
                        mUsername,
                        mPhotoUrl);
                mSimpleFirechatDatabaseReference.child("messages")
                        .push().setValue(friendlyMessage);
                mMsgEditText.setText("");
            }
        });

        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

        FirebaseRemoteConfigSettings firebaseRemoteConfigSettings =
                new FirebaseRemoteConfigSettings.Builder()
                        .setDeveloperModeEnabled(true)
                        .build();

        Map<String, Object> defaultConfigMap = new HashMap<>();
        defaultConfigMap.put("button_name", "Send");

        mFirebaseRemoteConfig.setConfigSettings(firebaseRemoteConfigSettings);
        mFirebaseRemoteConfig.setDefaults(defaultConfigMap);

        fetchConfig();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                mFirebaseAuth.signOut();
                Auth.GoogleSignInApi.signOut(mGoogleApiClient);
                mUsername = DEFAULT_NAME;
                startActivity(new Intent(this, AuthorizationActivity.class));
                return true;
            case R.id.reconfig:
                fetchConfig();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Toast.makeText(this, "Google Play Services error.", Toast.LENGTH_SHORT).show();
    }

    public void fetchConfig() {//change desing
        long cacheExpiration = 3600; // 1 hour in seconds
        // If developer mode is enabled reduce cacheExpiration to 0 so that
        // each fetch goes to the server. This should not be used in release
        // builds.
        if (mFirebaseRemoteConfig.getInfo().getConfigSettings()
                .isDeveloperModeEnabled()) {
            cacheExpiration = 0;
        }
        mFirebaseRemoteConfig.fetch(cacheExpiration)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        // Make the fetched config available via
                        // FirebaseRemoteConfig get<type> calls.
                        mFirebaseRemoteConfig.activateFetched();
                        mSendButton.setText(mFirebaseRemoteConfig.getString("button_name"));
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        mSendButton.setText(mFirebaseRemoteConfig.getString("button_name"));
                    }
                });
    }
}
//https://github.com/firebase/geofire-java