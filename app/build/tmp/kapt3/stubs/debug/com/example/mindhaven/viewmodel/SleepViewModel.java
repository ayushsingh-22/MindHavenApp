package com.example.mindhaven.viewmodel;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000J\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\t\n\u0002\b\f\n\u0002\u0010 \n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\f\b\u0007\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\b\u0010*\u001a\u00020+H\u0002J\u0006\u0010,\u001a\u00020+J\b\u0010-\u001a\u00020+H\u0002J\b\u0010.\u001a\u00020+H\u0014J\u0006\u0010/\u001a\u00020+J\u0010\u00100\u001a\u00020+2\b\b\u0002\u00101\u001a\u00020\u001aJ\u000e\u00102\u001a\u00020+2\u0006\u00103\u001a\u00020\u0013J\u0010\u00104\u001a\u00020+2\u0006\u00105\u001a\u00020\u001aH\u0002J\u0006\u00106\u001a\u00020+R\u0010\u0010\u0005\u001a\u0004\u0018\u00010\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000R+\u0010\t\u001a\u00020\b2\u0006\u0010\u0007\u001a\u00020\b8F@BX\u0086\u008e\u0002\u00a2\u0006\u0012\n\u0004\b\r\u0010\u000e\u001a\u0004\b\t\u0010\n\"\u0004\b\u000b\u0010\fR\u0010\u0010\u000f\u001a\u0004\u0018\u00010\u0010X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0011\u001a\u0004\u0018\u00010\u0012X\u0082\u000e\u00a2\u0006\u0002\n\u0000R/\u0010\u0014\u001a\u0004\u0018\u00010\u00132\b\u0010\u0007\u001a\u0004\u0018\u00010\u00138F@BX\u0086\u008e\u0002\u00a2\u0006\u0012\n\u0004\b\u0019\u0010\u000e\u001a\u0004\b\u0015\u0010\u0016\"\u0004\b\u0017\u0010\u0018R+\u0010\u001b\u001a\u00020\u001a2\u0006\u0010\u0007\u001a\u00020\u001a8F@FX\u0086\u008e\u0002\u00a2\u0006\u0012\n\u0004\b \u0010!\u001a\u0004\b\u001c\u0010\u001d\"\u0004\b\u001e\u0010\u001fR+\u0010\"\u001a\u00020\b2\u0006\u0010\u0007\u001a\u00020\b8F@BX\u0086\u008e\u0002\u00a2\u0006\u0012\n\u0004\b%\u0010\u000e\u001a\u0004\b#\u0010\n\"\u0004\b$\u0010\fR\u0017\u0010&\u001a\b\u0012\u0004\u0012\u00020\u00130\'\u00a2\u0006\b\n\u0000\u001a\u0004\b(\u0010)\u00a8\u00067"}, d2 = {"Lcom/example/mindhaven/viewmodel/SleepViewModel;", "Landroidx/lifecycle/AndroidViewModel;", "application", "Landroid/app/Application;", "(Landroid/app/Application;)V", "activeTimerJob", "Lkotlinx/coroutines/Job;", "<set-?>", "", "isPlaying", "()Z", "setPlaying", "(Z)V", "isPlaying$delegate", "Landroidx/compose/runtime/MutableState;", "mediaService", "Lcom/example/mindhaven/service/MusicService;", "mediaServiceConnection", "Landroid/content/ServiceConnection;", "Lcom/example/mindhaven/model/SoundItem;", "selectedSound", "getSelectedSound", "()Lcom/example/mindhaven/model/SoundItem;", "setSelectedSound", "(Lcom/example/mindhaven/model/SoundItem;)V", "selectedSound$delegate", "", "selectedTimer", "getSelectedTimer", "()J", "setSelectedTimer", "(J)V", "selectedTimer$delegate", "Landroidx/compose/runtime/MutableLongState;", "showLottieAnimation", "getShowLottieAnimation", "setShowLottieAnimation", "showLottieAnimation$delegate", "sleepSounds", "", "getSleepSounds", "()Ljava/util/List;", "cancelTimer", "", "clearSelection", "connectToMediaService", "onCleared", "pause", "play", "timerDuration", "selectSound", "sound", "startTimer", "duration", "stop", "app_debug"})
@androidx.media3.common.util.UnstableApi()
public final class SleepViewModel extends androidx.lifecycle.AndroidViewModel {
    @org.jetbrains.annotations.Nullable()
    private android.content.ServiceConnection mediaServiceConnection;
    @org.jetbrains.annotations.Nullable()
    private com.example.mindhaven.service.MusicService mediaService;
    @org.jetbrains.annotations.Nullable()
    private kotlinx.coroutines.Job activeTimerJob;
    @org.jetbrains.annotations.NotNull()
    private final androidx.compose.runtime.MutableLongState selectedTimer$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.compose.runtime.MutableState isPlaying$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.compose.runtime.MutableState selectedSound$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.compose.runtime.MutableState showLottieAnimation$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<com.example.mindhaven.model.SoundItem> sleepSounds = null;
    
    public SleepViewModel(@org.jetbrains.annotations.NotNull()
    android.app.Application application) {
        super(null);
    }
    
    public final long getSelectedTimer() {
        return 0L;
    }
    
    public final void setSelectedTimer(long p0) {
    }
    
    public final boolean isPlaying() {
        return false;
    }
    
    private final void setPlaying(boolean p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.example.mindhaven.model.SoundItem getSelectedSound() {
        return null;
    }
    
    private final void setSelectedSound(com.example.mindhaven.model.SoundItem p0) {
    }
    
    public final boolean getShowLottieAnimation() {
        return false;
    }
    
    private final void setShowLottieAnimation(boolean p0) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.example.mindhaven.model.SoundItem> getSleepSounds() {
        return null;
    }
    
    private final void connectToMediaService() {
    }
    
    public final void selectSound(@org.jetbrains.annotations.NotNull()
    com.example.mindhaven.model.SoundItem sound) {
    }
    
    public final void play(long timerDuration) {
    }
    
    public final void pause() {
    }
    
    public final void stop() {
    }
    
    private final void startTimer(long duration) {
    }
    
    private final void cancelTimer() {
    }
    
    public final void clearSelection() {
    }
    
    @java.lang.Override()
    protected void onCleared() {
    }
}