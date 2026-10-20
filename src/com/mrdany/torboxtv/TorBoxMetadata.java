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
   try {
    Method getter=response.getClass().getMethod("getDataUrl");
    String data=(String)getter.invoke(response);
    response.getClass().getMethod("setDataUrl",String.class).invoke(response,enrich(data,name,year));
   }catch(NoSuchMethodException series){
    Object episodes=response.getClass().getMethod("getEpisodes").invoke(response);
    if(episodes instanceof Iterable)for(Object e:(Iterable<?>)episodes){
     String data=(String)e.getClass().getMethod("getData").invoke(e);
     e.getClass().getMethod("setData",String.class).invoke(e,enrich(data,name,year));
    }
   }
  }catch(Exception ignored){/* Unknown response/failure/coroutine marker passes through unchanged. */}
  return response;
 }
 static String enrich(String data,String name,int year)throws Exception {
  JSONObject j=new JSONObject(data);j.put("movieName",name).put("originalTitle",name).put("releaseYear",year);return j.toString();
 }
}
