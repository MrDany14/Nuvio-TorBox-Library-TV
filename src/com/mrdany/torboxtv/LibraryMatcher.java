package com.mrdany.torboxtv;

import org.json.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Conservative filename matching: ambiguity produces no result, never a fallback file. */
final class LibraryMatcher {
    static String normalize(String s){return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replace("&amp;"," and ").replace("&"," and ").replaceAll("[^a-z0-9]+"," ").trim().replaceAll(" +"," ");}
    static boolean ready(JSONObject item){return item.optBoolean("download_finished",false)&&item.optBoolean("download_present",false)&&item.optLong("id",-1)>=0;}
    static boolean episode(String path,int season,int episode){
        Matcher m=Pattern.compile("(?i)(?<![a-z0-9])s(\\d{1,2})[ ._-]*e(\\d{1,3})(?![0-9])").matcher(path);
        while(m.find())if(Integer.parseInt(m.group(1))==season&&Integer.parseInt(m.group(2))==episode)return true;
        m=Pattern.compile("(?i)(?<![a-z0-9])(\\d{1,2})x(\\d{1,3})(?![0-9])").matcher(path);
        while(m.find())if(Integer.parseInt(m.group(1))==season&&Integer.parseInt(m.group(2))==episode)return true;
        return false;
    }
    static boolean title(String release,List<String> aliases,int year,boolean series){
        String base=release.replace('\\','/');base=base.substring(base.lastIndexOf('/')+1);
        String n=normalize(base);
        for(String alias:aliases){String a=normalize(alias);if(a.isEmpty())continue;
            if(!n.equals(a)&&!n.startsWith(a+" "))continue;
            String suffix=n.substring(a.length()).trim();
            // Prefix alone is insufficient: Toy Story must not match Toy Story 2.
            if(!suffix.isEmpty()&&!suffix.matches("^(?:s\\d{1,2}e?\\d*|\\d{1,2}x\\d+|season|sezonul|complete|collection|19\\d{2}|20\\d{2}|2160p|1080p|720p|480p|web|webrip|webdl|bluray|bdrip|hdtv|dvd|dvdrip|rodubbed|rodub|dublat|mkv|mp4|avi)(?:\\b.*)?$"))continue;
            if(!series&&year>0){Matcher y=Pattern.compile("(?<!\\d)(19\\d{2}|20\\d{2})(?!\\d)").matcher(suffix);if(!y.find()||Integer.parseInt(y.group(1))!=year)continue;}
            return true;
        }
        return false;
    }
    static boolean video(String file){String n=file.toLowerCase(Locale.ROOT);return n.matches(".*\\.(mkv|mp4|m4v|avi)$")&&!Pattern.compile("(?i)(?:^|[ ._/-])(sample|trailer|preview)(?:[ ._/-]|$)").matcher(n).find();}
    static boolean conflictingSeriesTitle(String path,List<String> aliases){
        String base=path.replace('\\','/');base=base.substring(base.lastIndexOf('/')+1);
        Matcher marker=Pattern.compile("(?i)(?<![a-z0-9])(?:s\\d{1,2}[ ._-]*e\\d{1,3}|\\d{1,2}x\\d{1,3})").matcher(base);
        if(!marker.find())return false;
        String prefix=normalize(base.substring(0,marker.start()));
        if(prefix.isEmpty())return false; // S01E01.mkv inherits its verified pack title.
        prefix=prefix.replaceFirst(" (?:19|20)[0-9]{2}$","");
        for(String alias:aliases)if(prefix.equals(normalize(alias)))return false;
        return true;
    }
    static String audio(String name,String groups){
        String n=normalize(name);
        // A subtitled Romanian release is not evidence of Romanian audio.
        boolean dubbed=Pattern.compile("(?:^| )(?:rodubbed|rodub|ro dubbed|ro dub|dublat|dublaj|romanian dubbed|romanian audio|audio ro|audio romana|limba romana)(?: |$)").matcher(n).find();
        if(dubbed)return "RO Dub (filename)";
        for(String group:groups.split(",")){String g=normalize(group);if(!g.isEmpty()&&Pattern.compile("(?:^| )"+Pattern.quote(g)+"(?: |$)").matcher(n).find())return "Possible RO Dub";}
        return null;
    }
    static final class Match {
        final long torrent,file;final String name,confidence;final boolean metadataRequired;
        Match(long t,long f,String n,String c){this(t,f,n,c,false);}
        Match(long t,long f,String n,String c,boolean required){torrent=t;file=f;name=n;confidence=c;metadataRequired=required;}
    }
    static List<Match> find(JSONObject item,JSONObject query,String groups)throws Exception {
        return find(item,query,groups,false);
    }
    static List<Match> findForMetadata(JSONObject item,JSONObject query,String groups)throws Exception {
        return find(item,query,groups,true);
    }
    private static List<Match> find(JSONObject item,JSONObject query,String groups,boolean includeUnknown)throws Exception {
        List<Match> out=new ArrayList<>();if(!ready(item))return out;
        List<String> aliases=new ArrayList<>();for(String k:Arrays.asList("movieName","originalTitle","localTitle")){String s=query.optString(k,"");if(!s.isEmpty())aliases.add(s);}
        boolean series=query.optInt("season",-1)>=0&&query.optInt("episode",-1)>0;
        String pack=item.optString("name","");int year=query.optInt("releaseYear",0);
        if(!series&&year<=0)return out;
        JSONArray files=item.optJSONArray("files");if(files==null)return out;
        boolean packMatches=title(pack,aliases,year,series);
        for(int i=0;i<files.length();i++){
            JSONObject f=files.getJSONObject(i);String name=f.optString("name",f.optString("short_name",""));
            if(!video(name)||f.optLong("id",-1)<0||f.optLong("size",0)<=0)continue;
            if(!packMatches&&!title(name,aliases,year,series))continue;
            if(series&&!episode(name,query.getInt("season"),query.getInt("episode")))continue;
            if(series&&conflictingSeriesTitle(name,aliases))continue;
            if(!series&&Pattern.compile("(?i)(?<![a-z0-9])s\\d{1,2}e\\d+").matcher(name).find())continue;
            String ro=audio(pack+" "+name,groups);if(ro==null&&!includeUnknown)continue;
            out.add(new Match(item.getLong("id"),f.getLong("id"),name,ro==null?"Audio metadata required":ro,ro==null));
        }
        // Multi-file movie packs without per-file title identity are ambiguous.
        if(!series&&out.size()>1)out.removeIf(m->!title(m.name,aliases,year,false));
        return out;
    }
}
