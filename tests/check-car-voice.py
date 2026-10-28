"""Pure car-session checks; Android integration is checked separately and on the Pixel."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parent.parent
out=root/'.tools/car-voice-checks'
out.mkdir(parents=True,exist_ok=True)
subprocess.run(['javac','-d',str(out),str(root/'app/src/main/java/com/steve/spotifywakeprobe/CarAudioSession.java'),str(root/'tests/CarAudioSessionCheck.java')],check=True)
subprocess.run(['java','-cp',str(out),'com.steve.spotifywakeprobe.CarAudioSessionCheck'],check=True)
