package com.mrdany.torboxtv;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
/** At most three concurrent verifications; callbacks stay on the caller thread. */
final class ParallelLibraryResolver {
 static final class Stats{int scanned,candidates,obtained,media,links;String failure="";}
 static final class Work{final long id;final int priority;Work(long id,int priority){this.id=id;this.priority=priority;}}
 static final class Verified{final LibraryMatcher.Match match;final String url,identity;Verified(LibraryMatcher.Match m,String u,String i){match=m;url=u;identity=i;}}
 static final class Checked{int obtained,media;String failure="";boolean unauthorized;final List<Verified> verified=new ArrayList<>();}
 static Checked verify(Work work,JSONObject query,String groups,CachedDiscoveryResolver.Gateway api){
  Checked result=new Checked();String stage="Library refresh";
  try{
   api.check();JSONObject current=api.item(work.id);
   for(LibraryMatcher.Match match:LibraryMatcher.findForMetadata(current,query,groups)){
    if(result.verified.size()>=3)break;
    stage="Download-link API";api.check();String url=api.link(match.torrent,match.file);result.obtained++;
    stage="Media probe";api.probeMedia(url);result.media++;
    stage="Audio metadata";LibraryMatcher.Match audio=api.audioMatch(match,url);
    if(audio==null){result.failure="Audio metadata: Romanian audio not established";continue;}
    String hash=current.optString("hash","").toLowerCase(Locale.ROOT);
    result.verified.add(new Verified(audio,url,(hash.isEmpty()?"id="+work.id:hash)+":"+match.file));
   }
  }catch(TorBoxClient.AuthenticationFailure e){result.unauthorized=true;}
  catch(Exception e){result.failure=stage+": "+TorBoxClient.safeError(e);}
  return result;
 }
 static Stats resolve(JSONArray page,JSONObject query,String groups,CachedDiscoveryResolver.Gateway api,int limit,Set<Long> seen,Set<String> returned,CachedDiscoveryResolver.Emit emit)throws Exception{
  Stats stats=new Stats();List<Work> work=new ArrayList<>();
  for(int i=0;i<page.length();i++){
   JSONObject item=page.getJSONObject(i);long id=item.optLong("id",-1);if(id<0||!seen.add(id))continue;stats.scanned++;
   List<LibraryMatcher.Match> matches=LibraryMatcher.findForMetadata(item,query,groups);if(matches.isEmpty())continue;stats.candidates+=matches.size();int priority=2;
   for(LibraryMatcher.Match m:matches)priority=Math.min(priority,m.metadataRequired?2:"Possible RO Dub".equals(m.confidence)?1:0);
   work.add(new Work(id,priority));
  }
  if(work.isEmpty()||limit<=0)return stats;
  Collections.sort(work,Comparator.comparingInt(w->w.priority));
  ExecutorService pool=Executors.newFixedThreadPool(3,r->{Thread t=new Thread(r,"TorBox-verify");t.setDaemon(true);return t;});
  CompletionService<Checked> completion=new ExecutorCompletionService<>(pool);List<Future<Checked>> pending=new ArrayList<>();int next=0,inFlight=0;
  long deadline=System.nanoTime()+18000000000L;
  try{
   while(next<work.size()&&inFlight<Math.min(3,limit)){final Work w=work.get(next++);pending.add(completion.submit(()->verify(w,query,groups,api)));inFlight++;}
   while(inFlight>0&&stats.links<limit){
    api.check();long remaining=deadline-System.nanoTime();if(remaining<=0)break;
    Future<Checked> done=completion.poll(remaining,TimeUnit.NANOSECONDS);if(done==null)break;inFlight--;
    Checked checked=done.get();if(checked.unauthorized)throw new TorBoxClient.AuthenticationFailure();stats.obtained+=checked.obtained;stats.media+=checked.media;
    if(!checked.failure.isEmpty())stats.failure=checked.failure;
    for(Verified v:checked.verified){if(stats.links>=limit)break;if(returned.contains(v.identity))continue;
     try{emit.stream(v.match,v.url);returned.add(v.identity);stats.links++;}catch(Exception e){stats.failure="Nuvio link callback: "+TorBoxClient.safeError(e);}
    }
    while(stats.links+inFlight<limit&&inFlight<3&&next<work.size()){final Work w=work.get(next++);pending.add(completion.submit(()->verify(w,query,groups,api)));inFlight++;}
   }
  }finally{for(Future<Checked> f:pending)f.cancel(true);pool.shutdownNow();}
  return stats;
 }
}
