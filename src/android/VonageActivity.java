package com.tokbox.cordova;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.res.Configuration;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Rational;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.hrs.patient.R;
import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.Connection;
import com.opentok.android.OpentokError;
import com.opentok.android.Publisher;
import com.opentok.android.Session;
import com.opentok.android.Stream;
import com.opentok.android.Subscriber;

import java.util.HashMap;

public class VonageActivity extends Activity implements Session.ConnectionListener,
    Session.ReconnectionListener, Session.SessionListener{

    private static final String TAG = "OTPlugin";

    private Session session;
    private Publisher publisher;
    private Subscriber subscriber;
    private FrameLayout subscriberViewContainer;
    private FrameLayout publisherViewContainer;
    private ImageButton pictureInPictureButton;
    private Button endButton;

    private LinearLayout imageViewHeader;
    private LinearLayout imageViewFooter;

    private String apiKey;
    private String sessionID;
    private String token;

    @Override
    public void onConnected(Session session) {
        Log.d(TAG, "Session connected");

        if (publisher == null) {
            publisher = new Publisher.Builder(getApplicationContext()).build();
            session.publish(publisher);

            publisherViewContainer.addView(publisher.getView());

            if (publisher.getView() instanceof GLSurfaceView) {
                ((GLSurfaceView) publisher.getView()).setZOrderOnTop(true);
            }
        }
    }

    @Override
    public void onDisconnected(Session session) {
    }

    @Override
    public void onStreamReceived(Session session, Stream stream) {
        Log.d(TAG, "Stream Receieved");
        if (subscriber == null) {
            subscriber = new Subscriber.Builder(getApplicationContext(), stream).build();
            session.subscribe(subscriber);
            subscriber.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
           subscriberViewContainer.addView(subscriber.getView());
        } else {
            Log.d(TAG, "This sample supports just one subscriber");
        }
    }

    @Override
    public void onStreamDropped(Session session, Stream stream) {
        Log.d(TAG, "Stream dropped -->");
        subscriberViewContainer.removeAllViews();
        subscriber = null;
        Log.d(TAG, "End the call now!!! as subscriber has dropped ---->.");
        if(session!=null) {
            session.disconnect();
            finish();
        }
    }

    @Override
    public void onError(Session session, OpentokError opentokError) {
        finishWithMessage("Session error: " + opentokError.getMessage());
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.vonageactivity_main);
        Log.d(TAG, "Vonage activity on create --");
        apiKey = getIntent().getExtras().getString("apiKey");
        sessionID = getIntent().getExtras().getString("sessionID");
        token = getIntent().getExtras().getString("token");
        Log.d(TAG, "api key " + apiKey + " session id " + sessionID + " token " + token);
        subscriberViewContainer = findViewById(R.id.subscriber_container);
        publisherViewContainer = findViewById(R.id.publisher_container);
        pictureInPictureButton = findViewById(R.id.picture_in_picture_button);
        endButton = findViewById(R.id.endCall);
        pictureInPictureButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onClick(View v) {
                PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(9, 16)) // Portrait Aspect Ratio
                    .build();

                enterPictureInPictureMode(params);

            }
        });

        endButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                Log.d(TAG, "End the call");
                if(session!=null) {
                    session.disconnect();
                    finish();
                }
            }
        });

        final ImageButton swapCamera = findViewById(R.id.swapCamera);
        swapCamera.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (publisher == null) {
                    return;
                }

                publisher.cycleCamera();
            }
        });

        imageViewHeader = findViewById(R.id.imageViewHeader);
        imageViewFooter = findViewById((R.id.imageViewFooter));

        final ImageButton toggleAudio = findViewById(R.id.toggleAudio);
        toggleAudio.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (publisher == null) {
                    return;
                }

                if (toggleAudio.isSelected()) {
                    toggleAudio.setImageResource(R.drawable.ic_mic);
                    publisher.setPublishAudio(true);
                } else {
                    toggleAudio.setImageResource(R.drawable.ic_muted);
                    publisher.setPublishAudio(false);
                }
                toggleAudio.setSelected(!toggleAudio.isSelected()); // reverse
            }
        });

       final  ImageButton toggleVideo = findViewById(R.id.toggleVideo);
        toggleVideo.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (publisher == null) {
                    return;
                }

                if (toggleVideo.isSelected()) {
                    toggleVideo.setImageResource(R.drawable.ic_video_on);
                    publisher.setPublishVideo(true);
                } else {
                    toggleVideo.setImageResource(R.drawable.ic_video_off);
                    publisher.setPublishVideo(false);
                }
                toggleVideo.setSelected(!toggleVideo.isSelected());  // reverse

            }
        });

    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);

        if (isInPictureInPictureMode) {
            pictureInPictureButton.setVisibility(View.GONE);
            publisherViewContainer.setVisibility(View.GONE);
            publisher.getView().setVisibility(View.GONE);
            imageViewHeader.setVisibility(View.GONE);
            imageViewFooter.setVisibility(View.GONE);
        } else {
            pictureInPictureButton.setVisibility(View.VISIBLE);
            publisherViewContainer.setVisibility(View.VISIBLE);
            publisher.getView().setVisibility(View.VISIBLE);
            publisher.getView().refreshDrawableState();
            imageViewHeader.setVisibility(View.VISIBLE);
            imageViewFooter.setVisibility(View.VISIBLE);
            if (publisher.getView() instanceof GLSurfaceView) {
                ((GLSurfaceView) publisher.getView()).setZOrderOnTop(true);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "Vonage activity on start --");
        if (session == null) {
            session = new Session.Builder(getApplicationContext(), apiKey, sessionID)
                .build();
        }
        session.setSessionListener(this);
        session.setConnectionListener(this);
        session.connect(token);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onPause() {
        super.onPause();

        if (!isInPictureInPictureMode()) {
            if (session != null) {
                session.onPause();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onResume() {
        super.onResume();

        if (isInPictureInPictureMode()) {
            if (session != null) {
                session.onResume();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (subscriber != null) {
            subscriberViewContainer.removeView(subscriber.getView());
        }

        if (publisher != null) {
            publisherViewContainer.removeView(publisher.getView());
        }
    }

    private void finishWithMessage(String message) {
        Log.e(TAG, message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        this.finish();
    }

    @Override
    public void onConnectionCreated(Session session, Connection connection) {

    }

    @Override
    public void onConnectionDestroyed(Session session, Connection connection) {

    }

    @Override
    public void onReconnecting(Session session) {

    }

    @Override
    public void onReconnected(Session session) {

    }

}
