package com.mrdany.torboxtv;

import com.lagradost.cloudstream3.metaproviders.TmdbProvider;
import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function1;
import org.json.*;
import java.util.*;

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
        int count=0;
        try {
            String key=new PrivateKeyStore(TorBoxPlugin.app).read();
            if(key.isEmpty()){TorBoxSetup.show();return false;}
            JSONObject query=new JSONObject(data);
            // Normal lookup uses the Nuvio SDK's metadata response. Diagnostics
            // may omit movie year, so those queries are conservatively skipped.
            String groups=TorBoxPlugin.app.getSharedPreferences("torbox_tv_options",0).getString("groups",TorBoxSetup.GROUPS);
            TorBoxClient client=new TorBoxClient(key);Set<Long> seen=new HashSet<>();
            for(int offset=0;offset<20000&&count<3;offset+=1000){
                client.check();JSONArray page=client.page(offset,1000);int newItems=0;
                for(int i=0;i<page.length()&&count<3;i++){
                    JSONObject item=page.getJSONObject(i);long id=item.optLong("id",-1);if(id<0||!seen.add(id))continue;newItems++;
                    List<LibraryMatcher.Match> found=LibraryMatcher.find(item,query,groups);if(found.isEmpty())continue;
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
        }catch(TorBoxClient.AuthenticationFailure invalidKey){TorBoxSetup.show();}
        catch(Exception ignored){/* Expected misses stay absent; no placeholder video links. */}
        android.util.Log.i("TorBoxTV","Library lookup returned "+count+" verified file link(s)");
        return count>0;
    }
    static Object link(LibraryMatcher.Match m,String url)throws Exception {
        String lower=m.name.toLowerCase(Locale.ROOT);int quality=lower.contains("2160p")?2160:lower.contains("1080p")?1080:lower.contains("720p")?720:0;
        return Class.forName("com.lagradost.cloudstream3.utils.ExtractorLink")
            .getConstructor(String.class,String.class,String.class,String.class,int.class,boolean.class,Map.class,String.class)
            .newInstance("TorBox Library",m.confidence+" | TorBox Library",url,"",quality,false,Collections.emptyMap(),null);
    }
}
