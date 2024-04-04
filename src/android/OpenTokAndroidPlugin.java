package com.tokbox.cordova;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.Manifest;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.content.pm.PackageManager;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.RequiresApi;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import com.opentok.android.AudioDeviceManager;
import com.opentok.android.Connection;
import com.opentok.android.OpentokError;
import com.opentok.android.Publisher;
import com.opentok.android.PublisherKit;
import com.opentok.android.Session;
import com.opentok.android.Stream;
import com.opentok.android.Stream.StreamVideoType;
import com.opentok.android.Subscriber;
import com.opentok.android.SubscriberKit;
import com.opentok.android.BaseVideoRenderer;

import timber.log.Timber;

public class OpenTokAndroidPlugin extends CordovaPlugin
        implements  Session.SessionListener,
                    Session.ConnectionListener,
                    Session.ReconnectionListener,
                    Session.ArchiveListener,
                    Session.SignalListener,
                    PublisherKit.PublisherListener,
                    Session.StreamPropertiesListener {
    // flag to toggle between using the webview session implementation and
    // the native VonageActivity.java implementation.
    // Set this back to false if we decide to go back to using native at some point.
    private static final boolean USE_WEBVIEW_SESSION = true;
    private static final JSONObject JSON_OBJECT_EMPTY = new JSONObject();

    // Enums as strings for webview translation
    private static final String VIDEO_TYPE_CUSTOM = "custom";
    private static final String VIDEO_TYPE_CAMERA = "camera";
    private static final String VIDEO_TYPE_SCREEN = "screen";
    private static final String REASON_CLIENT_DISCONNECTED = "clientDisconnected";
    private static final String STATUS_CONNECTED = "connected";

    // Top-level keys for event objects
    private static final String KEY_SCOPE = "scope"; // used by `emitSharedJsEvent()`
    private static final String KEY_TYPE = "type"; // used by `emitSharedJsEvent()`
    private static final String KEY_EVENT_TYPE = "eventType"; // used by `triggerJSEvent()`
    private static final String KEY_DATA = "data"; // used by both

    // Scope keys for event objects
    private static final String SCOPE_TYPE_SESSION_EVENTS = "sessionEvents";
    private static final String SCOPE_TYPE_PUBLISHER_EVENTS = "publisherEvents";
    private static final String SCOPE_TYPE_SUBSCRIBER_EVENTS = "subscriberEvents";

    // Type keys for event objects
    private static final String EVENT_TYPE_SESSION_CONNECTED = "sessionConnected";
    private static final String EVENT_TYPE_SESSION_DISCONNECTED = "sessionDisconnected";
    private static final String EVENT_TYPE_SESSION_RECONNECTED = "sessionReconnected";
    private static final String EVENT_TYPE_SESSION_RECONNECTING = "sessionReconnecting";
    private static final String EVENT_TYPE_SESSION_ERROR = "sessionError";
    private static final String EVENT_TYPE_CONNECTION_CREATED = "connectionCreated";
    private static final String EVENT_TYPE_CONNECTION_DESTROYED = "connectionDestroyed";
    private static final String EVENT_TYPE_CONNECTED = "connected";
    private static final String EVENT_TYPE_DISCONNECTED = "disconnected";
    private static final String EVENT_TYPE_SUBSCRIBED_TO_STREAM = "subscribedToStream";
    private static final String EVENT_TYPE_VIDEO_DATA_RECEIVED = "videoDataReceived";
    private static final String EVENT_TYPE_AUDIO_LEVEL_UPDATED = "audioLevelUpdated";
    private static final String EVENT_TYPE_VIDEO_DISABLED = "videoDisabled";
    private static final String EVENT_TYPE_VIDEO_DISABLE_WARNING = "videoDisableWarning";
    private static final String EVENT_TYPE_VIDEO_DISABLE_WARNING_LIFTED = "videoDisableWarningLifted";
    private static final String EVENT_TYPE_VIDEO_ENABLED = "videoEnabled";
    private static final String EVENT_TYPE_SIGNAL_RECEIVED = "signalReceived";
    private static final String EVENT_TYPE_ARCHIVE_STARTED = "archiveStarted";
    private static final String EVENT_TYPE_ARCHIVE_STOPPED = "archiveStopped";
    private static final String EVENT_TYPE_STREAM_PROPERTY_CHANGED = "streamPropertyChanged";
    private static final String EVENT_TYPE_STREAM_CREATED = "streamCreated";
    private static final String EVENT_TYPE_STREAM_DESTROYED = "streamDestroyed";

    // Data keys for event objects
    private static final String DATA_KEY_CONNECTION = "connection";
    private static final String DATA_KEY_MESSAGE = "message";
    private static final String DATA_KEY_CONNECTION_ID = "connectionId";
    private static final String DATA_KEY_ERROR_DOMAIN = "errorDomain";
    private static final String DATA_KEY_ERROR_NAME = "errorName";
    private static final String DATA_KEY_ERROR_CODE = "errorCode";
    private static final String DATA_KEY_SYSTEM_MESSAGE = "systemMessage";
    private static final String DATA_KEY_CREATION_TIME = "creationTime";
    private static final String DATA_KEY_FPS = "fps";
    private static final String DATA_KEY_OPENTOK_ERROR = "opentokError";
    private static final String DATA_KEY_WIDTH = "width";
    private static final String DATA_KEY_HEIGHT = "height";
    private static final String DATA_KEY_HAS_AUDIO = "hasAudio";
    private static final String DATA_KEY_HAS_VIDEO = "hasVideo";
    private static final String DATA_KEY_VIDEO_DIMENSIONS = "videoDimensions";
    private static final String DATA_KEY_VIDEO_TYPE = "videoType";
    private static final String DATA_KEY_NAME = "name";
    private static final String DATA_KEY_STREAM_ID = "streamId";
    private static final String DATA_KEY_STATUS = "status";
    private static final String DATA_KEY_REASON = "reason";
    private static final String DATA_KEY_AUDIO_LEVEL = "audioLevel";
    private static final String DATA_KEY_CHANGED_PROPERTY = "changedProperty";
    private static final String DATA_KEY_NEW_VALUE = "newValue";
    private static final String DATA_KEY_OLD_VALUE = "oldValue";
    private static final String DATA_KEY_STREAM = "stream";
    private static final String DATA_KEY_ID = "id";

    // Cordova actions
    private static final String ACTION_INIT_PUBLISHER = "initPublisher";
    private static final String ACTION_DESTROY_PUBLISHER = "destroyPublisher";
    private static final String ACTION_INIT_SESSION = "initSession";
    private static final String ACTION_SET_CAMERA_POSITION = "setCameraPosition";
    private static final String ACTION_PUBLISH_AUDIO = "publishAudio";
    private static final String ACTION_PUBLISH_VIDEO = "publishVideo";
    private static final String ACTION_ADD_EVENT = "addEvent";
    private static final String ACTION_CONNECT = "connect";
    private static final String ACTION_DISCONNECT = "disconnect";
    private static final String ACTION_PUBLISH = "publish";
    private static final String ACTION_SIGNAL = "signal";
    private static final String ACTION_UNPUBLISH = "unpublish";
    private static final String ACTION_UNSUBSCRIBE = "unsubscribe";
    private static final String ACTION_SUBSCRIBE = "subscribe";
    private static final String ACTION_SUBSCRIBE_TO_AUDIO = "subscribeToAudio";
    private static final String ACTION_SUBSCRIBE_TO_VIDEO = "subscribeToVideo";
    private static final String ACTION_UPDATE_VIEW = "updateView";
    private static final String ACTION_GET_IMG_DATA = "getImgData";
    private static final String ACTION_EXCEPTION_HANDLER = "exceptionHandler";
    private static final String ACTION_GET_OVERLAY_STATE = "getOverlayState";
    private static final String ACTION_SET_MINIMIZED = "setMinimized";
    private static final String ACTION_SET_SHARED_EVENT_LISTENER = "setSharedEventListener";

    private String sessionId;
    private String apiKey;
    protected Session mSession;
    public boolean sessionConnected;
    public boolean publishCalled; // we need this because creating publisher before sessionConnected = crash
    public RunnablePublisher myPublisher;
    public HashMap<String, CallbackContext> myEventListeners;
    public HashMap<String, Connection> connectionCollection;
    public HashMap<String, Stream> streamCollection;
    public HashMap<String, RunnableSubscriber> subscriberCollection;

    // Old tracking values.
    private HashMap<String, Boolean> streamHasAudio;
    private HashMap<String, Boolean> streamHasVideo;
    private HashMap<String, JSONObject> streamVideoDimensions;

    private static OpenTokAndroidPlugin mInstance = null;
    private static JSONObject viewList = new JSONObject();
    public static final String[] perms = {Manifest.permission.INTERNET, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
    public CallbackContext permissionsCallback = null;
    private CallbackContext sharedEventContext = null;
    private boolean minimized = false;

    public static OpenTokAndroidPlugin getInstance() {
        return mInstance;
    }

    public class RunnableUpdateViews implements Runnable {
        public JSONArray mProperty;
        public View mView;
        public ArrayList<RunnableUpdateViews> allStreamViews;

        public class CustomComparator implements Comparator<RunnableUpdateViews> {
            @Override
            public int compare(RunnableUpdateViews object1, RunnableUpdateViews object2) {
                return object1.getZIndex() - object2.getZIndex();
            }
        }

        public void updateZIndices() {
            allStreamViews = new ArrayList<RunnableUpdateViews>();
            for (Map.Entry<String, RunnableSubscriber> entry : subscriberCollection.entrySet()) {
                allStreamViews.add(entry.getValue());
            }
            if (myPublisher != null) {
                allStreamViews.add(myPublisher);
            }

            // Sort is still needed, because we need to sort from negative to positive for the z translation.
            Collections.sort(allStreamViews, new CustomComparator());

            for (int i = 0; i < allStreamViews.size(); i++) {
                RunnableUpdateViews viewContainer  = allStreamViews.get(i);
                // Set depth location of camera view based on CSS z-index.
                // See: https://developer.android.com/reference/android/view/View.html#setTranslationZ(float)
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    viewContainer.mView.setTranslationZ(viewContainer.getZIndex());
                }
                // If the zIndex is 0(default) bring the view to the top, last one wins.
                // See: https://github.com/saghul/cordova-plugin-iosrtc/blob/5b6a180b324c8c9bac533fa481a457b74183c740/src/PluginMediaStreamRenderer.swift#L191
                if(i == allStreamViews.size() - 1) {
                    if (viewContainer.mView instanceof GLSurfaceView) {
                        ((GLSurfaceView) viewContainer.mView).setZOrderOnTop(true);
                    } else {
                        viewContainer.mView.bringToFront();
                    }
                }
            }
        }

        public int getZIndex() {
            try {
                return mProperty.getInt(5);
            } catch (Exception e) {
                return 0;
            }
        }

        @SuppressLint("NewApi")
        @Override
        public void run() {
            try {
                Timber.i("updating view in ui runnable" + mProperty.toString());
                Timber.i("updating view in ui runnable " + mView.toString());

                float widthRatio, heightRatio;

                // Ratios are index 6 & 7 on TB.updateViews, 8 & 9 on subscribe event, and 9 & 10 on TB.initPublisher
                int ratioIndex;
                if (mProperty.get(6) instanceof Number) {
                    ratioIndex = 6;
                } else if (mProperty.get(8) instanceof Number) {
                    ratioIndex = 8;
                } else {
                    ratioIndex = 9;
                }

                DisplayMetrics metrics = new DisplayMetrics();
                cordova.getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);

                widthRatio = (float) mProperty.getDouble(ratioIndex) * metrics.density;
                heightRatio = (float) mProperty.getDouble(ratioIndex + 1) * metrics.density;
                mView.setY(mProperty.getInt(1) * heightRatio);
                mView.setX(mProperty.getInt(2) * widthRatio);
                ViewGroup.LayoutParams params = mView.getLayoutParams();
                params.height = (int) (mProperty.getInt(4) * heightRatio);
                params.width = (int) (mProperty.getInt(3) * widthRatio);
                mView.setLayoutParams(params);
                updateZIndices();
            } catch (Exception e) {
                Timber.i("error when trying to retrieve properties while resizing properties");
            }
        }
    }

    public class RunnablePublisher extends RunnableUpdateViews implements
            PublisherKit.PublisherListener, Publisher.CameraListener,
            PublisherKit.AudioLevelListener {
        /* properties: [name, position.top, position.left, width, height, zIndex,
           publishAudio, publishVideo, cameraName, ratios.widthRatio, ratios.heightRatio,
           audioFallbackEnabled, audioBitrate, audioSource, videoSource, frameRate, cameraResolution]
       */
        public Publisher mPublisher;

        public RunnablePublisher(JSONArray args) {
            this.mProperty = args;

            // prevent dialog box from showing because it causes crash
            SharedPreferences prefs = cordova.getActivity().getApplicationContext().getSharedPreferences("permissions",
                    Context.MODE_PRIVATE);
            Editor edit = prefs.edit();
            edit.clear();
            edit.putBoolean("opentok.publisher.accepted", true);
            edit.commit();


            boolean audioFallbackEnabled = true;
            boolean videoTrack = true;
            boolean audioTrack = true;
            boolean publishAudio = true;
            boolean publishVideo = true;
            int audioBitrate = 40000;
            String publisherName = "Android-Cordova-Publisher";
            String frameRate = "FPS_15";
            String resolution = "MEDIUM";
            String cameraName = "front";
            try {
                publisherName = this.mProperty.getString(0);
                audioBitrate = this.mProperty.getInt(12);
                frameRate = "FPS_" + this.mProperty.getString(15);
                videoTrack = this.mProperty.getString(14).equals("true");
                audioTrack = this.mProperty.getString(13).equals("true");
                audioFallbackEnabled = this.mProperty.getString(11).equals("true");
                publishVideo = this.mProperty.getString(7).equals("true");
                publishAudio = this.mProperty.getString(6).equals("true");
                cameraName = this.mProperty.getString(8).equals("back") ? "back" : cameraName;
                if (compareStrings(this.mProperty.getString(16), "1280x720")) {
                    resolution = "HIGH";
                }
                if (compareStrings(this.mProperty.getString(16), "320x240") || compareStrings(this.mProperty.getString(16), "352x288")) {
                    resolution = "LOW";
                }
                Timber.i("publisher properties sanitized");
            } catch (Exception e) {
                Timber.i("Unable to set publisher properties");
            }
            mPublisher = new Publisher.Builder(cordova.getActivity().getApplicationContext())
                    .videoTrack(videoTrack)
                    .audioTrack(audioTrack)
                    .name(publisherName)
                    .audioBitrate(audioBitrate)
                    .frameRate(Publisher.CameraCaptureFrameRate.valueOf(frameRate))
                    .resolution(Publisher.CameraCaptureResolution.valueOf(resolution))
                    .renderer(new OpenTokCustomVideoRenderer(cordova.getActivity().getApplicationContext()))
                    .build();
            mPublisher.setCameraListener(this);
            mPublisher.setPublisherListener(this);
            mPublisher.setAudioLevelListener(this);
            mPublisher.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
            mPublisher.setAudioFallbackEnabled(audioFallbackEnabled);
            mPublisher.setPublishVideo(publishVideo);
            mPublisher.setPublishAudio(publishAudio);

            if (cameraName.equals("back")) {
                mPublisher.cycleCamera();
            }
        }

        public void setPropertyFromArray(JSONArray args) {
            this.mProperty = args;
        }

        public void startPublishing() {
            mSession.publish(mPublisher);
            cordova.getActivity().runOnUiThread(this);
        }

        public void stopPublishing() {
            ViewGroup parent = (ViewGroup) webView.getView().getParent();
            parent.removeView(this.mView);
            if(this.mPublisher != null){
                try {
                    mSession.unpublish(this.mPublisher);
                } catch(Exception e) {
                    Timber.i("Could not unpublish Publisher");
                }
            }
        }

        public void destroyPublisher() {
            cordova.getActivity().runOnUiThread(
                new Runnable() {
                    public void run() {
                        ViewGroup parent = (ViewGroup) webView.getView().getParent();
                        parent.removeView(mView);
                    }
                });

            if (this.mPublisher != null) {
                this.mPublisher.setPublishVideo(false);
                this.mPublisher = null;
            }
        }

        public void getImgData(CallbackContext callbackContext) {
            ((OpenTokCustomVideoRenderer) mPublisher.getRenderer()).getSnapshot(callbackContext);
        }

        public void run() {
            if(this.mView == null) {
                this.mView = mPublisher.getView();
                ((ViewGroup) webView.getView().getParent()).addView(this.mView);

                // Set depth location of camera view based on CSS z-index.
                // See: https://developer.android.com/reference/android/view/View.html#setTranslationZ(float)
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    this.mView.setTranslationZ(this.getZIndex());
                }
            }
            super.run();
        }

        // event listeners
        @Override
        public void onError(PublisherKit arg0, OpentokError arg1) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onStreamCreated(PublisherKit arg0, Stream arg1) {
            Timber.i("publisher stream received");
            streamCollection.put(arg1.getStreamId(), arg1);

            streamHasAudio.put(arg1.getStreamId(), arg1.hasAudio());
            streamHasVideo.put(arg1.getStreamId(), arg1.hasVideo());
            JSONObject videoDimensions = new JSONObject();
            try {
                videoDimensions.put(DATA_KEY_WIDTH, arg1.getVideoWidth());
                videoDimensions.put(DATA_KEY_HEIGHT, arg1.getVideoHeight());
            } catch (JSONException e) {
            }
            streamVideoDimensions.put(arg1.getStreamId(), videoDimensions);

            triggerStreamEvent(arg1, SCOPE_TYPE_PUBLISHER_EVENTS, EVENT_TYPE_STREAM_CREATED);
        }

        @Override
        public void onStreamDestroyed(PublisherKit arg0, Stream arg1) {
            streamCollection.remove(arg1.getStreamId());

            streamHasAudio.remove(arg1.getStreamId());
            streamHasVideo.remove(arg1.getStreamId());
            streamVideoDimensions.remove(arg1.getStreamId());

            triggerStreamEvent(arg1, SCOPE_TYPE_PUBLISHER_EVENTS, EVENT_TYPE_STREAM_DESTROYED);
        }

        @Override
        public void onCameraChanged(Publisher arg0, int arg1) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onCameraError(Publisher arg0, OpentokError arg1) {
            // TODO Auto-generated method stub

        }

        // audioLevelListener
        public void onAudioLevelUpdated(PublisherKit publisher, float audioLevel) {
            JSONObject data = new JSONObject();
            try {
                data.put(DATA_KEY_AUDIO_LEVEL, audioLevel);
                emitCommonEvent(SCOPE_TYPE_PUBLISHER_EVENTS, EVENT_TYPE_AUDIO_LEVEL_UPDATED, data);
            } catch (JSONException e) {
            }
        }
    }

    public class RunnableSubscriber extends RunnableUpdateViews implements
            SubscriberKit.SubscriberListener, SubscriberKit.VideoListener,
            SubscriberKit.AudioLevelListener {
        //  property contains: [stream.streamId, position.top, position.left, width, height, subscribeToVideo, zIndex] )
        public Subscriber mSubscriber;
        public Stream mStream;

        public RunnableSubscriber(JSONArray args, Stream stream) {
            this.mProperty = args;
            mStream = stream;

            logMessage("NEW SUBSCRIBER BEING CREATED");
            mSubscriber = new Subscriber.Builder(cordova.getActivity().getApplicationContext(), mStream)
                    .renderer(new OpenTokCustomVideoRenderer(cordova.getActivity().getApplicationContext()))
                    .build();
            Timber.d("When New Subscriber Created Get audio volume--> " + mSubscriber.getAudioVolume());
            mSubscriber.setAudioVolume(100);
            Timber.d("After setting -->When New Subscriber Created Get audio volume--> " + mSubscriber.getAudioVolume());
            mSubscriber.setVideoListener(this);
            mSubscriber.setSubscriberListener(this);
            mSubscriber.setAudioLevelListener(this);
            mSubscriber.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
            mSession.subscribe(mSubscriber);
            cordova.getActivity().runOnUiThread(this);
        }

        public void setPropertyFromArray(JSONArray args) {
            this.mProperty = args;
        }

        public void removeStreamView() {
            cordova.getActivity().runOnUiThread(
                new Runnable() {
                    public void run() {
                        ViewGroup parent = (ViewGroup) webView.getView().getParent();
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                            // removeView() will crash the app when a call ends on an Android version less than 9.
                            parent.removeViewInLayout(mView);
                        } else {
                            parent.removeView(mView);
                        }
                    }
                });

            if(mSubscriber != null) {
                try {
                    mSession.unsubscribe(mSubscriber);
                    mSubscriber.destroy();
                } catch(Exception e) {
                    Timber.i("Could not unsubscribe Subscriber");
                }
            }
        }

        public void run() {
            if(this.mView == null) {
                this.mView = mSubscriber.getView();
                ((ViewGroup) webView.getView().getParent()).addView(this.mView);

                // Set depth location of camera view based on CSS z-index.
                // See: https://developer.android.com/reference/android/view/View.html#setTranslationZ(float)
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    this.mView.setTranslationZ(this.getZIndex());
                }
                Timber.i("subscriber view is added to parent view!");
            }
            super.run();
        }

        public void getImgData(CallbackContext callbackContext) {
            ((OpenTokCustomVideoRenderer) mSubscriber.getRenderer()).getSnapshot(callbackContext);
        }

        @Override
        public void onConnected(SubscriberKit arg0) {
            // TODO Auto-generated method stub
            JSONObject eventData = new JSONObject();
            String streamId = arg0.getStream().getStreamId();
            try {
                eventData.put(DATA_KEY_STREAM_ID, streamId);
                emitCommonEvent(SCOPE_TYPE_SUBSCRIBER_EVENTS, EVENT_TYPE_CONNECTED, eventData);
                triggerJSEvent(SCOPE_TYPE_SESSION_EVENTS, EVENT_TYPE_SUBSCRIBED_TO_STREAM, eventData); // Backwards compatibility
            } catch (JSONException e) {
                Timber.e("JSONException" + e.getMessage());
            }
            Timber.i("subscriber" + streamId + " is connected");
            this.run();
        }

        @Override
        public void onDisconnected(SubscriberKit arg0) {
            // TODO Auto-generated method stub
            JSONObject eventData = new JSONObject();
            String streamId = arg0.getStream().getStreamId();
            try {
                eventData.put(DATA_KEY_STREAM_ID, streamId);
                emitCommonEvent(SCOPE_TYPE_SUBSCRIBER_EVENTS, EVENT_TYPE_DISCONNECTED, eventData);
            } catch (JSONException e) {
                Timber.e("JSONException " + e.getMessage());
            }
            Timber.i("subscriber" + streamId + " is disconnected");
        }

        @Override
        public void onError(SubscriberKit arg0, OpentokError arg1) {
            JSONObject eventData = new JSONObject();
            String streamId = arg0.getStream().getStreamId();
            int errorCode = arg1.getErrorCode().getErrorCode();
            String errorMessage = arg1.getMessage();
            try {
                eventData.put(DATA_KEY_ERROR_CODE, errorCode);
                eventData.put(DATA_KEY_STREAM_ID, streamId);
                emitCommonEvent(SCOPE_TYPE_SESSION_EVENTS, EVENT_TYPE_SUBSCRIBED_TO_STREAM, eventData);
            } catch (JSONException e) {
                Timber.e("JSONException " + e.getMessage());
            }
            Timber.e("subscriber exception: " + errorMessage + ", stream id: " + streamId);
        }

        // listeners
        @Override
        public void onVideoDataReceived(SubscriberKit arg0) {
            emitCommonEvent(SCOPE_TYPE_SUBSCRIBER_EVENTS, EVENT_TYPE_VIDEO_DATA_RECEIVED, null);
        }

        @Override
        public void onVideoDisabled(SubscriberKit arg0, String reason) {
            JSONObject data = new JSONObject();
            try {
                data.put(DATA_KEY_REASON, reason);
                emitCommonEvent(SCOPE_TYPE_SUBSCRIBER_EVENTS, EVENT_TYPE_VIDEO_DISABLED, data);
            } catch(JSONException e) {
            }
        }

        @Override
        public void onVideoDisableWarning(SubscriberKit arg0) {
            emitCommonEvent(SCOPE_TYPE_SUBSCRIBER_EVENTS, EVENT_TYPE_VIDEO_DISABLE_WARNING, null);
        }

        @Override
        public void onVideoDisableWarningLifted(SubscriberKit arg0) {
            emitCommonEvent(SCOPE_TYPE_SUBSCRIBER_EVENTS, EVENT_TYPE_VIDEO_DISABLE_WARNING_LIFTED, null);
        }

        @Override
        public void onVideoEnabled(SubscriberKit arg0, String reason) {
            JSONObject data = new JSONObject();
            try {
                data.put(DATA_KEY_REASON, reason);
                emitCommonEvent(SCOPE_TYPE_SUBSCRIBER_EVENTS, EVENT_TYPE_VIDEO_ENABLED, data);
            } catch (JSONException ignored) {
            }
        }

        // audioLevelListener
        public void onAudioLevelUpdated(SubscriberKit subscriber, float audioLevel) {
            JSONObject data = new JSONObject();
            try {
                data.put(DATA_KEY_AUDIO_LEVEL, audioLevel);
                emitCommonEvent(SCOPE_TYPE_SUBSCRIBER_EVENTS, EVENT_TYPE_AUDIO_LEVEL_UPDATED, data);
            } catch (JSONException ignored) {
            }
        }

        public void subscribeToAudio(boolean value) {
            mSubscriber.setSubscribeToAudio(value);
        }

        public void subscribeToVideo(boolean value) {
            mSubscriber.setSubscribeToVideo(value);
        }
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        mInstance = this;

        // Make the web view transparent.
        webView.getView().setBackgroundColor(Color.argb(1, 0, 0, 0));

        Timber.d("Initialize Plugin");
        // By default, get a pointer to mainView and add mainView to the viewList as it always exists (hold cordova's view)
        if (!viewList.has("mainView")) {
            // Cordova view is not in the viewList so add it.
            try {
                viewList.put("mainView", webView);
                Timber.d("Found CordovaView ****** " + webView);
            } catch (JSONException e) {
                // Error handle (this should never happen!)
                Timber.e("Critical error. Failed to retrieve Cordova's view");
            }
        }

        // set OpenTok states
        publishCalled = false;
        sessionConnected = false;
        myEventListeners = new HashMap<String, CallbackContext>();
        connectionCollection = new HashMap<String, Connection>();
        streamCollection = new HashMap<String, Stream>();
        subscriberCollection = new HashMap<String, RunnableSubscriber>();

        // Old tracking values.
        streamHasAudio = new HashMap<String, Boolean>();
        streamHasVideo = new HashMap<String, Boolean>();
        streamVideoDimensions = new HashMap<String, JSONObject>();
        String deviceName = Settings.Global.getString(cordova.getContext().getContentResolver(), "device_name");
        /* DEV-11766 (epic DEV-11304) : Setting custom audio driver for A7 lite devices to enhance volume*/
        Timber.d("Device name ----> " + deviceName);
        Timber.d("AudioDeviceManager instance : " + AudioDeviceManager.getAudioDevice());
        boolean isCustomAudioDriverSet = AudioDeviceManager.getAudioDevice() instanceof AdvancedAudioDevice;
        Timber.d("is Custom Audio Driver Set --> " + isCustomAudioDriverSet);
        if (deviceName!=null && deviceName.contains("A7 Lite") && !isCustomAudioDriverSet) {
            AdvancedAudioDevice advancedAudioDevice = new AdvancedAudioDevice(cordova.getContext());
            AudioDeviceManager.setAudioDevice(advancedAudioDevice);
            Timber.d("For A7 lite, setting custom audio driver");
        }

        super.initialize(cordova, webView);
    }

    @Override
    public void onDestroy() {
        Timber.i("onDestroy");
        mInstance = null;
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        Timber.i(action);
        // TB Methods
        if (ACTION_INIT_PUBLISHER.equals(action)) {
            if (USE_WEBVIEW_SESSION) {
                Timber.d("init publisher for webview");
                myPublisher = new RunnablePublisher(args);
            } else {
                myPublisher = new RunnablePublisher(args);
            }
        } else if (ACTION_DESTROY_PUBLISHER.equals(action)) {
            if (myPublisher != null) {
                myPublisher.destroyPublisher();
                myPublisher = null;
                callbackContext.success();
                return true;
            }
        } else if (ACTION_INIT_SESSION.equals(action)) {
            Timber.i("init session command called");
            apiKey = args.getString(0);
            sessionId = args.getString(1);
            if (USE_WEBVIEW_SESSION) {
                Timber.i("created new webview session with data: " + args.toString());
                mSession = new Session(this.cordova.getActivity().getApplicationContext(), apiKey, sessionId);
                mSession.setSessionListener(this);
                mSession.setConnectionListener(this);
                mSession.setReconnectionListener(this);
                mSession.setSignalListener(this);
                mSession.setStreamPropertiesListener(this);
                logOT(null);
            } else {
                Timber.i("created new session with data: " + args.toString());
                mSession = new Session(this.cordova.getActivity().getApplicationContext(), apiKey, sessionId);
                mSession.setSessionListener(this);
                mSession.setConnectionListener(this);
                mSession.setReconnectionListener(this);
                mSession.setSignalListener(this);
                mSession.setStreamPropertiesListener(this);
                logOT(null);
            }
        } else if (ACTION_SET_CAMERA_POSITION.equals(action)) {
            myPublisher.mPublisher.cycleCamera();
        } else if (ACTION_PUBLISH_AUDIO.equals(action)) {
            String val = args.getString(0);
            boolean publishAudio = true;
            if (val.equalsIgnoreCase("false")) {
                publishAudio = false;
            }
            Timber.i("setting publishAudio");
            myPublisher.mPublisher.setPublishAudio(publishAudio);
        } else if (ACTION_PUBLISH_VIDEO.equals(action)) {
            String val = args.getString(0);
            boolean publishVideo = true;
            if (val.equalsIgnoreCase("false")) {
                publishVideo = false;
            }
            Timber.i("setting publishVideo");
            myPublisher.mPublisher.setPublishVideo(publishVideo);

            // session Methods
        } else if (ACTION_ADD_EVENT.equals(action)) {
            Timber.i("adding new event - " + args.getString(0));
            myEventListeners.put(args.getString(0), callbackContext);
        } else if (ACTION_CONNECT.equals(action)) {
            Timber.d("CONNECT method called");
            String token = args.getString(0);
            if (USE_WEBVIEW_SESSION) {
                Timber.i("Will connect webview session");
                mSession.connect(token);
            } else {
                mSession.connect(token);
                callbackContext.success();
            }
        } else if (ACTION_DISCONNECT.equals(action)) {
            mSession.disconnect();
        } else if (ACTION_PUBLISH.equals(action)) {
            if (sessionConnected) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    cordova.requestPermissions(this, 0, perms);
                    permissionsCallback = callbackContext;
                } else {
                    myPublisher.startPublishing();
                    Timber.i("publisher is publishing");
                }
            }
        } else if (ACTION_SIGNAL.equals(action)) {
            Connection c = connectionCollection.get(args.getString(2));
            if (c == null) {
                mSession.sendSignal(args.getString(0), args.getString(1));
            } else {
                mSession.sendSignal(args.getString(0), args.getString(1), c);
            }
        } else if (ACTION_UNPUBLISH.equals(action)) {
            Timber.i("unpublish command called");
            if (myPublisher != null) {
                myPublisher.stopPublishing();
                callbackContext.success();
                return true;
            }
        } else if (ACTION_UNSUBSCRIBE.equals(action)) {
            Timber.i("unsubscribe command called");
            Timber.i("unsubscribe data: " + args.toString() );
            final RunnableSubscriber runsub = subscriberCollection.get( args.getString(0) );
            if (runsub != null) {
                runsub.removeStreamView();
                callbackContext.success();
                return true;
            }
        } else if (ACTION_SUBSCRIBE.equals(action)) {
            Timber.i("subscribe command called");
            Timber.i("subscribe data: " + args.toString());
            Stream stream = streamCollection.get(args.getString(0));
            RunnableSubscriber runsub = new RunnableSubscriber(args, stream);
            subscriberCollection.put(stream.getStreamId(), runsub);
        } else if (ACTION_SUBSCRIBE_TO_AUDIO.equals(action)) {
            RunnableSubscriber runsub = subscriberCollection.get(args.getString(0));
            String val = args.getString(1);
            if(runsub != null) {
                boolean subscribeAudio = true;
                if (val.equalsIgnoreCase("false")) {
                    subscribeAudio = false;
                }
                Timber.i("setting subscribeToAudio");
                runsub.subscribeToAudio(subscribeAudio);
            }
        } else if (ACTION_SUBSCRIBE_TO_VIDEO.equals(action)) {
            RunnableSubscriber runsub = subscriberCollection.get(args.getString(0));
            String val = args.getString(1);
            if(runsub != null) {
                boolean subscribeVideo = true;
                if (val.equalsIgnoreCase("false")) {
                    subscribeVideo = false;
                }
                Timber.i("setting subscribeToVideo");
                runsub.subscribeToVideo(subscribeVideo);
            }
        } else if (ACTION_UPDATE_VIEW.equals(action)) {
            if (args.getString(0).equals("TBPublisher") && myPublisher != null && sessionConnected) {
                Timber.i("updating view for publisher");
                myPublisher.setPropertyFromArray(args);
                cordova.getActivity().runOnUiThread(myPublisher);
            } else {
                RunnableSubscriber runsub = subscriberCollection.get(args.getString(0));
                if (runsub != null) {
                    runsub.setPropertyFromArray(args);
                    cordova.getActivity().runOnUiThread(runsub);
                }
            }
        } else if (ACTION_GET_IMG_DATA.equals(action)) {
            if (args.getString(0).equals("TBPublisher") && myPublisher != null && sessionConnected) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                      myPublisher.getImgData(callbackContext);
                    }
                });
                return true;
            } else {
                RunnableSubscriber runsub = subscriberCollection.get(args.getString(0));
                if (runsub != null) {
                  cordova.getThreadPool().execute(new Runnable() {
                      public void run() {
                         runsub.getImgData(callbackContext);
                      }
                  });
                  runsub.getImgData(callbackContext);
                  return true;
                }
            }
        } else if (ACTION_EXCEPTION_HANDLER.equals(action)) {
            Timber.d("ignoring action '%s' (unimplemented)", ACTION_EXCEPTION_HANDLER);

        } else if (ACTION_GET_OVERLAY_STATE.equals(action)) {
            callbackContext.success(getCurrentOverlayState());

        } else if (ACTION_SET_MINIMIZED.equals(action)) {
            boolean requestMinimized = args.getBoolean(0);
            setMinimized(requestMinimized, callbackContext);

        } else if (ACTION_SET_SHARED_EVENT_LISTENER.equals(action)) {
            setSharedEventListener(callbackContext);
        }
        return true;
    }

    private JSONObject getCurrentOverlayState() {
//        boolean active = mVonageActivity != null;
        JSONObject result = new JSONObject();
//        try {
//            result
//                .put("active", active)
//                .put("minimized", minimized);
//        } catch (JSONException e) {
//            Timber.e("getCurrentOverlayState failed! -> %s", e.getMessage());
//        }
        return result;
    }

    // emits to both trigger function and shared function to reduce boilerplate
    private void emitCommonEvent(String scope, String type, JSONObject data) {
        triggerJSEvent(scope, type, data);
        emitSharedJsEvent(scope, type, data);
    }
    
    private void setSharedEventListener(CallbackContext callbackContext)
    {
        if (sharedEventContext != null) {
            sharedEventContext.error("event listener callback overwritten");
        }
        sharedEventContext = callbackContext;
    }

    /**
     * Variation of `triggerJSEvent()` that passes event data directly
     * to application code for more granular control.
     * (i.e., does not do any unwrapping / remapping from the plugin side)
     */
    private void emitSharedJsEvent(String scope, String type, JSONObject data) {
        Timber.d("emitSharedJsEvent -> %s", type);
        try {
            if (sharedEventContext == null) {
                return;
            }
            if (data == null) {
                data = JSON_OBJECT_EMPTY;
            }
            JSONObject payload = new JSONObject()
                .put(KEY_SCOPE, scope)
                .put(KEY_TYPE, type)
                .put(KEY_DATA, data);
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, payload);
            pluginResult.setKeepCallback(true);
            sharedEventContext.sendPluginResult(pluginResult);
        } catch (JSONException e) {
            Timber.e("emitSharedJsEvent failed! -> %s", e.getMessage());
        }
    }

