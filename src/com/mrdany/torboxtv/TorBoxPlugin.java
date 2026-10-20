package com.mrdany.torboxtv;

import android.app.Activity;
import android.content.Context;
import com.lagradost.cloudstream3.metaproviders.TmdbProvider;
import java.lang.ref.WeakReference;
import java.util.*;

/** Separate native TV provider. No hosting service; acquisition is cached-only. */
public final class TorBoxPlugin {
    static Context app;
    static WeakReference<Activity> activity = new WeakReference<>(null);
    public void load(Context context) {
        app = context.getApplicationContext();
        if (context instanceof Activity) activity = new WeakReference<>((Activity) context);
    }
    public List<TmdbProvider> getRegisteredMainAPIs() {
        return Collections.<TmdbProvider>singletonList(new TorBoxProvider());
    }
    public List<Object> getRegisteredExtractorAPIs() { return Collections.emptyList(); }
}
