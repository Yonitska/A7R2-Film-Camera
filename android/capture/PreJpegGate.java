package org.hongen.sonylutprobe;

/** Save/ownership protocol. Grading may overlap the original save, but
 * the second store requires original confirmation; a new shot requires rearm. */
final class PreJpegGate {
    enum State { NEW, PREVIEW, REGISTERING, DEFAULT_REGISTERED, DEFAULT_READY, CONFIGURING, INITIALIZED, READY, ARMED, RAW, ORIGINAL_SAVING, ORIGINAL_CONFIRMED, SAVING, SAVED, REARMING, FAILED }
    private State state=State.NEW;
    private long shot;
    synchronized State state(){return state;}
    synchronized void preview(){move(State.NEW,State.PREVIEW);}
    /** Recipe capture uses Sony's direct JPEG shutter; no RAW sequence exists,
     * so preview readiness alone unlocks the shutter. */
    synchronized void previewReady(){move(State.PREVIEW,State.READY);}
    synchronized void registering(){move(State.PREVIEW,State.REGISTERING);}
    synchronized void defaultRegistered(){move(State.REGISTERING,State.DEFAULT_REGISTERED);}
    synchronized void defaultUnlocked(){move(State.DEFAULT_REGISTERED,State.DEFAULT_READY);}
    synchronized void defaultsComplete(){move(State.DEFAULT_READY,State.INITIALIZED);}
    synchronized void configuring(){move(State.DEFAULT_READY,State.CONFIGURING);}
    synchronized void registered(boolean initOnly){move(State.CONFIGURING,initOnly?State.INITIALIZED:State.READY);}
    synchronized boolean arm(){if(state!=State.READY)return false;state=State.ARMED;shot++;return true;}
    // Sony STATUS_CANCELED ends an attempt before RAW ownership transfers.
    // Do not unlock an in-flight develop/save or revive a terminal failure.
    synchronized boolean cancelShutter(){if(state!=State.ARMED)return false;state=State.READY;return true;}
    synchronized long shot(){return shot;}
    synchronized boolean busyShot(long id){return id==shot&&busy();}
    synchronized boolean beginRearm(long id){if(id!=shot||state!=State.SAVED)return false;state=State.REARMING;return true;}
    synchronized boolean rearmed(long id){if(id!=shot||state!=State.REARMING)return false;state=State.READY;return true;}
    synchronized void raw(){move(State.ARMED,State.RAW);}
    synchronized void saving(){move(State.RAW,State.SAVING);}
    /** Direct JPEG store: the camera wrote the only file, so ARMED moves
     * straight to SAVING and back to READY after the receipt is catalogued.
     * Shot-scoped: a late verification from the previous shot must never
     * consume the gate of a newer capture (the old code moved the new shot's
     * ARMED gate to SAVING, so its scan was skipped and the finder stayed on
     * the processing page). */
    synchronized boolean recipeStoring(long id){if(id!=shot||state!=State.ARMED)return false;state=State.SAVING;return true;}
    synchronized boolean recipeReady(long id){if(id!=shot||state!=State.SAVING)return false;state=State.READY;return true;}
    synchronized void recipeStoring(){move(State.ARMED,State.SAVING);}
    synchronized void recipeReady(){move(State.SAVING,State.READY);}
    synchronized void savingOriginal(){move(State.RAW,State.ORIGINAL_SAVING);}
    synchronized void originalSaved(){move(State.ORIGINAL_SAVING,State.ORIGINAL_CONFIRMED);}
    synchronized void savingGraded(){move(State.ORIGINAL_CONFIRMED,State.SAVING);}
    synchronized void saved(){move(State.SAVING,State.SAVED);}
    synchronized void fail(){state=State.FAILED;}
    synchronized boolean busy(){return state==State.ARMED||state==State.RAW||state==State.ORIGINAL_SAVING||state==State.ORIGINAL_CONFIRMED||state==State.SAVING;}
    private void move(State from,State to){if(state!=from)throw new IllegalStateException("expected "+from+", got "+state);state=to;}
}
