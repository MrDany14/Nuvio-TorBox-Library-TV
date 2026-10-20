package com.mrdany.torboxtv;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;
public final class ParallelLibraryTest {
 static int checks;
 static void check(boolean b,String label){checks++;if(!b)throw new AssertionError(label);}
 static JSONObject item(int id,String group)throws Exception{return new JSONObject().put("id",id).put("hash",String.format("%040d",id)).put("name","Bluey.S01-"+group).put("download_finished",true).put("download_present",true).put("files",new JSONArray().put(new JSONObject().put("id",id*10).put("name","Bluey.S01E01-"+group+".mkv").put("size",50000)));}
 static final class Fake extends CachedDiscoveryResolver.Gateway {
  Map<Long,JSONObject> items=new HashMap<>();Set<Long> touched=ConcurrentHashMap.newKeySet();AtomicInteger active=new AtomicInteger(),maximum=new AtomicInteger();CountDownLatch together;boolean failOne,barrier;
  Fake(boolean barrier)throws Exception{this.barrier=barrier;together=new CountDownLatch(3);items.put(1L,ParallelLibraryTest.item(1,"RODubbed"));items.put(2L,ParallelLibraryTest.item(2,"playWEB"));items.put(3L,ParallelLibraryTest.item(3,"TGS"));items.put(4L,ParallelLibraryTest.item(4,"OldT"));}
  JSONObject cached(List<String> h){throw new AssertionError();}long claimCached(String h){throw new AssertionError();}
  JSONObject item(long id)throws Exception{touched.add(id);int n=active.incrementAndGet();maximum.accumulateAndGet(n,Math::max);try{if(barrier){together.countDown();if(!together.await(2,TimeUnit.SECONDS))throw new Exception("Concurrency did not happen");}return items.get(id);}finally{active.decrementAndGet();}}
  String link(long t,long f)throws Exception{if(failOne&&t==1)throw new Exception("dummy failure");return "https://example.invalid/video.mkv";}
  void check(){}void probeMedia(String url){}
  LibraryMatcher.Match audioMatch(LibraryMatcher.Match m,String url){return new LibraryMatcher.Match(m.torrent,m.file,m.name,"RO Dub (audio metadata)");}
 }
 static JSONArray page(Fake f){return new JSONArray().put(f.items.get(4L)).put(f.items.get(3L)).put(f.items.get(2L)).put(f.items.get(1L));}
 static JSONObject query()throws Exception{return new JSONObject().put("movieName","Bluey").put("season",1).put("episode",1);}
 public static void main(String[] args)throws Exception{
  Fake f=new Fake(true);long caller=Thread.currentThread().getId();AtomicBoolean wrongThread=new AtomicBoolean();AtomicInteger count=new AtomicInteger();
  ParallelLibraryResolver.Stats result=ParallelLibraryResolver.resolve(page(f),query(),"RODubbed,playWEB",f,3,new HashSet<>(),new HashSet<>(),(m,u)->{if(Thread.currentThread().getId()!=caller)wrongThread.set(true);count.incrementAndGet();});
  check(result.links==3&&count.get()==3,"three verified callbacks");check(f.maximum.get()==3,"checks overlap with three-worker maximum");check(!wrongThread.get(),"callbacks serialized on lookup caller");check(f.touched.size()==3&&f.touched.contains(1L)&&f.touched.contains(2L),"priority and remaining-result accounting avoid unnecessary fourth check");
  Fake bad=new Fake(false);bad.failOne=true;
  result=ParallelLibraryResolver.resolve(page(bad),query(),"RODubbed,playWEB",bad,3,new HashSet<>(),new HashSet<>(),(m,u)->{});
  check(result.links==3&&bad.touched.size()==4,"failed candidate replaced without losing three-result target");
  Fake one=new Fake(false);result=ParallelLibraryResolver.resolve(page(one),query(),"RODubbed,playWEB",one,1,new HashSet<>(),new HashSet<>(),(m,u)->{});
  check(result.links==1&&one.touched.size()==1,"one remaining slot does not launch three requests");
  Fake stale=new Fake(false);stale.items.get(1L).put("download_present",false);result=ParallelLibraryResolver.resolve(new JSONArray().put(item(1,"RODubbed")),query(),"RODubbed",stale,3,new HashSet<>(),new HashSet<>(),(m,u)->{});
  check(result.links==0,"fresh missing file rejected despite page snapshot");
  LookupMemo.page("account-a:0:1000","[]",0);check(LookupMemo.page("account-a:0:1000",29999)!=null,"Library snapshot valid for 30 seconds");check(LookupMemo.page("account-a:0:1000",30000)==null,"Library snapshot expires");
  check(LookupMemo.page("account-b:0:1000",0)==null,"cache scoped by account");
  AudioLanguageMetadata.Result ro=new AudioLanguageMetadata.Result(Collections.singletonList("ron"),true);LookupMemo.audio("account-a:file",ro,0);
  check(LookupMemo.audio("account-a:file",299999)==ro,"known language reused within five minutes");check(LookupMemo.audio("account-a:file",300000)==null,"language cache expires");
  LookupMemo.audio("account-a:unknown",AudioLanguageMetadata.unknown(),0);check(LookupMemo.audio("account-a:unknown",0)==null,"unknown or timed-out language never cached");
  LookupMemo.page("account-a:0:1000","[]",0);LookupMemo.audio("account-a:file",ro,0);LookupMemo.clear("account-a");
  check(LookupMemo.page("account-a:0:1000",0)==null&&LookupMemo.audio("account-a:file",0)==null,"authorization failure clears account caches");
  JSONObject original=item(1,"RODubbed");original.put("irrelevant_large_field","not required");original.getJSONArray("files").getJSONObject(0).put("absolute_path","unused").put("opensubtitles_hash","unused");
  JSONObject compact=TorBoxClient.snapshot(new JSONArray().put(original)).getJSONObject(0);
  check(!compact.has("irrelevant_large_field")&&!compact.getJSONArray("files").getJSONObject(0).has("absolute_path"),"snapshot omits fields not used for matching");
  check(LibraryMatcher.findForMetadata(original,query(),"RODubbed").size()==LibraryMatcher.findForMetadata(compact,query(),"RODubbed").size(),"compact snapshot preserves exact matching");
  System.out.println("Parallel Library and memo: "+checks+" checks passed; fake gateway, no real credentials or network.");
 }
}
