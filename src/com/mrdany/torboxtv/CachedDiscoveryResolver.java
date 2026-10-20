package com.mrdany.torboxtv;

import org.json.*;
import java.util.*;

/** Cache/file checks are separate from the account operation, so their ordering is testable. */
final class CachedDiscoveryResolver {
    static abstract class Gateway {
        abstract JSONObject cached(List<String> hashes)throws Exception;
        abstract long claimCached(String hash)throws Exception;
        abstract JSONObject item(long id)throws Exception;
        abstract String link(long torrent,long file)throws Exception;
        abstract void check()throws Exception;
        void probeMedia(String url)throws Exception{TorBoxClient.probe(url);}
    }
    interface Emit {void stream(LibraryMatcher.Match match,String url)throws Exception;}
    static final class Stats {int cached,exactFiles,registered,links,failed;}
    static JSONObject cachedItem(JSONObject cache,TorrentDiscovery.Candidate candidate)throws Exception {
        JSONArray raw=cache.optJSONArray("files");if(raw==null)return null;
        JSONArray files=new JSONArray();
        for(int i=0;i<raw.length();i++){
            JSONObject f=raw.optJSONObject(i);if(f==null)continue;
            // Cached metadata IDs are not trusted as download IDs; fresh IDs are
            // read again from the user's item after cached-only registration.
            files.put(new JSONObject().put("id",i).put("name",f.optString("name",f.optString("short_name",""))).put("size",f.optLong("size",0)));
        }
        return new JSONObject().put("id",0).put("name",cache.optString("name",candidate.title)).put("files",files)
            .put("download_finished",true).put("download_present",true);
    }
    static Stats resolve(List<TorrentDiscovery.Candidate> candidates,JSONObject query,String groups,Gateway api,int limit,Set<String> returned,Emit emit)throws Exception {
        Stats stats=new Stats();if(candidates.isEmpty()||limit<=0)return stats;
        List<String> hashes=new ArrayList<>();for(TorrentDiscovery.Candidate c:candidates)hashes.add(c.hash);
        JSONObject cached=api.cached(hashes);int attempts=0;
        for(TorrentDiscovery.Candidate c:candidates){
            if(stats.links>=limit||attempts>=5)break;
            try{api.check();}catch(Exception timeout){stats.failed++;break;}
            JSONObject cache=cached.optJSONObject(c.hash);if(cache==null)continue;stats.cached++;
            if(cache.has("cached")&&!cache.optBoolean("cached"))continue;
            if(!cache.optString("hash",c.hash).equalsIgnoreCase(c.hash))continue;
            JSONObject preview=cachedItem(cache,c);if(preview==null)continue;
            if(LibraryMatcher.find(preview,query,groups).isEmpty())continue;
            stats.exactFiles++;attempts++;
            try {
                // The server MUST enforce cached-only; no uncached retry exists.
                long id=api.claimCached(c.hash);stats.registered++;
                JSONObject actual=api.item(id);
                if(!actual.optString("hash","").equalsIgnoreCase(c.hash)||actual.optLong("id",-1)!=id)continue;
                for(LibraryMatcher.Match m:LibraryMatcher.find(actual,query,groups)){
                    if(stats.links>=limit)break;
                    String identity=c.hash+":"+m.file;if(returned.contains(identity))continue;
                    String url=api.link(m.torrent,m.file);api.probeMedia(url);emit.stream(m,url);returned.add(identity);stats.links++;
                }
            }catch(Exception ignored){stats.failed++;}
        }
        return stats;
    }
}
