package com.mrdany.torboxtv;

import com.lagradost.cloudstream3.metaproviders.TmdbProvider;
import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function1;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public final class TorBoxProvider extends TmdbProvider {
    public String getName(){return "TorBox RoDub Finder [TV Test]";}
    public String getMainUrl(){return "https://torbox.app";}
    public String getLang(){return "ro";}
    public boolean getHasMainPage(){return false;}
    public boolean getUseMetaLoadResponse(){return true;}
    public Object load(String input,final Continuation continuation){
        try {
            String url=input;
            if(input.startsWith("{")){JSONObject j=new JSONObject(input);url="https://www.themoviedb.org/"+("tv".equals(j.optString("type"))?"tv/":"movie/")+j.getInt("id");}
            Continuation wrapped=new Continuation(){
                public kotlin.coroutines.CoroutineContext getContext(){return continuation.getContext();}
                public void resumeWith(Object result){continuation.resumeWith(TorBoxMetadata.annotate(result));}
            };
            return TorBoxMetadata.annotate(super.load(url,wrapped));
        }catch(Exception e){return null;}
    }
    public Object loadLinks(String data,boolean casting,Function1 subtitles,Function1 callback,Continuation continuation){
        int count=0,scanned=0,candidates=0,discovered=0,eligible=0,cached=0,discoveryFailed=0; boolean diagnostic=false;
        String requested="",outcome="Lookup finished";
        int obtained=0,probed=0;String lastFailure="";
        ExecutorService discoveryPool=null;Future<TorrentDiscovery.Result> search=null;
        try {
            String key=new PrivateKeyStore(TorBoxPlugin.app).read();
            if(key.isEmpty()){TorBoxSetup.show();return false;}
            JSONObject query=new JSONObject(data);
            diagnostic=!query.has("originalTitle");
            requested=query.optString("movieName","Requested title");
            if(query.optInt("season",-1)<0&&query.optInt("releaseYear",0)<=0){
                outcome="Movie metadata could not be completed";
                query=completeMovieQuery(query);
            }
            requested=query.optString("movieName",requested)+(query.optInt("releaseYear",0)>0?" ("+query.optInt("releaseYear")+")":"");
            outcome="Library request failed or timed out";
            String groups=TorBoxPlugin.app.getSharedPreferences("torbox_tv_options",0).getString("groups",TorBoxSetup.GROUPS);
            final JSONObject searchQuery=new JSONObject(query.toString());
            discoveryPool=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"TorBox-search");t.setDaemon(true);return t;});
            search=discoveryPool.submit(()->TorrentDiscovery.discover(searchQuery,groups));
            TorBoxClient client=new TorBoxClient(key);Set<Long> seen=new HashSet<>();
            Set<String> returned=new HashSet<>();
            // Three pages leave time for global cached discovery on large Libraries.
            for(int offset=0;offset<3000&&count<3;offset+=1000){
                client.check();JSONArray page=client.page(offset,1000);int newItems=0;
                for(int i=0;i<page.length()&&count<3;i++){
                    JSONObject item=page.getJSONObject(i);long id=item.optLong("id",-1);if(id<0||!seen.add(id))continue;newItems++;
                    scanned++;
                    List<LibraryMatcher.Match> found=LibraryMatcher.find(item,query,groups);if(found.isEmpty())continue;
                    candidates+=found.size();
                    // Confirm current per-item completion/files rather than trusting an index flag.
                    found=LibraryMatcher.find(client.item(id),query,groups);
                    if(found.isEmpty())lastFailure="Library refresh: item not ready or exact file no longer matches";
                    for(LibraryMatcher.Match match:found){
                        if(count>=3)break;
                        String stage="Download-link API";
                        try{client.check();String url=client.link(match.torrent,match.file);obtained++;
                            stage="Media probe";TorBoxClient.probe(url);probed++;
                            stage="Nuvio link callback";callback.invoke(link(match,url));returned.add(item.optString("hash","").toLowerCase(Locale.ROOT)+":"+match.file);count++;}
                        catch(Exception error){lastFailure=stage+": "+TorBoxClient.safeError(error);}
                    }
                }
                if(page.length()<1000||newItems==0)break;
            }
            if(count<3){
                outcome="Public discovery unavailable or timed out";
                TorrentDiscovery.Result found=search.get(13,TimeUnit.SECONDS);
                discovered=found.received;eligible=found.candidates.size();discoveryFailed=found.failed;
                outcome="TorBox cache verification failed or timed out";
                CachedDiscoveryResolver.Stats stats=CachedDiscoveryResolver.resolve(found.candidates,query,groups,client,3-count,returned,(match,url)->callback.invoke(link(match,url)));
                cached=stats.cached;candidates+=stats.exactFiles;count+=stats.links;
                obtained+=stats.obtained;probed+=stats.media;if(!stats.failure.isEmpty())lastFailure=stats.failure;
            }
            outcome=count>0?"Verified media links returned":candidates==0?"No exact eligible Romanian file found in the checked Library/cache candidates":"Matched files did not yield a verified media link";
        }catch(TorBoxClient.AuthenticationFailure invalidKey){outcome="TorBox authorization failed";lastFailure=TorBoxClient.safeError(invalidKey);TorBoxSetup.show();}
        catch(Exception error){lastFailure=outcome+": "+TorBoxClient.safeError(error);}
        finally{if(search!=null)search.cancel(true);if(discoveryPool!=null)discoveryPool.shutdownNow();}
        outcome+="\nDownload URLs obtained: "+obtained+"; media checks passed: "+probed;
        if(!lastFailure.isEmpty())outcome+="\nLast failure: "+lastFailure;
        android.util.Log.i("TorBoxTV","RoDub lookup returned "+count+" verified file link(s)");
        if(diagnostic)showDiagnostic(requested,scanned,candidates,count,outcome,discovered,eligible,cached,discoveryFailed);
        else if(TorBoxPlugin.app!=null)TorBoxPlugin.app.getSharedPreferences("torbox_tv_options",0).edit()
            .putString("last_lookup_summary",summary(requested,scanned,candidates,count,outcome,discovered,eligible,cached,discoveryFailed)).apply();
        return count>0;
    }
    JSONObject completeMovieQuery(JSONObject query)throws Exception {
        if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())throw new Exception("Worker thread required");
        final CountDownLatch ready=new CountDownLatch(1);final Object[] result={null};
        final kotlin.coroutines.CoroutineContext context=(kotlin.coroutines.CoroutineContext)Class.forName("kotlin.coroutines.EmptyCoroutineContext").getField("INSTANCE").get(null);
        Continuation receiver=new Continuation(){
            public kotlin.coroutines.CoroutineContext getContext(){return context;}
            public void resumeWith(Object value){result[0]=value;ready.countDown();}
        };
        Object initial=load(new JSONObject().put("id",query.getInt("tmdbID")).put("type","movie").toString(),receiver);
        if(initial!=null&&!initial.getClass().getName().contains("CoroutineSingletons")){result[0]=initial;ready.countDown();}
        if(!ready.await(15,TimeUnit.SECONDS)||result[0]==null)throw new Exception("Metadata unavailable");
        JSONObject metadata=new JSONObject((String)result[0].getClass().getMethod("getDataUrl").invoke(result[0]));
        if(metadata.optInt("tmdbID",-1)!=query.getInt("tmdbID")||metadata.optInt("releaseYear",0)<=0)throw new Exception("Metadata identity incomplete");
        JSONObject enriched=new JSONObject(query.toString());
        for(String k:Arrays.asList("movieName","originalTitle","releaseYear"))if(metadata.has(k))enriched.put(k,metadata.get(k));
        return enriched;
    }
    static void showDiagnostic(String title,int scanned,int candidates,int links,String outcome,int discovered,int eligible,int cached,int failed){
        final android.app.Activity a=TorBoxPlugin.activity.get();if(a==null||a.isFinishing())return;
        String previous=TorBoxPlugin.app.getSharedPreferences("torbox_tv_options",0).getString("last_lookup_summary","");
        final String message=(previous.isEmpty()?summary(title,scanned,candidates,links,outcome,discovered,eligible,cached,failed):"LAST TITLE LOOKUP\n"+previous+"\n\nBUILT-IN TEST\n"+title+": "+links+" links. "+outcome)
            +"\n\nThe built-in Test uses The Matrix. Normal lookups search the selected title. Cached-only registrations can appear in your TorBox Library.";
        a.runOnUiThread(()->new android.app.AlertDialog.Builder(a).setTitle("TorBox Library diagnostic").setMessage(message).setPositiveButton("OK",null).show());
    }
    static String summary(String title,int scanned,int candidates,int links,String outcome,int discovered,int eligible,int cached,int failed){
        return title+"\nLibrary items checked (up to 3000): "+scanned+"\nPublic index candidates: "+discovered+"\nEligible Romanian candidates: "+eligible+"\nCached candidates: "+cached+"\nMatching RO file candidates: "+candidates+"\nVerified links: "+links+"\nDiscovery service failures: "+failed+"\n"+outcome;
    }
    static Object link(LibraryMatcher.Match m,String url)throws Exception {
        String lower=m.name.toLowerCase(Locale.ROOT);int quality=lower.contains("2160p")?2160:lower.contains("1080p")?1080:lower.contains("720p")?720:0;
        return Class.forName("com.lagradost.cloudstream3.utils.ExtractorLink")
            .getConstructor(String.class,String.class,String.class,String.class,int.class,boolean.class,Map.class,String.class)
            .newInstance("TorBox RoDub Finder",m.confidence+" | TorBox cached",url,"",quality,false,Collections.emptyMap(),null);
    }
}
