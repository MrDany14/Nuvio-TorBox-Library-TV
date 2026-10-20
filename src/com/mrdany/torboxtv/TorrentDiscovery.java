package com.mrdany.torboxtv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Public metadata only: no TorBox credentials are accepted by this class. */
final class TorrentDiscovery {
    static final class Candidate {
        final String hash,title;
        Candidate(String h,String t){hash=h.toLowerCase(Locale.ROOT);title=t;}
    }
    static final class Result {
        final List<Candidate> candidates=new ArrayList<>();
        int received,failed;
    }
    static String request(String url,String body)throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(4500);c.setReadTimeout(6500);
        c.setRequestProperty("Accept","application/json");
        try {
            if(body!=null){byte[] b=body.getBytes(StandardCharsets.UTF_8);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setFixedLengthStreamingMode(b.length);try(OutputStream out=c.getOutputStream()){out.write(b);}}
            if(c.getResponseCode()!=200)throw new IOException("Discovery service unavailable");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Stopped");out.write(b,0,n);if(out.size()>4*1024*1024)throw new IOException("Discovery response too large");}}
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }finally{c.disconnect();}
    }
    static List<Candidate> torrentio(JSONObject response){
        List<Candidate> out=new ArrayList<>();JSONArray streams=response.optJSONArray("streams");if(streams==null)return out;
        for(int i=0;i<streams.length();i++){
            JSONObject s=streams.optJSONObject(i);if(s==null)continue;
            String h=s.optString("infoHash","");if(!h.matches("(?i)[0-9a-f]{40}"))continue;
            // Ignore URL-only entries, including server error videos.
            String t=s.optString("title",s.optString("description","")).split("\\n",2)[0];
            if(!t.isEmpty())out.add(new Candidate(h,t));
        }
        return out;
    }
    static List<Candidate> knaben(JSONObject response){
        List<Candidate> out=new ArrayList<>();JSONArray hits=response.optJSONArray("hits");if(hits==null)return out;
        for(int i=0;i<hits.length();i++){JSONObject h=hits.optJSONObject(i);if(h==null)continue;String hash=h.optString("hash","");String title=h.optString("title","");if(hash.matches("(?i)[0-9a-f]{40}")&&!title.isEmpty())out.add(new Candidate(hash,title));}
        return out;
    }
    static boolean eligible(Candidate c,JSONObject query,String groups){
        List<String> aliases=new ArrayList<>();for(String k:Arrays.asList("movieName","originalTitle","localTitle")){String s=query.optString(k,"");if(!s.isEmpty())aliases.add(s);}
        boolean series=query.optInt("season",-1)>=0&&query.optInt("episode",-1)>0;
        if(!series&&query.optInt("releaseYear",0)<=0)return false;
        if(series&&!episodeCandidate(c.title,query.optInt("season"),query.optInt("episode")))return false;
        return LibraryMatcher.title(c.title,aliases,query.optInt("releaseYear",0),series)&&LibraryMatcher.audio(c.title,groups)!=null;
    }
    /** Exclude explicit wrong episodes/seasons BEFORE the bounded cache shortlist.
     * Season and complete-series packs remain candidates for exact-file checks. */
    static boolean episodeCandidate(String title,int season,int episode){
        if(java.util.regex.Pattern.compile("(?i)(?<![a-z0-9])(?:s\\d{1,2}[ ._-]*e\\d{1,3}|\\d{1,2}x\\d{1,3})(?![0-9])").matcher(title).find())return LibraryMatcher.episode(title,season,episode);
        java.util.regex.Matcher range=java.util.regex.Pattern.compile("(?i)(?<![a-z0-9])s(\\d{1,2})[ ._]*-[ ._]*s?(\\d{1,2})(?![a-z0-9])").matcher(title);
        while(range.find())if(season>=Integer.parseInt(range.group(1))&&season<=Integer.parseInt(range.group(2)))return true;
        java.util.regex.Matcher marker=java.util.regex.Pattern.compile("(?i)(?<![a-z0-9])(?:s|season[ ._-]*|sezonul[ ._-]*)(\\d{1,2})(?![a-z0-9])").matcher(title);
        boolean explicitSeason=false;
        while(marker.find()){explicitSeason=true;if(Integer.parseInt(marker.group(1))==season)return true;}
        return !explicitSeason;
    }
    static Result discover(JSONObject query,String groups)throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"TorBox-discovery");t.setDaemon(true);return t;});
        CompletionService<List<Candidate>> completion=new ExecutorCompletionService<>(pool);
        String imdb=query.optString("imdbID","");boolean series=query.optInt("episode",-1)>0;
        completion.submit(()->{
            if(!imdb.matches("tt[0-9]+"))return Collections.emptyList();
            String id=imdb+(series?":"+query.getInt("season")+":"+query.getInt("episode"):"");
            return torrentio(new JSONObject(request("https://torrentio.strem.fun/stream/"+(series?"series/":"movie/")+id+".json",null)));
        });
        completion.submit(()->{
            JSONObject body=new JSONObject().put("query",query.getString("movieName")).put("search_field","title").put("search_type","100%")
                .put("size",300).put("order_by","seeders").put("order_direction","desc").put("hide_unsafe",true).put("hide_xxx",true);
            return knaben(new JSONObject(request("https://api.knaben.org/v1",body.toString())));
        });
        Result result=new Result();Set<String> seen=new HashSet<>();long end=System.nanoTime()+12000000000L;
        try{
            for(int i=0;i<2;i++){
                Future<List<Candidate>> f=completion.poll(Math.max(1,end-System.nanoTime()),TimeUnit.NANOSECONDS);
                if(f==null){result.failed+=2-i;break;}
                try{for(Candidate c:f.get()){result.received++;if(eligible(c,query,groups)&&seen.add(c.hash))result.candidates.add(c);}}
                catch(Exception ignored){result.failed++;}
            }
        }finally{pool.shutdownNow();}
        Collections.sort(result.candidates,(a,b)->Boolean.compare("Possible RO Dub".equals(LibraryMatcher.audio(a.title,groups)),"Possible RO Dub".equals(LibraryMatcher.audio(b.title,groups))));
        if(result.candidates.size()>50)result.candidates.subList(50,result.candidates.size()).clear();
        return result;
    }
}
