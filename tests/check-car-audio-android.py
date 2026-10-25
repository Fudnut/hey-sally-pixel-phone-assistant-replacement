"""Exercise the real debug Activity/Android adapter with deterministic Android boundaries.

Requires ANDROID_HOME and a JDK. No device, audio hardware, saved samples or calls.
"""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parent.parent
out = root / '.tools/car-audio-android-checks'
files = {
'android/content/Context.java': '''package android.content; public class Context {
 public static final int RECEIVER_EXPORTED=2;public boolean mic=true,phone=true,allowStart=true;public int starts,stops,permissions;
 public final android.media.AudioManager audio=new android.media.AudioManager();public final android.telecom.TelecomManager telecom=new android.telecom.TelecomManager();
 public final android.app.ActivityManager manager=new android.app.ActivityManager();public final java.util.List<BroadcastReceiver> receivers=new java.util.ArrayList<>();
 public <T>T getSystemService(Class<T> t){return t.cast(t==android.media.AudioManager.class?audio:t==android.telecom.TelecomManager.class?telecom:manager);}
 public int checkSelfPermission(String p){return p.equals("android.permission.RECORD_AUDIO")?!mic?-1:0:!phone?-1:0;}
 public Intent registerReceiver(BroadcastReceiver r,IntentFilter f,int flags){receivers.add(r);return null;}public void unregisterReceiver(BroadcastReceiver r){receivers.remove(r);}
 public boolean stopService(Intent i){stops++;manager.running=false;return true;}public ComponentName startForegroundService(Intent i){if(!allowStart)throw new IllegalStateException();starts++;manager.running=true;return null;}
 public void startActivity(Intent i){}public void requestPermissions(String[] p,int request){permissions++;}
}''',
'android/content/Intent.java': '''package android.content;public class Intent {String action,state;public Intent(Context c,Class<?> t){}public Intent(String a){action=a;}
 public String getAction(){return action;}public String getStringExtra(String k){return state;}}''',
'android/content/IntentFilter.java': 'package android.content;public class IntentFilter {public IntentFilter(String s){}}',
'android/content/ComponentName.java': 'package android.content;public class ComponentName {public String getClassName(){return "com.steve.spotifywakeprobe.WakeService";}}',
'android/content/BroadcastReceiver.java': 'package android.content;public abstract class BroadcastReceiver {public abstract void onReceive(Context c,Intent i);}',
'android/app/ActivityManager.java': '''package android.app;public class ActivityManager {public boolean running;public static class RunningServiceInfo {public android.content.ComponentName service=new android.content.ComponentName();}
 public java.util.List<RunningServiceInfo> getRunningServices(int n){return running?java.util.List.of(new RunningServiceInfo()):java.util.List.of();}}''',
'android/app/Activity.java': '''package android.app;public class Activity extends android.content.Context {public android.view.Window window=new android.view.Window();
 public void onCreate(android.os.Bundle b){}protected void onResume(){}protected void onPause(){}protected void onDestroy(){}
 public void setContentView(android.view.View v){}public android.view.Window getWindow(){return window;}public void finish(){}}''',
'android/os/Bundle.java': 'package android.os;public class Bundle {}',
'android/os/Looper.java': 'package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}',
'android/os/Handler.java': '''package android.os;import java.util.*;public class Handler {public static long now;public static Map<Runnable,Long> timers=new LinkedHashMap<>();public Handler(Looper l){}
 public boolean post(Runnable r){return postDelayed(r,0);}public boolean postDelayed(Runnable r,long delay){synchronized(timers){timers.put(r,now+delay);}return true;}
 public void removeCallbacks(Runnable r){synchronized(timers){timers.remove(r);}}public static void advance(long ms){now+=ms;for(int guard=0;guard<1000;guard++){Runnable next=null;synchronized(timers){for(var e:timers.entrySet())if(e.getValue()<=now){next=e.getKey();break;}if(next!=null)timers.remove(next);}if(next==null)return;next.run();}throw new AssertionError("Unbounded callbacks");}
}''',
'android/view/View.java': 'package android.view;public class View {public interface OnClickListener {void onClick(View v);}}',
'android/view/Window.java': 'package android.view;public class Window {public int flags;public void addFlags(int f){flags|=f;}public void clearFlags(int f){flags&=~f;}}',
'android/widget/TextView.java': 'package android.widget;public class TextView extends android.view.View {public String text="";public TextView(android.content.Context c){}public void setText(String s){text=s;}public void setTextSize(int n){}}',
'android/widget/LinearLayout.java': 'package android.widget;public class LinearLayout extends android.view.View {public static final int VERTICAL=1;public LinearLayout(android.content.Context c){}public void setOrientation(int n){}public void setPadding(int a,int b,int c,int d){}public void addView(android.view.View v){}}',
'android/widget/Button.java': 'package android.widget;public class Button extends TextView {public boolean enabled=true;public Button(android.content.Context c){super(c);}public void setOnClickListener(android.view.View.OnClickListener l){}public void setEnabled(boolean b){enabled=b;}}',
'android/widget/ProgressBar.java': 'package android.widget;public class ProgressBar extends android.view.View {public ProgressBar(android.content.Context c,Object a,int s){}public void setMax(int i){}public void setProgress(int i){}}',
'android/widget/ScrollView.java': 'package android.widget;public class ScrollView extends android.view.View {public ScrollView(android.content.Context c){}public void addView(android.view.View v){}}',
'android/media/AudioDeviceInfo.java': '''package android.media;public class AudioDeviceInfo {public static final int TYPE_BLUETOOTH_SCO=7,TYPE_BLE_HEADSET=26,TYPE_BUILTIN_MIC=15;int type,id;
 public AudioDeviceInfo(int t,int i){type=t;id=i;}public int getId(){return id;}public int getType(){return type;}}''',
'android/media/AudioDeviceCallback.java': 'package android.media;public class AudioDeviceCallback {public void onAudioDevicesRemoved(AudioDeviceInfo[] d){}}',
'android/media/AudioAttributes.java': '''package android.media;public class AudioAttributes {public static final int USAGE_VOICE_COMMUNICATION=2,CONTENT_TYPE_SPEECH=1;public static class Builder {public Builder setUsage(int n){return this;}public Builder setContentType(int n){return this;}public AudioAttributes build(){return new AudioAttributes();}}}''',
'android/media/AudioFocusRequest.java': '''package android.media;public class AudioFocusRequest {public AudioManager.OnAudioFocusChangeListener listener;public static class Builder {AudioFocusRequest r=new AudioFocusRequest();public Builder(int n){}public Builder setAudioAttributes(AudioAttributes a){return this;}public Builder setOnAudioFocusChangeListener(AudioManager.OnAudioFocusChangeListener l,android.os.Handler h){r.listener=l;return this;}public AudioFocusRequest build(){return r;}}}''',
'android/media/AudioManager.java': '''package android.media;import java.util.*;public class AudioManager {public static final int MODE_NORMAL=0,MODE_RINGTONE=1,MODE_IN_CALL=2,MODE_IN_COMMUNICATION=3,MODE_CALL_SCREENING=4,AUDIOFOCUS_GAIN_TRANSIENT=2,AUDIOFOCUS_REQUEST_GRANTED=1;
 public interface OnAudioFocusChangeListener {void onAudioFocusChange(int n);}public int mode,inputType=15,clears,normalRequests,focusRequests,abandons,ownCapture,focusResult=1;public boolean available=true,accept=true,silenced;public Runnable duringRoute;public AudioDeviceCallback callback;public AudioFocusRequest lastFocus;
 public int getMode(){return mode;}public void setMode(int n){mode=n;if(n==0)normalRequests++;}public boolean setCommunicationDevice(AudioDeviceInfo d){if(duringRoute!=null)duringRoute.run();return accept;}public void clearCommunicationDevice(){clears++;}
 public List<AudioDeviceInfo> getAvailableCommunicationDevices(){return available?List.of(new AudioDeviceInfo(7,1)):List.of();}public void registerAudioDeviceCallback(AudioDeviceCallback c,android.os.Handler h){callback=c;}public void unregisterAudioDeviceCallback(AudioDeviceCallback c){callback=null;}
 public List<AudioRecordingConfiguration> getActiveRecordingConfigurations(){return ownCapture>0?List.of(new AudioRecordingConfiguration(this)):List.of();}
 public int requestAudioFocus(AudioFocusRequest r){focusRequests++;lastFocus=r;return focusResult;}public int abandonAudioFocusRequest(AudioFocusRequest r){abandons++;return 1;}
}''',
'android/media/AudioRecordingConfiguration.java': 'package android.media;public class AudioRecordingConfiguration {AudioManager a;public AudioRecordingConfiguration(AudioManager a){this.a=a;}public int getClientAudioSessionId(){return a.ownCapture;}public boolean isClientSilenced(){return a.silenced;}}',
'android/media/AudioFormat.java': 'package android.media;public class AudioFormat {public static final int CHANNEL_IN_MONO=16,ENCODING_PCM_16BIT=2;public static class Builder {public Builder setSampleRate(int n){return this;}public Builder setChannelMask(int n){return this;}public Builder setEncoding(int n){return this;}public AudioFormat build(){return new AudioFormat();}}}',
'android/media/MediaRecorder.java': 'package android.media;public class MediaRecorder {public static class AudioSource {public static final int VOICE_COMMUNICATION=7;}}',
'android/media/AudioRecord.java': '''package android.media;public class AudioRecord {public static final int STATE_INITIALIZED=1,RECORDSTATE_RECORDING=3;public static int opens,releases;final AudioManager audio;volatile boolean stopped;
 AudioRecord(AudioManager a){audio=a;opens++;}public static int getMinBufferSize(int r,int c,int e){return 3200;}public int getState(){return 1;}public int getRecordingState(){return stopped?1:3;}public void startRecording(){}
 public AudioRecordingConfiguration getActiveRecordingConfiguration(){return new AudioRecordingConfiguration(audio);}public AudioDeviceInfo getRoutedDevice(){return new AudioDeviceInfo(audio.inputType,2);}
 public synchronized int read(short[] b,int o,int n){while(!stopped)try{wait();}catch(InterruptedException e){return -1;}return -1;}public synchronized void stop(){stopped=true;notifyAll();}public void release(){releases++;}
 public static class Builder {android.content.Context c;public Builder setContext(android.content.Context c){this.c=c;return this;}public Builder setAudioSource(int n){return this;}public Builder setPrivacySensitive(boolean p){return this;}public Builder setAudioFormat(AudioFormat f){return this;}public Builder setBufferSizeInBytes(int n){return this;}public AudioRecord build(){return new AudioRecord(c.audio);}}
}''',
'android/speech/tts/TextToSpeech.java': '''package android.speech.tts;public class TextToSpeech {public static final int SUCCESS=0,ERROR=-1,QUEUE_FLUSH=0;public static int phrases;public static TextToSpeech last;public interface OnInitListener {void onInit(int n);}public OnInitListener init;public UtteranceProgressListener listener;
 public TextToSpeech(android.content.Context c,OnInitListener l){last=this;init=l;}public void setAudioAttributes(android.media.AudioAttributes a){}public void setOnUtteranceProgressListener(UtteranceProgressListener l){listener=l;}public int speak(String s,int q,android.os.Bundle b,String id){phrases++;return 0;}public void stop(){}public void shutdown(){}}''',
'android/speech/tts/UtteranceProgressListener.java': 'package android.speech.tts;public abstract class UtteranceProgressListener {public abstract void onStart(String s);public abstract void onDone(String s);public abstract void onError(String s);}',
'android/telecom/TelecomManager.java': 'package android.telecom;public class TelecomManager {public boolean busy;public boolean isInCall(){return busy;}}',
'android/telephony/TelephonyManager.java': 'package android.telephony;public class TelephonyManager {public static final String ACTION_PHONE_STATE_CHANGED="android.intent.action.PHONE_STATE",EXTRA_STATE="state",EXTRA_STATE_RINGING="RINGING",EXTRA_STATE_OFFHOOK="OFFHOOK";}',
'com/steve/spotifywakeprobe/WakeService.java': 'package com.steve.spotifywakeprobe;public class WakeService {}',
'com/steve/spotifywakeprobe/MainActivity.java': 'package com.steve.spotifywakeprobe;public class MainActivity {}',
}
paths = []
for name, content in files.items():
    path = out / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8')
    paths.append(str(path))
platform = max((Path(os.environ['ANDROID_HOME']) / 'platforms').glob('*/android.jar'))
source = root / 'app/src/debug/java/com/steve/spotifywakeprobe'
classes = out / 'classes'
subprocess.run(['javac', '-cp', str(platform), '-d', str(classes), *paths,
                *map(str, source.glob('*.java')), str(root / 'tests/CarAudioAndroidCheck.java')], check=True)
subprocess.run(['java', '-cp', str(classes) + os.pathsep + str(platform),
                'com.steve.spotifywakeprobe.CarAudioAndroidCheck'], check=True, timeout=30)
