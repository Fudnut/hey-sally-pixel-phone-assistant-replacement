"""Run the real WakeService against deterministic Android/audio boundary doubles.

Requires JDK 17+ and ANDROID_HOME. No phone, calls, microphone or network access.
"""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parent.parent
out = root / '.tools/call-checks'
source = root / 'app/src/main/java/com/steve/spotifywakeprobe'
files = {
    'android/content/Context.java': '''package android.content;
import java.util.*; import java.util.concurrent.Executor;
public class Context {
 public static final int MODE_PRIVATE=0, RECEIVER_EXPORTED=2;
 public final android.media.AudioManager audio=new android.media.AudioManager();
 public final android.telecom.TelecomManager telecom=new android.telecom.TelecomManager();
 public final List<BroadcastReceiver> receivers=new ArrayList<>(); public boolean phonePermission=true;
 public <T> T getSystemService(Class<T> type) {
  Object result=type==android.media.AudioManager.class?audio:type==android.telecom.TelecomManager.class?telecom:new android.app.NotificationManager();
  return type.cast(result);
 }
 public int checkSelfPermission(String p){return p.equals("android.permission.READ_PHONE_STATE")&&!phonePermission?-1:0;}
 public Executor getMainExecutor(){return Runnable::run;}
 public Intent registerReceiver(BroadcastReceiver r,IntentFilter f,int flags){receivers.add(r);return null;}
 public void unregisterReceiver(BroadcastReceiver r){receivers.remove(r);}
 public void broadcast(String state){for(var r:new ArrayList<>(receivers))r.onReceive(this,new Intent(android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED).putExtra(android.telephony.TelephonyManager.EXTRA_STATE,state));}
 public SharedPreferences getSharedPreferences(String name,int mode){return new SharedPreferences();}
 public String getPackageName(){return "com.steve.spotifywakeprobe";}
 public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}
}''',
    'android/content/pm/PackageManager.java': '''package android.content.pm; import java.util.*; public class PackageManager {
 public static final int PERMISSION_GRANTED=0;
 public static class ResolveInfoFlags {public static ResolveInfoFlags of(long f){return new ResolveInfoFlags();}}
 public List<ResolveInfo> queryIntentServices(android.content.Intent i,ResolveInfoFlags f){ResolveInfo r=new ResolveInfo();r.serviceInfo=new ServiceInfo();r.serviceInfo.packageName="com.google.android.as";return List.of(r);}
}''',
    'android/content/pm/ResolveInfo.java': 'package android.content.pm; public class ResolveInfo {public ServiceInfo serviceInfo;}',
    'android/content/pm/ServiceInfo.java': 'package android.content.pm; public class ServiceInfo {public static final int FOREGROUND_SERVICE_TYPE_MICROPHONE=128;public String packageName;}',
    'android/content/SharedPreferences.java': '''package android.content; public class SharedPreferences {
 public static boolean carInput,placeGate;public boolean getBoolean(String k,boolean d){return k.equals("carInput")?carInput:k.equals("placeGate")?placeGate:d;}public String getString(String k,String d){return d;} public Editor edit(){return new Editor();}
 public static class Editor { public Editor putString(String k,String v){return this;} public void apply(){} }
}''',
    'android/content/Intent.java': '''package android.content; import java.util.*; public class Intent {
 String action; Map<String,String> values=new HashMap<>(); public Intent(String a){action=a;} public Intent(Context c,Class<?> t){}
 public String getAction(){return action;} public String getStringExtra(String k){return values.get(k);}
 public Intent putExtra(String k,String v){values.put(k,v);return this;} public Intent putExtra(String k,boolean v){return this;}
 public Intent putExtra(String k,int v){return this;} public Intent putStringArrayListExtra(String k,ArrayList<String> v){return this;}
}''',
    'android/content/IntentFilter.java': 'package android.content; public class IntentFilter {public IntentFilter(String s){}}',
    'android/content/BroadcastReceiver.java': 'package android.content; public abstract class BroadcastReceiver {public abstract void onReceive(Context c,Intent i);}',
    'android/app/Service.java': '''package android.app; import android.content.*; public class Service extends Context {
 public static final int START_STICKY=1,START_NOT_STICKY=2; public android.os.IBinder onBind(Intent i){return null;}
 public int stopsSelf;public int onStartCommand(Intent i,int f,int n){return 1;} public void onDestroy(){} public void stopSelf(){stopsSelf++;}
 public void startForeground(int id,Notification n,int type){}
}''',
    'android/app/Notification.java': '''package android.app; public class Notification {public static final int VISIBILITY_SECRET=-1;
 public static class Builder {public Builder(android.content.Context c,String s){} public Builder setSmallIcon(int i){return this;}
 public Builder setContentTitle(String s){return this;} public Builder setContentText(String s){return this;}
 public Builder setContentIntent(PendingIntent p){return this;} public Builder setOngoing(boolean b){return this;}
 public Builder setAutoCancel(boolean b){return this;} public Builder setVisibility(int i){return this;} public Notification build(){return new Notification();}}
}''',
    'android/app/NotificationChannel.java': 'package android.app; public class NotificationChannel {public NotificationChannel(String a,String b,int c){}}',
    'android/app/NotificationManager.java': '''package android.app; public class NotificationManager {public static final int IMPORTANCE_LOW=2,IMPORTANCE_DEFAULT=3;
 public void createNotificationChannel(NotificationChannel c){} public void cancel(int i){} public void notify(int i,Notification n){} }''',
    'android/app/PendingIntent.java': '''package android.app; public class PendingIntent {public static final int FLAG_IMMUTABLE=1,FLAG_UPDATE_CURRENT=2;
 public static PendingIntent getActivity(android.content.Context c,int n,android.content.Intent i,int f){return new PendingIntent();}}''',
    'android/os/Looper.java': 'package android.os; public class Looper {public static Looper getMainLooper(){return new Looper();}}',
    'android/os/Handler.java': '''package android.os; import java.util.*; public class Handler {
 public static final Map<Runnable,Long> timers=new LinkedHashMap<>(); public static long now;
 public static boolean asyncPosts;public Handler(Looper l){} public boolean post(Runnable r){if(asyncPosts)timers.put(r,now);else r.run();return true;}
 public boolean postDelayed(Runnable r,long delay){timers.put(r,now+delay);return true;} public void removeCallbacks(Runnable r){timers.remove(r);}
 public static void advance(long ms){now+=ms;for(var e:new ArrayList<>(timers.entrySet()))if(e.getValue()<=now&&timers.remove(e.getKey())!=null)e.getKey().run();}
}''',
    'android/os/SystemClock.java': 'package android.os; public class SystemClock {public static long elapsedRealtime(){return Handler.now;}}',
    'android/media/AudioAttributes.java': '''package android.media; public class AudioAttributes {public static final int USAGE_MEDIA=1,USAGE_ASSISTANT=16,USAGE_VOICE_COMMUNICATION=2,CONTENT_TYPE_SPEECH=1;
 public static class Builder {public Builder setUsage(int i){return this;} public Builder setContentType(int i){return this;} public AudioAttributes build(){return new AudioAttributes();}}}''',
    'android/media/AudioFocusRequest.java': '''package android.media; public class AudioFocusRequest {public AudioManager.OnAudioFocusChangeListener listener;
 public static class Builder {AudioFocusRequest r=new AudioFocusRequest(); public Builder(int i){} public Builder setAudioAttributes(AudioAttributes a){return this;}
 public Builder setOnAudioFocusChangeListener(AudioManager.OnAudioFocusChangeListener l,android.os.Handler h){r.listener=l;return this;} public AudioFocusRequest build(){return r;}}}''',
    'android/media/AudioManager.java': '''package android.media; import java.util.*; import java.util.concurrent.Executor; import android.view.KeyEvent;
public class AudioManager {
 public static final int AUDIOFOCUS_GAIN=1,AUDIOFOCUS_GAIN_TRANSIENT=2,AUDIOFOCUS_LOSS=-1,AUDIOFOCUS_REQUEST_GRANTED=1,STREAM_NOTIFICATION=5,STREAM_VOICE_CALL=0;
 public static final int MODE_NORMAL=0,MODE_RINGTONE=1,MODE_IN_CALL=2,MODE_IN_COMMUNICATION=3,MODE_CALL_SCREENING=4;
 public interface OnAudioFocusChangeListener {void onAudioFocusChange(int change);} public interface OnModeChangedListener {void onModeChanged(int mode);}
 public int mode,focusResult=1,focusRequests,abandons; public AudioFocusRequest focus; public OnModeChangedListener listener;
 public final List<String> keys=new ArrayList<>(); public int getMode(){return mode;}
 public void addOnModeChangedListener(Executor e,OnModeChangedListener l){listener=l;} public void removeOnModeChangedListener(OnModeChangedListener l){listener=null;}
 public void mode(int m){mode=m;if(listener!=null)listener.onModeChanged(m);}
 public int requestAudioFocus(AudioFocusRequest r){focus=r;focusRequests++;return focusResult;} public int abandonAudioFocusRequest(AudioFocusRequest r){abandons++;return 1;}
 public void dispatchMediaKeyEvent(KeyEvent e){keys.add(e.action+":"+e.code);}
}''',
    'android/media/ToneGenerator.java': 'package android.media; public class ToneGenerator {public static final int TONE_PROP_BEEP=1;public ToneGenerator(int s,int v){}public void startTone(int t,int d){}public void release(){}}',
    'android/view/KeyEvent.java': 'package android.view; public class KeyEvent {public static final int ACTION_DOWN=0,ACTION_UP=1,KEYCODE_MEDIA_PAUSE=127;public int action,code;public KeyEvent(int a,int c){action=a;code=c;}}',
    'android/telecom/TelecomManager.java': '''package android.telecom; public class TelecomManager {
 public boolean busy,denied; public java.util.Deque<Boolean> probes=new java.util.ArrayDeque<>();
 public boolean isInCall(){if(denied)throw new SecurityException();return probes.isEmpty()?busy:probes.removeFirst();}}''',
    'android/telephony/TelephonyManager.java': '''package android.telephony; public class TelephonyManager {
 public static final String ACTION_PHONE_STATE_CHANGED="android.intent.action.PHONE_STATE", EXTRA_STATE="state",
 EXTRA_STATE_RINGING="RINGING", EXTRA_STATE_OFFHOOK="OFFHOOK", EXTRA_STATE_IDLE="IDLE";}''',
    'android/speech/SpeechRecognizer.java': '''package android.speech; public class SpeechRecognizer {
 public static final String RESULTS_RECOGNITION="results"; public static final int ERROR_NO_MATCH=7,ERROR_SPEECH_TIMEOUT=6,ERROR_NETWORK=2,ERROR_NETWORK_TIMEOUT=1,ERROR_SERVER=4,ERROR_SERVER_DISCONNECTED=11,ERROR_LANGUAGE_NOT_SUPPORTED=12,ERROR_LANGUAGE_UNAVAILABLE=13;
 public static int starts;public static boolean isRecognitionAvailable(android.content.Context c){return true;}
 public static SpeechRecognizer createSpeechRecognizer(android.content.Context c){return new SpeechRecognizer();}
 public void setRecognitionListener(RecognitionListener l){} public void startListening(android.content.Intent i){starts++;} public void cancel(){} public void destroy(){}
}''',
    'android/speech/tts/TextToSpeech.java': '''package android.speech.tts; public class TextToSpeech {public static final int SUCCESS=0,ERROR=-1,QUEUE_FLUSH=0;public static int stops;
 public interface OnInitListener {void onInit(int s);}public TextToSpeech(android.content.Context c,OnInitListener l){}public void setAudioAttributes(android.media.AudioAttributes a){}
 public void setOnUtteranceProgressListener(UtteranceProgressListener l){}public int speak(String s,int q,android.os.Bundle b,String id){return 0;}public void stop(){stops++;}public void shutdown(){}
}''',
    'android/speech/tts/UtteranceProgressListener.java': 'package android.speech.tts;public abstract class UtteranceProgressListener {public abstract void onStart(String s);public abstract void onDone(String s);public abstract void onError(String s);}',
    'android/util/Log.java': 'package android.util;public class Log {public static int i(String t,String s){return 0;}public static int e(String t,String s){return 0;}public static int w(String t,String s){return 0;}}',
    'org/vosk/Model.java': 'package org.vosk;public class Model {public void close(){}}',
    'org/vosk/Recognizer.java': 'package org.vosk;public class Recognizer {public Recognizer(Model m,float r){}public void close(){}}',
    'org/vosk/android/RecognitionListener.java': 'package org.vosk.android;public interface RecognitionListener {void onPartialResult(String s);void onResult(String s);void onFinalResult(String s);void onError(Exception e);void onTimeout();}',
    'org/vosk/android/SpeechService.java': 'package org.vosk.android;public class SpeechService {public SpeechService(org.vosk.Recognizer r,float f){}public void startListening(RecognitionListener l,int t){}public void cancel(){}public void shutdown(){}}',
    'org/vosk/android/StorageService.java': 'package org.vosk.android;public class StorageService {public static void unpack(android.content.Context c,String a,String b,java.util.function.Consumer<org.vosk.Model> ok,java.util.function.Consumer<Exception> error){ok.accept(new org.vosk.Model());}}',
    'com/steve/spotifywakeprobe/WakeMicrophone.java': '''package com.steve.spotifywakeprobe;public class WakeMicrophone {public static int opened,closed;public static WakeMicrophone last;public static boolean lastCar,lastCommand;java.util.function.Consumer<Boolean> route;
 public WakeMicrophone(android.content.Context c,org.vosk.Model m){this(c,m,false,false,null);}public WakeMicrophone(android.content.Context c,org.vosk.Model m,boolean car,boolean command,java.util.function.Consumer<Boolean> route){opened++;last=this;lastCar=car;lastCommand=command;this.route=route;}boolean started;public boolean isStarted(){return started;}public void start(org.vosk.android.RecognitionListener l,Runnable ready){started=true;if(route!=null)route.accept(true);ready.run();}public void pause(){}public void resume(org.vosk.android.RecognitionListener l,Runnable ready,boolean command){lastCommand=command;ready.run();}public void close(){closed++;}public long resultAudioMs(){return 0;}}''',
    'com/steve/spotifywakeprobe/AndroidCarAudioSession.java': '''package com.steve.spotifywakeprobe;public class AndroidCarAudioSession implements AutoCloseable {public final CarAudioSession session;public static boolean preserveForeignMode;boolean own,closing;
 public AndroidCarAudioSession(android.content.Context c,java.util.function.BooleanSupplier call,Runnable beforeClose,java.util.function.Consumer<String> error){session=new CarAudioSession(new CarAudioSession.Platform(){public boolean hasPermissions(){return c.checkSelfPermission("android.permission.READ_PHONE_STATE")==0;}public boolean hasActiveCall(){return call.getAsBoolean();}public boolean requestRoute(){own=true;c.audio.mode(3);return true;}public void stopCapture(){beforeClose.run();}public void releaseRoute(){c.audio.mode(preserveForeignMode?3:0);own=false;}public void schedule(Runnable r,long ms){new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(r,ms);}public void cancel(Runnable r){new android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacks(r);}public void report(String s){if(!session.isActive()&&!closing)error.accept(s);}});}
 public boolean ownsCommunicationMode(){return own;}public void close(){closing=true;session.stop();}}''',
    'com/steve/spotifywakeprobe/DiagnosticHistory.java': '''package com.steve.spotifywakeprobe;import android.content.Context;public class DiagnosticHistory {
 public static final java.util.List<String> events=new java.util.ArrayList<>();public static void clearLegacyStatus(Context c){}public static void resetWakeResults(){}public static void record(Context c,String s){events.add(s);}public static void flushWakeResults(Context c){}public static String recordWakeResult(Context c,String s,long n){return null;}}''',
    'com/steve/spotifywakeprobe/SpotifyController.java': '''package com.steve.spotifywakeprobe;import android.content.Context;import java.util.*;import java.util.function.Consumer;public class SpotifyController {
 public static int dispatched,cancelled;public static Runnable duringDispatch;public static Runnable execute(Context c,VoiceCommand v,List<String> n,Consumer<String> r){dispatched++;if(duringDispatch!=null)duringDispatch.run();return ()->cancelled++;}
 public static Runnable playPlaylist(Context c,String u,Consumer<String> r){return execute(c,null,null,r);}
 public static Thread fetchPlaylists(Context c,Consumer<List<PlaylistBrowse.Entry>> r,Consumer<String> f){return new Thread();}}''',
    'com/steve/spotifywakeprobe/SpotifySpecialPlayback.java': 'package com.steve.spotifywakeprobe;import java.util.*;import java.util.function.Consumer;public class SpotifySpecialPlayback {public static Runnable start(android.content.Context c,VoiceCommand v,List<String> n,Runnable b,Consumer<String> r){return ()->{};}}',
    'com/steve/spotifywakeprobe/MainActivity.java': 'package com.steve.spotifywakeprobe;public class MainActivity {}',
    'com/steve/spotifywakeprobe/PlaceMonitor.java': '''package com.steve.spotifywakeprobe;public class PlaceMonitor implements AutoCloseable {
 public static PlaceMonitor last;public static boolean wifi,bluetooth;public boolean started,closed;final java.util.function.Consumer<Boolean> changed;
 public PlaceMonitor(android.content.Context c,java.util.function.Consumer<Boolean> changed){this.changed=changed;last=this;}
 public void start(){started=true;}public boolean allowed(){return !closed&&(wifi||bluetooth);}
 public void set(boolean w,boolean b){wifi=w;bluetooth=b;changed.accept(wifi||bluetooth);}public void close(){closed=true;}}''',
}
paths = []
for name, content in files.items():
    path = out / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8')
    paths.append(str(path))
sdk = Path(os.environ['ANDROID_HOME'])
platform = max((sdk / 'platforms').glob('*/android.jar'), key=lambda p: p.parent.name)
production = ['WakeService', 'VoiceCommand', 'PlaylistBrowse', 'WakePhrase', 'CommandLanguage', 'CommandFailure', 'CarAudioSession']
if (source / 'CallAudioMonitor.java').exists():
    production.append('CallAudioMonitor')
classes = out / 'classes'
subprocess.run(['javac', '-cp', str(platform), '-d', str(classes), *paths,
                *[str(source / f'{name}.java') for name in production],
                str(root / 'tests/CallInterruptionCheck.java'), str(root / 'tests/PlaceGateCheck.java')], check=True)
for check in ('CallInterruptionCheck', 'PlaceGateCheck'):
    subprocess.run(['java', '-cp', str(classes) + os.pathsep + str(platform),
                    f'com.steve.spotifywakeprobe.{check}'], check=True)
