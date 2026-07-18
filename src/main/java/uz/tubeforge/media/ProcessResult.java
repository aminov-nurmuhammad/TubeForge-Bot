package uz.tubeforge.media;

public record ProcessResult(int exitCode, String output, boolean timedOut, boolean cancelled) {
    public boolean successful() { return exitCode == 0 && !timedOut && !cancelled; }
}