//    private void notifyCurrentOverlayState() {
//        emitSharedJsEvent("overlayStateChanged", getCurrentOverlayState());
//    }

    private void setMinimized(boolean requestMinimized, CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            @RequiresApi(Build.VERSION_CODES.O)
            public void run() {
                try {
//                    if (mVonageActivity == null) {
//                        callbackContext.error("overlay not active");
//                        return;
//                    }
                    if (minimized && requestMinimized) {
                        callbackContext.error("already minimized");
                        return;
                    }
                    if (!minimized && !requestMinimized) {
                        callbackContext.error("already maximized");
                        return;
                    }
                    if (requestMinimized) {
                        // mVonageActivity.minimize();
                        callbackContext.success();
                    } else {
                        // mVonageActivity.maximize();
                        callbackContext.success();
                    }
                } catch (Exception ex) {
                    String errorMessage = "minimize Error: " + ex.getMessage();
                    Timber.e(errorMessage);
                    callbackContext.error(errorMessage);
                }
            }
        });
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] results) throws JSONException {
        Boolean permissionError = false;
        for (int permissionResult : results) {
            if (permissionResult == PackageManager.PERMISSION_DENIED) {
                permissionError = true;
            }
        }
        if (permissionError) {
            PluginResult callback = new PluginResult(PluginResult.Status.ERROR, "permission denied");
            callback.setKeepCallback(false);
            permissionsCallback.sendPluginResult(callback);
        } else {
            myPublisher.startPublishing();
            Timber.i("permission granted-publisher is publishing");
        }
    }

    public void alertUser(String message) {
        // 1. Instantiate an AlertDialog.Builder with its constructor
        AlertDialog.Builder builder = new AlertDialog.Builder(cordova.getActivity());
        builder.setMessage(message).setTitle("TokBox Message");
        AlertDialog dialog = builder.create();
    }


    // sessionListener
    @Override
    public void onConnected(Session arg0) {
        logOT(arg0.getConnection().getConnectionId());
        Timber.i("session connected, triggering sessionConnected Event. My Cid is: " +
                mSession.getConnection().getConnectionId());
        sessionConnected = true;

        connectionCollection.put(mSession.getConnection().getConnectionId(), mSession.getConnection());
        emitConnectedEvent(mSession);
    }

    public void emitConnectedEvent(Session session) {
        JSONObject data = new JSONObject();
        try {
            data.put(DATA_KEY_STATUS, STATUS_CONNECTED);
            JSONObject connection = createDataFromConnection(session.getConnection());
            data.put(DATA_KEY_CONNECTION, connection);
        } catch (JSONException e) {
        }
        emitCommonEvent(SCOPE_TYPE_SESSION_EVENTS, EVENT_TYPE_SESSION_CONNECTED, data);
    }

    @Override
    public void onDisconnected(Session arg0) {
        sessionConnected = false;

        if (myPublisher != null) {
            myPublisher.destroyPublisher();
            myPublisher = null;
        }

        for (Map.Entry<String, RunnableSubscriber> entry : subscriberCollection.entrySet()) {
            entry.getValue().removeStreamView();
        }

        // delete all data and prevent updateviews from drawing non existent things
        subscriberCollection.clear();
        connectionCollection.clear();
        streamCollection.clear();

        emitDisconnectedEvent();
    }

    public void emitDisconnectedEvent() {
        JSONObject data = new JSONObject();
        try {
            data.put(DATA_KEY_REASON, REASON_CLIENT_DISCONNECTED);
        } catch (JSONException ignored) {
        }
        emitCommonEvent(SCOPE_TYPE_SESSION_EVENTS, EVENT_TYPE_SESSION_DISCONNECTED, data);
    }

    // reconnection listener
    @Override
    public void onReconnected(Session session) {
        Timber.i("session reconnected");
        emitSessionReconnectedEvent();
    }

    public void emitSessionReconnectedEvent() {
        emitCommonEvent(SCOPE_TYPE_SESSION_EVENTS, EVENT_TYPE_SESSION_RECONNECTED, null);
    }

    @Override
    public void onReconnecting(Session session) {
        Timber.i("session reconnecting");
        emitSessionReconnectingEvent();
    }

    public void emitSessionReconnectingEvent() {
        emitCommonEvent(SCOPE_TYPE_SESSION_EVENTS, EVENT_TYPE_SESSION_RECONNECTING, null);
    }

    @Override
    public void onStreamDropped(Session arg0, Stream arg1) {
        Timber.i("session dropped stream");
        streamCollection.remove(arg1.getStreamId());

        streamHasAudio.remove(arg1.getStreamId());
        streamHasVideo.remove(arg1.getStreamId());
        streamVideoDimensions.remove(arg1.getStreamId());

        RunnableSubscriber subscriber = subscriberCollection.get(arg1.getStreamId());
        if (subscriber != null) {
            subscriber.removeStreamView();
            subscriberCollection.remove(arg1.getStreamId());
        }
        emitStreamDroppedEvent(arg1);
    }

    public void emitStreamDroppedEvent(Stream arg1) {
        triggerStreamEvent(arg1, SCOPE_TYPE_SESSION_EVENTS, EVENT_TYPE_STREAM_DESTROYED);
    }

    @Override
    public void onStreamReceived(Session arg0, Stream arg1) {
        Timber.i("stream received");
        streamCollection.put(arg1.getStreamId(), arg1);

        this.streamHasAudio.put(arg1.getStreamId(), arg1.hasAudio());
        this.streamHasVideo.put(arg1.getStreamId(), arg1.hasVideo());
        JSONObject videoDimensions = new JSONObject();
        try {
            videoDimensions.put(DATA_KEY_WIDTH, arg1.getVideoWidth());
            videoDimensions.put(DATA_KEY_HEIGHT, arg1.getVideoHeight());
        } catch (JSONException e) {
        }
        this.streamVideoDimensions.put(arg1.getStreamId(), videoDimensions);

        emitStreamReceivedEvent(arg1);
    }

    public void emitStreamReceivedEvent(Stream arg1) {
        triggerStreamEvent(arg1, SCOPE_TYPE_SESSION_EVENTS, EVENT_TYPE_STREAM_CREATED);
    }

    @Override
    public void onError(Session arg0, OpentokError arg1) {
        // TODO Auto-generated method stub
        Timber.e("session exception: " + arg1.getMessage());
        alertUser("session error " + arg1.getMessage());
    }

    // connectionListener
    public void onConnectionCreated(Session arg0, Connection arg1) {
        Timber.i("connectionCreated");
        connectionCollection.put(arg1.getConnectionId(), arg1);
        emitConnectionCreatedEvent(arg1);
    }

    public void emitConnectionCreatedEvent(Connection arg1) {
        JSONObject data = new JSONObject();
        try {
            JSONObject connection = createDataFromConnection(arg1);
            data.put(DATA_KEY_CONNECTION, connection);
        } catch (JSONException e) {
        }
        emitCommonEvent(SCOPE_TYPE_SESSION_EVENTS, EVENT_TYPE_CONNECTION_CREATED, data);
    }

    public void onConnectionDestroyed(Session arg0, Connection arg1) {
        if (arg1!=null) {
            Timber.i("connection dropped: " + arg1.getConnectionId());
            connectionCollection.remove(arg1.getConnectionId());
            emitConnectionDestroyedEvent(arg1);
        } else {
            Timber.i("Connection id does not exist");
        }
    }

    public void emitConnectionDestroyedEvent(Connection arg1) {
        JSONObject data = new JSONObject();
        try {
            JSONObject connection = createDataFromConnection(arg1);
            data.put(DATA_KEY_CONNECTION, connection);
        } catch (JSONException e) {
        }
        emitCommonEvent(SCOPE_TYPE_SESSION_EVENTS, EVENT_TYPE_CONNECTION_DESTROYED, data);
    }

    // signalListener
    public void onSignalReceived(Session arg0, String arg1, String arg2, Connection arg3) {
        JSONObject data = new JSONObject();
        Timber.i("signal type: " + arg1);
        Timber.i("signal data: " + arg2);
        try {
            data.put(KEY_TYPE, arg1);
            data.put(KEY_DATA, arg2);
            if (arg3 != null) {
                data.put(DATA_KEY_CONNECTION_ID, arg3.getConnectionId());
            }
            emitCommonEvent(SCOPE_TYPE_SESSION_EVENTS, EVENT_TYPE_SIGNAL_RECEIVED, data);
        } catch (JSONException ignored) {
        }
    }

    // archiveListener
    public void onArchiveStarted(Session session, String id, String name) {
        JSONObject data = new JSONObject();
        try {
            data.put(DATA_KEY_ID, id);
            data.put(DATA_KEY_NAME, name);
            emitCommonEvent(SCOPE_TYPE_SESSION_EVENTS, EVENT_TYPE_ARCHIVE_STARTED, data);
        } catch (JSONException e) {
            Timber.i("archive started: " + id + " - " + name);
        }
    }

    public void onArchiveStopped(Session session, String id) {
        JSONObject data = new JSONObject();
        try {
            data.put(DATA_KEY_ID, id);
            emitCommonEvent(SCOPE_TYPE_SESSION_EVENTS, EVENT_TYPE_ARCHIVE_STOPPED, data);
        } catch (JSONException e) {
            Timber.i("archive stopped: " + id);
        }
    }

    // streamPropertiesListener
    @Override
    public void onStreamHasAudioChanged(Session session, Stream stream, boolean newValue) {
        boolean oldValue = Boolean.TRUE.equals(this.streamHasAudio.get(stream.getStreamId()));
        this.streamHasAudio.put(stream.getStreamId(), newValue);

        this.onStreamPropertyChanged(DATA_KEY_HAS_AUDIO, newValue, oldValue, stream);
    }

    @Override
    public void onStreamHasVideoChanged(Session session, Stream stream, boolean newValue) {
        boolean oldValue = Boolean.TRUE.equals(this.streamHasVideo.get(stream.getStreamId()));
        this.streamHasVideo.put(stream.getStreamId(), newValue);

        this.onStreamPropertyChanged(DATA_KEY_HAS_VIDEO, newValue, oldValue, stream);
    }

    @Override
    public void onStreamVideoDimensionsChanged(Session session, Stream stream, int width, int height) {
        JSONObject oldValue = this.streamVideoDimensions.get(stream.getStreamId());

        JSONObject newValue = new JSONObject();
        try {
            newValue.put(DATA_KEY_WIDTH, width);
            newValue.put(DATA_KEY_HEIGHT, height);
            this.streamVideoDimensions.put(stream.getStreamId(), newValue);

            this.onStreamPropertyChanged(DATA_KEY_VIDEO_DIMENSIONS, newValue, oldValue, stream);
        } catch (JSONException e) {
        }
    }

    public void onStreamPropertyChanged(String changedProperty, Object newValue, Object oldValue, Stream stream) {
        JSONObject data = new JSONObject();
        try {
            JSONObject streamData = createDataFromStream(stream);
            data.put(DATA_KEY_CHANGED_PROPERTY, changedProperty);
            data.put(DATA_KEY_NEW_VALUE, newValue);
            data.put(DATA_KEY_OLD_VALUE, oldValue);
            data.put(DATA_KEY_STREAM, streamData);
            emitCommonEvent(SCOPE_TYPE_SESSION_EVENTS, EVENT_TYPE_STREAM_PROPERTY_CHANGED, data);
        } catch (JSONException ignored) {
        }
    }

    // Helper Methods
    public void logMessage(String a) {
        Timber.i(a);
    }

    public boolean compareStrings(String a, String b) {
        if (a != null && b != null && a.equalsIgnoreCase(b)) {
            return true;
        }
        return false;
    }

    public void triggerStreamEvent(Stream arg1, String eventType, String subEvent) {
        JSONObject data = new JSONObject();
        try {
            JSONObject stream = createDataFromStream(arg1);
            data.put(DATA_KEY_STREAM, stream);
            emitCommonEvent(eventType, subEvent, data);
        } catch (JSONException ignored) {
        }
    }

    public void logOT(final String connectionId) {
        RequestQueue queue = Volley.newRequestQueue(this.cordova.getActivity().getApplicationContext());
        String url = "https://hlg.tokbox.com/prod/logging/ClientEvent";
        StringRequest postRequest = new StringRequest(Request.Method.POST, url,
            new Response.Listener<String>()
            {
                @Override
                public void onResponse(String response) {
                    // response
                    Timber.i("Log Response: " + response);
                }
            },
            new Response.ErrorListener()
            {
                 @Override
                 public void onErrorResponse(VolleyError error) {
                     // error
                     Timber.i("Error logging");
               }
            }
        ) {
            @Override
            protected Map<String, String> getParams()
            {
                    JSONObject payload = new JSONObject();
                    try {
                        payload.put("platform", "Android");
                        payload.put("cp_version", "3.4.4");
                    } catch (JSONException e) {
                        Timber.i("Error creating payload json object");
                    }
                    Map<String, String>  params = new HashMap<String, String>();
                    params.put("payload_type", "info");
                    params.put("partner_id", apiKey);
                    params.put("payload", payload.toString());
                    params.put("source", "https://github.com/opentok/cordova-plugin-opentok");
                    params.put("build", "2.16.6");
                    params.put("session_id", sessionId);
                    if (connectionId != null) {
                        params.put("action", "cp_on_connect");
                        params.put(DATA_KEY_CONNECTION_ID, connectionId);
                    } else {
                        params.put("action", "cp_initialize");
                    }

                return params;
            }
        };
        queue.add(postRequest);
    }

    public JSONObject createDataFromConnection(Connection arg1) {
        JSONObject connection = new JSONObject();

        try {
            connection.put(DATA_KEY_CONNECTION_ID, arg1.getConnectionId());
            connection.put(DATA_KEY_CREATION_TIME, arg1.getCreationTime());
            connection.put(KEY_DATA, arg1.getData());
        } catch (JSONException e) {
        }
        return connection;
    }

    public JSONObject createDataFromStream(Stream arg1) {
        JSONObject result = new JSONObject();
        try {
            Connection connection = arg1.getConnection();
            if (connection != null) {
                result.put(DATA_KEY_CONNECTION_ID, connection.getConnectionId()); // Backwards compatibility
                result.put(DATA_KEY_CONNECTION, createDataFromConnection(connection));
            }
            result.put(DATA_KEY_CREATION_TIME, arg1.getCreationTime());
            result.put(DATA_KEY_FPS, -999);
            result.put(DATA_KEY_HAS_AUDIO, arg1.hasAudio());
            result.put(DATA_KEY_HAS_VIDEO, arg1.hasVideo());

            JSONObject videoDimensions = new JSONObject();
            try {
                videoDimensions.put(DATA_KEY_WIDTH, arg1.getVideoWidth());
                videoDimensions.put(DATA_KEY_HEIGHT, arg1.getVideoHeight());
            } catch (JSONException ignored) {}
            result.put(DATA_KEY_VIDEO_DIMENSIONS, videoDimensions);

            String videoType = VIDEO_TYPE_CUSTOM;
            if(arg1.getStreamVideoType() == Stream.StreamVideoType.StreamVideoTypeCamera) {
                videoType = VIDEO_TYPE_CAMERA;
            } else if(arg1.getStreamVideoType() == Stream.StreamVideoType.StreamVideoTypeScreen) {
                videoType = VIDEO_TYPE_SCREEN;
            }
            result.put(DATA_KEY_VIDEO_TYPE, videoType);

            result.put(DATA_KEY_NAME, arg1.getName());
            result.put(DATA_KEY_STREAM_ID, arg1.getStreamId());
        } catch (Exception ignored) {
        }
        return result;
    }

    public void triggerJSEvent(String event, String type, Object data) {
        JSONObject message = new JSONObject();

        try {
            message.put(KEY_EVENT_TYPE, type);
            message.put(KEY_DATA, data);
        } catch (JSONException ignored) { }

        PluginResult myResult = new PluginResult(PluginResult.Status.OK, message);
        myResult.setKeepCallback(true);
        myEventListeners.get(event).sendPluginResult(myResult);
    }

    private static JSONObject getOpentokErrorAsJson(OpentokError opentokError) {
        JSONObject data = new JSONObject();
        try {
            data.put(DATA_KEY_MESSAGE, opentokError.getMessage());
            data.put(DATA_KEY_ERROR_DOMAIN, opentokError.getErrorDomain().toString());
            data.put(DATA_KEY_ERROR_NAME, opentokError.getErrorCode().name());
            data.put(DATA_KEY_ERROR_CODE, opentokError.getErrorCode().getErrorCode());
            Exception systemException = opentokError.getException();
            if (systemException != null) {
                data.put(DATA_KEY_SYSTEM_MESSAGE, systemException.getMessage());
            }
        } catch (JSONException e) {
        }
        return data;
    }

    public void emitSessionError(OpentokError opentokError) {
        JSONObject data = new JSONObject();
        try {
            data.put(DATA_KEY_OPENTOK_ERROR, getOpentokErrorAsJson(opentokError));
        } catch (JSONException e) {
        }
        // This method was previously unused, so commenting out this `triggerJSEvent`
        // call to prevent introducing new / unexpected behaviors.
        // triggerJSEvent(SCOPE_TYPE_SESSION_EVENTS, EVENT_TYPE_SESSION_ERROR, data);

        emitSharedJsEvent(SCOPE_TYPE_SESSION_EVENTS, EVENT_TYPE_SESSION_ERROR, data);
    }

    @Override
    public void onError(PublisherKit arg0, OpentokError arg1) {
        emitSessionError(arg1);
    }

    @Override
    public void onStreamCreated(PublisherKit arg0, Stream arg1) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onStreamDestroyed(PublisherKit arg0, Stream arg1) {
        if (myPublisher != null) {
            myPublisher.destroyPublisher();
            myPublisher = null;
        }
    }

    @Override
    public void onStreamVideoTypeChanged(Session arg0, Stream arg1,
                                         StreamVideoType arg2) {
        // TODO Auto-generated method stub

    }
}
