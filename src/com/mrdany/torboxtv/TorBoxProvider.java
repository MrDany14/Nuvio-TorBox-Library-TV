package com.mrdany.torboxtv;

import com.lagradost.cloudstream3.metaproviders.TmdbProvider;
import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function1;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public final class TorBoxProvider extends TmdbProvider {
    public String getName(){return "TorBox Library [TV Test]";}
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
        int count=0,scanned=0,candidates=0; boolean diagnostic=false;
        String requested="",outcome="Lookup finished";
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
            TorBoxClient client=new TorBoxClient(key);Set<Long> seen=new HashSet<>();
            for(int offset=0;offset<20000&&count<3;offset+=1000){
                client.check();JSONArray page=client.page(offset,1000);int newItems=0;
                for(int i=0;i<page.length()&&count<3;i++){
                    JSONObject item=page.getJSONObject(i);long id=item.optLong("id",-1);if(id<0||!seen.add(id))continue;newItems++;
                    scanned++;
                    List<LibraryMatcher.Match> found=LibraryMatcher.find(item,query,groups);if(found.isEmpty())continue;
                    candidates+=found.size();
                    // Confirm current per-item completion/files rather than trusting an index flag.
                    found=LibraryMatcher.find(client.item(id),query,groups);
                    for(LibraryMatcher.Match match:found){
                        if(count>=3)break;
                        try{client.check();String url=client.link(match.torrent,match.file);TorBoxClient.probe(url);callback.invoke(link(match,url));count++;}
                        catch(Exception ignored){/* Do not expose URL, token or provider error body. */}
                    }
                }
                if(page.length()<1000||newItems==0)break;
            }
            outcome=count>0?"Verified media links returned":candidates==0?"No exact eligible Romanian filename found in your Library":"Matched files did not yield a verified media link";
        }catch(TorBoxClient.AuthenticationFailure invalidKey){outcome="TorBox authorization failed";TorBoxSetup.show();}
        catch(Exception ignored){/* Expected misses stay absent; no placeholder video links. */}
        android.util.Log.i("TorBoxTV","Library lookup returned "+count+" verified file link(s)");
        if(diagnostic)showDiagnostic(requested,scanned,candidates,count,outcome);
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
    static void showDiagnostic(String title,int scanned,int candidates,int links,String outcome){
        final android.app.Activity a=TorBoxPlugin.activity.get();if(a==null||a.isFinishing())return;
        final String message=title+"\nLibrary items checked: "+scanned+"\nMatching RO file candidates: "+candidates+"\nVerified links: "+links+"\n\n"+outcome+"\n\nThis test uses The Matrix, not the title you previously opened. Only your own Library is searched.";
        a.runOnUiThread(()->new android.app.AlertDialog.Builder(a).setTitle("TorBox Library diagnostic").setMessage(message).setPositiveButton("OK",null).show());
    }
    static Object link(LibraryMatcher.Match m,String url)throws Exception {
        String lower=m.name.toLowerCase(Locale.ROOT);int quality=lower.contains("2160p")?2160:lower.contains("1080p")?1080:lower.contains("720p")?720:0;
        return Class.forName("com.lagradost.cloudstream3.utils.ExtractorLink")
            .getConstructor(String.class,String.class,String.class,String.class,int.class,boolean.class,Map.class,String.class)
            .newInstance("TorBox Library",m.confidence+" | TorBox Library",url,"",quality,false,Collections.emptyMap(),null);
    }
}
