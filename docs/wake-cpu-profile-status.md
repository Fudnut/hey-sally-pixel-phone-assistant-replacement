# Wake CPU profile: status (2026-10-26)

- Not measured yet. The phone was plugged into the laptop, while this session ran on the RDP host PC, so adb saw nothing.
- Steve is moving the work to the laptop.
- Note: the old 21% (`.tools/device-wake-trial/cpu-optimized-end.json`) is one 101 s window, charging, with a synthetic wake attempt in it. It may overstate idle listening.
- Plan: check `pidof com.steve.spotifywakeprobe`, the cpufreq policies (related_cpus, max/cur freq) and simpleperf. Then sample `/proc/<pid>/task/*/stat` (utime+stime, field 39 last CPU) over 5 min with the room quiet, plus `top -H`. Optionally run `simpleperf record -g` on the debuggable build. Write `claude-audit/research/WAKE_CPU_PROFILE.md` and a HANDOFF entry. No commit.
- Threads to expect: `WakeMicrophone` (AudioRecord read + Vosk acceptWaveForm, the same thread), main, binder, and audio callback threads.
