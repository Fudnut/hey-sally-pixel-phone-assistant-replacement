from pathlib import Path
import zipfile,subprocess,os
out=Path('.tools/connection-checks');out.mkdir(exist_ok=True)
with zipfile.ZipFile('app/libs/spotify-app-remote-release-0.8.0.aar') as z: (out/'spotify.jar').write_bytes(z.read('classes.jar'))
files={
'android/os/Looper.java': 'package android.os; public class Looper { public static Looper getMainLooper(){return new Looper();} }',
'android/os/Handler.java': '''package android.os;
import java.util.*;
public class Handler {
 public Handler(Looper looper){} static long now; static Map<Runnable,Long> timers=new LinkedHashMap<>();
 public boolean post(Runnable r){r.run();return true;}
 public boolean postDelayed(Runnable r,long delay){timers.put(r,now+delay);return true;}
 public void removeCallbacks(Runnable r){timers.remove(r);}
 public static void reset(){now=0;timers.clear();}
 public static void advance(long ms){now+=ms;for(var entry:new ArrayList<>(timers.entrySet())) if(entry.getValue()<=now && timers.remove(entry.getKey())!=null)entry.getKey().run();}
}''',
'android/os/SystemClock.java': 'package android.os; public class SystemClock { public static long elapsedRealtime(){return Handler.now;} }',
'com/steve/spotifywakeprobe/DiagnosticHistory.java': 'package com.steve.spotifywakeprobe; import android.content.Context; import java.util.*; final class DiagnosticHistory { static List<String> events=new ArrayList<>(); static void record(Context c,String event){events.add(event);} }',
'android/util/Log.java': '''package android.util; public class Log { public static String text=""; public static int i(String tag,String s){text+=s;return 0;} public static int e(String tag,String s){text+=s;return 0;} public static int w(String tag,String s){text+=s;return 0;} }''',
'com/spotify/android/appremote/api/ConnectionParams.java': '''package com.spotify.android.appremote.api; public class ConnectionParams { public static class Builder { public Builder(String id){} public Builder setRedirectUri(String uri){return this;} public Builder showAuthView(boolean b){return this;} public ConnectionParams build(){return new ConnectionParams();} } }''',
'com/spotify/android/appremote/api/Connector.java': '''package com.spotify.android.appremote.api; public class Connector { public interface ConnectionListener { void onConnected(SpotifyAppRemote remote); void onFailure(Throwable e); } }''',
'com/spotify/android/appremote/api/SpotifyAppRemote.java': '''package com.spotify.android.appremote.api; import android.content.Context;
public class SpotifyAppRemote { public static Connector.ConnectionListener listener; public static int disconnected; public static boolean failSynchronously;
 public static void connect(Context c,ConnectionParams p,Connector.ConnectionListener l){listener=l;if(failSynchronously)throw new IllegalStateException("private-error-text");}
 public static void disconnect(SpotifyAppRemote r){disconnected++;} public PlayerApi getPlayerApi(){return null;}
}''',
'com/steve/spotifywakeprobe/SpotifyOAuth.java': '''package com.steve.spotifywakeprobe; import android.content.Context; final class SpotifyOAuth { static final String REDIRECT="local-test"; static String accessToken(Context c,String id){return "unused";} }''',
'com/steve/spotifywakeprobe/ConnectionCheck.java': '''package com.steve.spotifywakeprobe;
import android.os.Handler; import android.util.Log; import com.spotify.android.appremote.api.*; import java.util.*; import java.util.concurrent.atomic.AtomicBoolean; import java.util.function.Consumer;
public class ConnectionCheck {
 static int ready; static List<String> reports=new ArrayList<>(); static AtomicBoolean finished;
 static void start(boolean authorize) throws Exception {
  Handler.reset(); ready=0; reports.clear(); finished=new AtomicBoolean(); SpotifyAppRemote.disconnected=0; Log.text=""; DiagnosticHistory.events.clear();
  var connect=SpotifyController.class.getDeclaredMethod("connect",android.content.Context.class,String.class,boolean.class,Consumer.class,Consumer.class,AtomicBoolean.class);
  connect.setAccessible(true); connect.invoke(null,null,"test",authorize,(Consumer<SpotifyAppRemote>)r->ready++, (Consumer<String>)reports::add,finished);
 }
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args) throws Exception {
  start(false); Handler.advance(11999);check(reports.isEmpty(),"Connect timed out too early");Handler.advance(1);
  check(finished.get() && reports.size()==1,"Unresponsive playback connection must time out after 12 seconds");
  check(DiagnosticHistory.events.contains("SPOTIFY_CONNECT_RESULT TIMEOUT ms=12000 mode=PLAYBACK"),"Timeout must retain safe elapsed time in phone diagnostics");
  SpotifyAppRemote.listener.onConnected(new SpotifyAppRemote());check(ready==0 && SpotifyAppRemote.disconnected==1,"Late connection must disconnect without executing playback");
  check(DiagnosticHistory.events.contains("SPOTIFY_CONNECT_RESULT IGNORED ms=12000 mode=PLAYBACK"),"Late connection must be measurable without executing playback");
  SpotifyAppRemote.listener.onFailure(new IllegalStateException("private-error-text"));check(reports.size()==1,"Late failure must not report twice");
  start(false);SpotifyAppRemote.listener.onFailure(new com.spotify.android.appremote.api.error.UserNotAuthorizedException("private authorization details", null));check(CommandFailure.code(reports.get(0))==CommandFailure.Reason.AUTH,"SDK authorization error must retain safe AUTH code");
  start(false);Handler.advance(11000);SpotifyAppRemote.listener.onConnected(new SpotifyAppRemote());Handler.advance(1000);
  check(ready==1 && reports.isEmpty() && !finished.get(),"Successful connection must cancel its timer");
  check(DiagnosticHistory.events.contains("SPOTIFY_CONNECT_RESULT CONNECTED ms=11000 mode=PLAYBACK"),"Successful connection duration must be recorded");
  finished.set(true);SpotifyAppRemote.listener.onFailure(new IllegalStateException("private-error-text"));check(reports.isEmpty(),"Completed command must suppress disconnect error");
  start(false);SpotifyAppRemote.listener.onFailure(new IllegalStateException("private-error-text"));Handler.advance(12000);
  check(finished.get() && reports.size()==1 && !reports.get(0).contains("private-error-text") && !Log.text.contains("private-error-text"),"Failure must be single and contain no error text");
  SpotifyAppRemote.failSynchronously=true;start(false);Handler.advance(12000);check(reports.size()==1,"Synchronous connection exception must report once");SpotifyAppRemote.failSynchronously=false;
  start(true);Handler.advance(12000);check(reports.isEmpty(),"Manual authorization must allow time for consent");Handler.advance(108000);check(reports.size()==1,"Manual authorization must still be bounded");
  check(DiagnosticHistory.events.contains("SPOTIFY_CONNECT_RESULT TIMEOUT ms=120000 mode=AUTH"),"Consent timing must be separate from playback timing");
  for (Throwable error : new Throwable[]{new com.spotify.android.appremote.api.error.UserNotAuthorizedException("private authorization details", null), new java.io.IOException("private URL"), new java.net.SocketTimeoutException("private host")}) {
   Handler.reset();reports.clear();SpotifyController.authorizeRemote(null,"test",reports::add);SpotifyAppRemote.listener.onFailure(error);
   check(reports.size()==1 && reports.get(0).contains("("+CommandFailure.reason(error)+")") && !reports.get(0).contains("private"),"Setup screen must retain the safe SDK/network/timeout reason without provider messages");
  }
  Handler.reset();reports.clear();SpotifyController.authorizeRemote(null,"test",reports::add);Handler.advance(120000);
  check(reports.size()==1 && reports.get(0).contains("(TIMEOUT)"),"Setup consent timeout must be identifiable");
  System.out.println("Connection timeout, late callbacks, success cancellation, sanitized failure and authorization checks passed");
 }
}'''
}
files['android/content/Context.java'] = """package android.content;
public class Context {
 public static final int MODE_PRIVATE=0;
 public Context getApplicationContext(){return this;}
 public Prefs getSharedPreferences(String n,int m){return new Prefs();}
 public static class Prefs { public String getString(String n,String d){return "test";} }
 public android.content.pm.PackageManager getPackageManager(){return null;}
 public <T>T getSystemService(Class<T> c){return c.cast(new android.media.AudioManager());}
 public void startActivity(Intent i){}
}"""
files['android/media/AudioManager.java'] = "package android.media; public class AudioManager { public static int keys; public void dispatchMediaKeyEvent(android.view.KeyEvent e){keys++;} }"
files['android/view/KeyEvent.java'] = "package android.view; public class KeyEvent { public static final int ACTION_DOWN=0,ACTION_UP=1,KEYCODE_MEDIA_PLAY=126; public KeyEvent(int a,int b){} }"
files['com/spotify/protocol/client/CallResult.java'] = """package com.spotify.protocol.client;
public class CallResult<T> {
 public java.util.function.Consumer<T> success; public java.util.function.Consumer<Throwable> error;
 public CallResult<T> setResultCallback(java.util.function.Consumer<T> c){success=c;return this;}
 public CallResult<T> setErrorCallback(java.util.function.Consumer<Throwable> c){error=c;return this;}
 public void succeed(T t){success.accept(t);} public void fail(){error.accept(new java.io.IOException("private URL"));}
}"""
files['com/spotify/android/appremote/api/PlayerApi.java'] = """package com.spotify.android.appremote.api;
import com.spotify.protocol.client.CallResult; import com.spotify.protocol.types.*;
public class PlayerApi {
 public static int calls; public static CallResult<Empty> last; public static CallResult<PlayerState> state;
 private CallResult<Empty> call(){calls++;return last=new CallResult<>();}
 public CallResult<Empty> play(String u){return call();} public CallResult<Empty> pause(){return call();}
 public CallResult<Empty> resume(){return call();} public CallResult<Empty> skipNext(){return call();}
 public CallResult<Empty> skipPrevious(){return call();}
 public CallResult<PlayerState> getPlayerState(){calls++;return state=new CallResult<>();}
}"""
files['com/spotify/android/appremote/api/SpotifyAppRemote.java']=files['com/spotify/android/appremote/api/SpotifyAppRemote.java'].replace('return null;', 'return new PlayerApi();')
key='com/steve/spotifywakeprobe/ConnectionCheck.java'
extra="""
  android.content.Context context=new android.content.Context();
  Handler.reset();reports.clear();PlayerApi.calls=0;
  Runnable old=SpotifyController.execute(context,VoiceCommand.parse("resume"),List.of(),reports::add);
  var oldListener=SpotifyAppRemote.listener;old.run();
  Runnable newer=SpotifyController.execute(context,VoiceCommand.parse("pause"),List.of(),reports::add);
  oldListener.onConnected(new SpotifyAppRemote());check(PlayerApi.calls==0,"Canceled old connection must not dispatch playback");
  SpotifyAppRemote.listener.onConnected(new SpotifyAppRemote());check(PlayerApi.calls==1,"New pause must dispatch");
  var pause=PlayerApi.last;newer.run();pause.succeed(null);pause.fail();
  check(reports.isEmpty(),"Canceled callbacks must not report");
  Handler.reset();reports.clear();PlayerApi.calls=0;android.media.AudioManager.keys=0;
  Runnable playing=SpotifyController.execute(context,VoiceCommand.parse("resume"),List.of(),reports::add);
  SpotifyAppRemote.listener.onConnected(new SpotifyAppRemote());var late=PlayerApi.last;
  Handler.advance(30000);check(reports.size()==1 && CommandFailure.code(reports.get(0))==CommandFailure.Reason.TIMEOUT,"Connected unfinished operation needs a completion deadline");
  late.succeed(null);check(android.media.AudioManager.keys==0 && reports.size()==1,"Late success must not send media keys or report twice");
  Handler.reset();reports.clear();PlayerApi.calls=0;
  Runnable previous=SpotifyController.execute(context,VoiceCommand.parse("previous"),List.of(),reports::add);
  SpotifyAppRemote.listener.onConnected(new SpotifyAppRemote());previous.run();
  PlayerApi.state.succeed(new com.spotify.protocol.types.PlayerState(null,false,1,5000,null,null));
  check(PlayerApi.calls==1,"Canceled previous state callback must not issue skips");
  Handler.reset();reports.clear();
  SpotifyController.execute(context,VoiceCommand.parse("pause"),List.of(),reports::add);
  SpotifyAppRemote.listener.onConnected(new SpotifyAppRemote());PlayerApi.last.succeed(null);Handler.advance(30000);
  check(reports.size()==1 && reports.get(0).startsWith("Spotify: "),"Successful command must cancel deadline");
  Handler.reset();reports.clear();PlayerApi.calls=0;
  Runnable resolved=SpotifyController.playPlaylist(context,"spotify:playlist:old",reports::add);
  resolved.run();var connectedBefore=SpotifyAppRemote.listener;
  var playMethod=SpotifyController.class.getDeclaredMethod("play",android.content.Context.class,String.class,VoiceCommand.class,String.class,resolved.getClass());
  playMethod.setAccessible(true);playMethod.invoke(null,context,"test",VoiceCommand.parse("play song old"),"spotify:track:old",resolved);
  check(SpotifyAppRemote.listener==connectedBefore,"Canceled search completion must not start a connection");
  Runnable newSong=SpotifyController.playPlaylist(context,"spotify:playlist:new",reports::add);
  connectedBefore.onConnected(new SpotifyAppRemote());check(PlayerApi.calls==0,"Old connection cannot replace a newer song");
  SpotifyAppRemote.listener.onConnected(new SpotifyAppRemote());check(PlayerApi.calls==1,"Current song must dispatch");
  newSong.run();PlayerApi.last.succeed(null);check(reports.isEmpty(),"Stop cancellation must suppress late completion");
  var sameName=SpotifyController.class.getDeclaredMethod("sameName",String.class,String.class);sameName.setAccessible(true);
  check(!(boolean)sameName.invoke(null,"日本","韓国"),"Distinct non-Latin names must not collapse to empty matches");
  check((boolean)sameName.invoke(null,"Café","Cafe"),"Accented names must match their unaccented form");
  check((boolean)sameName.invoke(null,"日本","日本"),"Identical non-Latin names must remain matchable");
  check(!(boolean)sameName.invoke(null,"!!!","???"),"Empty normalized names must not match");
  System.out.println("Ordinary cancellation, stale callbacks, media-key suppression and completion deadline checks passed");
"""
files[key]=files[key].replace('  System.out.println("Connection timeout',extra+'  System.out.println("Connection timeout')

paths=[]
for name,content in files.items():
 p=out/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content,encoding='utf-8');paths.append(str(p))
sdk=Path(os.environ['ANDROID_HOME']); platform=max((sdk/'platforms').glob('*/android.jar'), key=lambda p: int(p.parent.name.split('-')[1].split('.')[0])); cp=str(out/'spotify.jar')+os.pathsep+str(platform)
source=Path('app/src/main/java/com/steve/spotifywakeprobe')
subprocess.run(['javac','-cp',cp,'-d',str(out/'classes'),*paths,*[str(source/n) for n in ['SpotifyController.java','VoiceCommand.java','PlaylistBrowse.java','CommandFailure.java']]],check=True)
subprocess.run(['java','-cp',str(out/'classes')+os.pathsep+cp,'com.steve.spotifywakeprobe.ConnectionCheck'],check=True)
