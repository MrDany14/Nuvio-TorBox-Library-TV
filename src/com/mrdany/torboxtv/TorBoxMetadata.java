package com.mrdany.torboxtv;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;
/** Adds title/year to Nuvio SDK metadata, without shipping an API credential. */
final class TorBoxMetadata {
 static Object annotate(Object response){
  if(response==null)return null;
  try {
   Object y=response.getClass().getMethod("getYear").invoke(response);
   String name=(String)response.getClass().getMethod("getName").invoke(response);
   Integer year=y instanceof Integer?(Integer)y:0;
   String imdb="";
   try{Object sync=response.getClass().getMethod("getSyncData").invoke(response);if(sync instanceof Map){Object v=((Map)sync).get("imdb");if(v!=null){String id=String.valueOf(v);if(id.matches("[0-9]+"))id="tt"+id;if(id.matches("tt[0-9]+"))imdb=id;}}}catch(Exception ignored){}
   try {
    Method getter=response.getClass().getMethod("getDataUrl");
    String data=(String)getter.invoke(response);
    response.getClass().getMethod("setDataUrl",String.class).invoke(response,enrich(data,name,year,imdb));
   }catch(NoSuchMethodException series){
    Object episodes=response.getClass().getMethod("getEpisodes").invoke(response);
    if(episodes instanceof Iterable)for(Object e:(Iterable<?>)episodes){
     String data=(String)e.getClass().getMethod("getData").invoke(e);
     e.getClass().getMethod("setData",String.class).invoke(e,enrich(data,name,year,imdb));
    }
   }
  }catch(Exception ignored){/* Unknown response/failure/coroutine marker passes through unchanged. */}
  return response;
 }
 static String enrich(String data,String name,int year,String imdb)throws Exception {
  JSONObject j=new JSONObject(data);j.put("movieName",name).put("originalTitle",name).put("releaseYear",year);if(!imdb.isEmpty())j.put("imdbID",imdb);return j.toString();
 }
}
