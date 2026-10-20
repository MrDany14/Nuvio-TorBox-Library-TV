package com.mrdany.torboxtv;

import java.util.*;
import org.json.*;

public final class CachedDiscoveryTest {
 static int assertions;
 static final String HASH="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
 static final String TITLE="Peppa.Pig.S04.RODubbed";
 static final String FILE="Peppa.Pig.S04E15.720p.RODubbed.mkv";
 static void check(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
 static JSONObject query()throws Exception{return new JSONObject().put("movieName","Peppa Pig").put("season",4).put("episode",15);}
 static JSONObject file(int id,String name)throws Exception{return new JSONObject().put("id",id).put("name",name).put("size",36500000);}
 static final class Fake extends CachedDiscoveryResolver.Gateway {
  JSONObject cache,actual;int claims,linkCalls,emitted;long usedFile=-1;boolean claimFails,probeFails;
  Fake()throws Exception{
   cache=new JSONObject().put(HASH,new JSONObject().put("hash",HASH).put("name",TITLE).put("files",new JSONArray().put(file(777,FILE))));
   actual=new JSONObject().put("id",13).put("hash",HASH).put("name",TITLE).put("download_finished",true).put("download_present",true).put("files",new JSONArray().put(file(42,FILE)));
  }
  JSONObject cached(List<String> hashes){return cache;}
  long claimCached(String hash)throws Exception{claims++;if(claimFails)throw new Exception("Server rejected cached-only acquisition");return 13;}
  JSONObject item(long id){return actual;}
  String link(long torrent,long file){linkCalls++;usedFile=file;return "https://example.invalid/video.mkv";}
  void probeMedia(String url)throws Exception{if(probeFails)throw new Exception("Media error");}
  void check(){}
 }
 static CachedDiscoveryResolver.Stats run(Fake f)throws Exception{return CachedDiscoveryResolver.resolve(Collections.singletonList(new TorrentDiscovery.Candidate(HASH,TITLE)),query(),"RODubbed",f,3,new HashSet<>(),(m,u)->f.emitted++);}
 public static void main(String[] args)throws Exception{
  Fake success=new Fake();check(run(success).links==1,"cached exact file returned");check(success.claims==1,"one cached-only registration");check(success.usedFile==42,"fresh actual ID used, never cached metadata ID/index");
  Fake uncached=new Fake();uncached.cache=new JSONObject();run(uncached);check(uncached.claims==0&&uncached.emitted==0,"uncached never registered or emitted");
  Fake wrongEpisode=new Fake();wrongEpisode.cache.getJSONObject(HASH).getJSONArray("files").getJSONObject(0).put("name","Peppa.Pig.S04E16.RODubbed.mkv");run(wrongEpisode);check(wrongEpisode.claims==0,"wrong cached episode rejected before registration");
  Fake unavailableFiles=new Fake();unavailableFiles.cache.getJSONObject(HASH).remove("files");run(unavailableFiles);check(unavailableFiles.claims==0,"cache flag without files cannot register");
  Fake mismatchedHash=new Fake();mismatchedHash.cache.getJSONObject(HASH).put("hash","bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");run(mismatchedHash);check(mismatchedHash.claims==0,"cache hash mismatch rejected before registration");
  Fake staleCache=new Fake();staleCache.claimFails=true;run(staleCache);check(staleCache.claims==1&&staleCache.linkCalls==0&&staleCache.emitted==0,"stale cache failure has no uncached fallback");
  Fake notReady=new Fake();notReady.actual.put("download_present",false);run(notReady);check(notReady.linkCalls==0&&notReady.emitted==0,"actual cache availability rechecked");
  Fake replacedFile=new Fake();replacedFile.actual.getJSONArray("files").getJSONObject(0).put("name","Peppa.Pig.S04E16.RODubbed.mkv");run(replacedFile);check(replacedFile.linkCalls==0,"fresh file match rechecked");
  Fake wrongActualHash=new Fake();wrongActualHash.actual.put("hash","bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");run(wrongActualHash);check(wrongActualHash.linkCalls==0,"fresh torrent hash rechecked");
  Fake badMedia=new Fake();badMedia.probeFails=true;run(badMedia);check(badMedia.emitted==0,"bad media response never offered");
  String body=new String(TorBoxClient.cachedOnlyBody(HASH,"TestBoundary"),"UTF-8");check(body.contains("name=\"add_only_if_cached\"\r\n\r\ntrue\r\n"),"server-side cached-only flag in actual multipart builder");check(body.contains("name=\"allow_zip\"\r\n\r\nfalse\r\n"),"zip fallback disabled");
  JSONObject errorVideo=new JSONObject().put("streams",new JSONArray().put(new JSONObject().put("url","https://example.invalid/error.mp4").put("title","Peppa.Pig.S04.RODubbed")));
  check(TorrentDiscovery.torrentio(errorVideo).isEmpty(),"URL-only placeholder videos not treated as torrents");
  JSONObject malformed=new JSONObject().put("streams",new JSONArray().put(new JSONObject().put("infoHash","invalid").put("title",TITLE)));
  check(TorrentDiscovery.torrentio(malformed).isEmpty(),"malformed hash rejected");
  check(!TorrentDiscovery.eligible(new TorrentDiscovery.Candidate(HASH,"Mickey.Mouse.Clubhouse.S01.RODubbed"),new JSONObject().put("movieName","Mickey Mouse Funhouse").put("season",1).put("episode",1),"RODubbed"),"wrong series rejected during discovery too");
  System.out.println("Cached discovery: "+assertions+" checks passed; fake account gateway, no credentials or real account operations.");
 }
}
