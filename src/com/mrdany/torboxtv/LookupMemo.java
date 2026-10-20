package com.mrdany.torboxtv;
import java.util.*;
/** Bounded process-memory caches. No API key or signed URL is retained here. */
final class LookupMemo {
 static final class Entry<T>{final T value;final long until;Entry(T v,long u){value=v;until=u;}}
 private static final LinkedHashMap<String,Entry<String>> pages=new LinkedHashMap<>(8,0.75f,true);
 private static final LinkedHashMap<String,Entry<AudioLanguageMetadata.Result>> audio=new LinkedHashMap<>(128,0.75f,true);
 static synchronized String page(String key,long now){Entry<String> e=pages.get(key);if(e==null)return null;if(now>=e.until){pages.remove(key);return null;}return e.value;}
 static synchronized void page(String key,String value,long now){if(value.length()>4*1024*1024)return;pages.put(key,new Entry<>(value,now+30000));trim(pages,6);}
 static synchronized AudioLanguageMetadata.Result audio(String key,long now){Entry<AudioLanguageMetadata.Result> e=audio.get(key);if(e==null)return null;if(now>=e.until){audio.remove(key);return null;}return e.value;}
 static synchronized void audio(String key,AudioLanguageMetadata.Result value,long now){if(value.verdict==AudioLanguageMetadata.Verdict.UNKNOWN)return;audio.put(key,new Entry<>(value,now+300000));trim(audio,128);}
 private static <T> void trim(LinkedHashMap<String,T> map,int size){while(map.size()>size)map.remove(map.keySet().iterator().next());}
 static synchronized void clear(String account){pages.keySet().removeIf(k->k.startsWith(account+":"));audio.keySet().removeIf(k->k.startsWith(account+":"));}
}
