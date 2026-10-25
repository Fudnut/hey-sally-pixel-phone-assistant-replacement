"""Run real car routing and microphone classes against controlled Android/Vosk boundaries."""
from pathlib import Path
import ast,os,subprocess
root=Path(__file__).resolve().parent.parent
out=root/'.tools/car-voice-android-checks'
def boundary_files(name):
    tree=ast.parse((root/'tests'/name).read_text(encoding='utf-8'))
    return ast.literal_eval(next(n.value for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='files' for t in n.targets)))
files=boundary_files('check-car-audio-android.py')
files.pop('com/steve/spotifywakeprobe/WakeService.java',None)
files['android/content/Context.java']=files['android/content/Context.java'].replace('public static final int RECEIVER_EXPORTED=2;', '''public static final int RECEIVER_EXPORTED=2;
 public Context getApplicationContext(){return this;}public android.content.pm.ApplicationInfo getApplicationInfo(){return new android.content.pm.ApplicationInfo();}public java.io.File getFilesDir(){return new java.io.File(".");}''')
files['android/content/pm/ApplicationInfo.java']='package android.content.pm;public class ApplicationInfo {public static final int FLAG_DEBUGGABLE=2;public int flags;}'
files['android/media/AudioManager.java']=files['android/media/AudioManager.java'].replace('public int getMode()', 'public int communicationType=7;public AudioDeviceInfo getCommunicationDevice(){return new AudioDeviceInfo(communicationType,1);}public int getMode()')
files['android/media/AudioRecordingConfiguration.java']=files['android/media/AudioRecordingConfiguration.java'].replace('public int getClientAudioSessionId()', 'public AudioDeviceInfo getAudioDevice(){return new AudioDeviceInfo(a.inputType,2);}public int getClientAudioSource(){return 7;}public AudioFormat getClientFormat(){return new AudioFormat();}public AudioFormat getFormat(){return new AudioFormat();}public android.media.audiofx.AudioEffect.Descriptor[] getEffects(){return new android.media.audiofx.AudioEffect.Descriptor[0];}public int getClientAudioSessionId()')
files['android/media/AudioFormat.java']=files['android/media/AudioFormat.java'].replace('public static final int CHANNEL_IN_MONO','public int getSampleRate(){return 16000;}public static final int CHANNEL_IN_MONO')
files['android/media/audiofx/AudioEffect.java']='package android.media.audiofx;public class AudioEffect {public static final java.util.UUID EFFECT_TYPE_AEC=new java.util.UUID(0,1),EFFECT_TYPE_NS=new java.util.UUID(0,2),EFFECT_TYPE_AGC=new java.util.UUID(0,3);public static class Descriptor {public java.util.UUID type;}}'
files['android/media/AudioRecord.java']='''package android.media;public class AudioRecord {
 public static final int STATE_INITIALIZED=1,RECORDSTATE_RECORDING=3,READ_BLOCKING=0;public static AudioRecord last;public volatile boolean stopped;public boolean released;public volatile int reads;int pending;public int source;AudioManager audio;
 AudioRecord(AudioManager a,int s){audio=a;source=s;last=this;}public static int getMinBufferSize(int r,int c,int e){return 3200;}public int getState(){return 1;}public int getRecordingState(){return stopped?1:3;}public void startRecording(){}public boolean isPrivacySensitive(){return true;}
 public void registerAudioRecordingCallback(java.util.concurrent.Executor e,AudioManager.AudioRecordingCallback c){}public void unregisterAudioRecordingCallback(AudioManager.AudioRecordingCallback c){}
 public AudioRecordingConfiguration getActiveRecordingConfiguration(){return new AudioRecordingConfiguration(audio);}public AudioDeviceInfo getRoutedDevice(){return new AudioDeviceInfo(audio.inputType,2);}
 public int getAudioSessionId(){return 1;}public int getAudioSource(){return source;}public int read(short[] b,int o,int n,int mode){return read(b,o,n);}public synchronized void offer(){pending++;notifyAll();}public synchronized int read(short[] b,int o,int n){reads++;while(pending==0&&!stopped)try{wait();}catch(InterruptedException e){return -1;}if(stopped)return -1;pending--;java.util.Arrays.fill(b,(short)1000);return n;}
 public synchronized void stop(){stopped=true;notifyAll();}public void release(){released=true;}
 public static class Builder {android.content.Context c;int source;public Builder setContext(android.content.Context c){this.c=c;return this;}public Builder setAudioSource(int n){source=n;return this;}public Builder setPrivacySensitive(boolean p){return this;}public Builder setAudioFormat(AudioFormat f){return this;}public Builder setBufferSizeInBytes(int n){return this;}public AudioRecord build(){return new AudioRecord(c.audio,source);}}
}'''
files['android/media/AudioManager.java']=files['android/media/AudioManager.java'].replace('public interface OnAudioFocusChangeListener', 'public static class AudioRecordingCallback {public void onRecordingConfigChanged(java.util.List<AudioRecordingConfiguration> c){}}public interface OnAudioFocusChangeListener')
files['android/media/MediaRecorder.java']=files['android/media/MediaRecorder.java'].replace('VOICE_COMMUNICATION=7','VOICE_COMMUNICATION=7,VOICE_RECOGNITION=6')
files['android/content/Context.java']=files['android/content/Context.java'].replace('public Context getApplicationContext()', 'public java.util.concurrent.Executor getMainExecutor(){return Runnable::run;}public Context getApplicationContext()')
files['org/vosk/Model.java']='package org.vosk;public class Model {}'
files['org/vosk/Recognizer.java']='''package org.vosk;public class Recognizer {public static int accepts;public static boolean failConstruction;public Recognizer(Model m,float r){if(failConstruction)throw new IllegalStateException();}public void reset(){}public boolean acceptWaveForm(short[] a,int n){accepts++;return true;}public String getResult(){return "next";}public void close(){}}'''
files['org/vosk/android/RecognitionListener.java']='package org.vosk.android;public interface RecognitionListener {void onPartialResult(String s);void onResult(String s);void onFinalResult(String s);void onError(Exception e);void onTimeout();}'
files['org/json/JSONObject.java']='package org.json;public class JSONObject {String value="";public JSONObject(){}public JSONObject(String s){value=s;}public JSONObject put(String k,String v){value=v;return this;}public String optString(String k){return value;}public String toString(){return value;}}'
files['com/steve/spotifywakeprobe/VoskWakeDecoder.java']='package com.steve.spotifywakeprobe;final class VoskWakeDecoder {static int closes;static WakeDetector create(org.vosk.Model m){return new WakeDetector(new WakeDetector.Decoder(){public String accept(short[] s,int n){return null;}public void close(){closes++;}});}}'
files['com/steve/spotifywakeprobe/DiagnosticHistory.java']='package com.steve.spotifywakeprobe;class DiagnosticHistory {static void record(android.content.Context c,String s){}}'
paths=[]
for name,content in files.items():
    path=out/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content,encoding='utf-8');paths.append(str(path))
platform=max((Path(os.environ['ANDROID_HOME'])/'platforms').glob('*/android.jar'))
source=root/'app/src/main/java/com/steve/spotifywakeprobe'
production=['CarAudioSession','AndroidCarAudioSession','WakeMicrophone','WakeDetector','WakePhrase']
classes=out/'classes'
subprocess.run(['javac','-cp',str(platform),'-d',str(classes),*paths,*[str(source/(n+'.java')) for n in production],str(root/'tests/CarVoiceAndroidCheck.java')],check=True)
subprocess.run(['java','-cp',str(classes)+os.pathsep+str(platform),'com.steve.spotifywakeprobe.CarVoiceAndroidCheck'],check=True,timeout=30)
