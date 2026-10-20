package com.mrdany.torboxtv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** API credentials go to the fixed TorBox API. Its issued delivery URLs can
 * themselves contain a playback credential; never log those URLs. */
final class TorBoxClient extends CachedDiscoveryResolver.Gateway {
    static final class AuthenticationFailure extends IOException {}
    static final class Failure extends IOException {final String code;Failure(String code){super(code);this.code=code;}}
    interface Transport {HttpURLConnection open(URL url)throws Exception;}
    private final String key;
    private final Transport transport;
    private final String account;
    private final java.util.concurrent.ConcurrentHashMap<String,String> fileIdentities=new java.util.concurrent.ConcurrentHashMap<>();
    final long deadline=System.nanoTime()+40000000000L;
    TorBoxClient(String key){this(key,url->(HttpURLConnection)url.openConnection());}
    TorBoxClient(String key,Transport transport){this.key=key;this.transport=transport;this.account=digest(key);}
    static String digest(String text){try{byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}catch(Exception e){throw new IllegalStateException("SHA-256 unavailable");}}
    static long memoTime(){return System.nanoTime()/1000000L;}
    static String safeError(Exception e){
        if(e instanceof Failure)return ((Failure)e).code;
        if(e instanceof AuthenticationFailure)return "API authorization rejected (401/403)";
        if(e instanceof javax.net.ssl.SSLException)return "TLS verification failed";
        if(e instanceof java.net.SocketTimeoutException)return "Connection/read timed out";
        if(e instanceof java.net.UnknownHostException)return "DNS lookup failed";
        if(e instanceof java.lang.reflect.InvocationTargetException)return "Nuvio callback/model invocation failed";
        if(e instanceof java.lang.ReflectiveOperationException)return "Nuvio link model incompatible";
        if(e instanceof org.json.JSONException)return "Unexpected API JSON format";
        return e instanceof IOException?"I/O failure or lookup deadline":"Unexpected processing failure";
    }
    void check() throws IOException {if(Thread.currentThread().isInterrupted()||System.nanoTime()>deadline)throw new IOException("TorBox lookup timed out");}
    JSONObject api(String path,boolean bearer) throws Exception {
        return api(path,bearer,null,null);
    }
    JSONObject api(String path,boolean bearer,byte[] body,String contentType) throws Exception {
        check();
        HttpURLConnection c=transport.open(new URL("https://api.torbox.app/v1/api/torrents/"+path));
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(7000);c.setReadTimeout(10000);
        c.setRequestProperty("Accept","application/json");
        if(bearer)c.setRequestProperty("Authorization","Bearer "+key);
        try {
            if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type",contentType);c.setFixedLengthStreamingMode(body.length);try(OutputStream out=c.getOutputStream()){out.write(body);}}
            int status=c.getResponseCode();
            if(status==401||status==403){LookupMemo.clear(account);throw new AuthenticationFailure();}
            if(status!=200&&status!=201)throw new Failure("API HTTP "+status);
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
            try(InputStream in=c.getInputStream()){while((n=in.read(b))!=-1){check();out.write(b,0,n);if(out.size()>24*1024*1024)throw new IOException("Library response too large");}}
            JSONObject result=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
            if(!result.optBoolean("success"))throw new Failure("API declined request");
            return result;
        } finally {c.disconnect();}
    }
    JSONArray page(int offset,int limit)throws Exception {
        check();String memoKey=account+":"+offset+":"+limit;String previous=limit==1000?LookupMemo.page(memoKey,memoTime()):null;if(previous!=null)return new JSONArray(previous);
        Object d=api("mylist?bypass_cache=true&offset="+offset+"&limit="+limit,true).opt("data");
        if(!(d instanceof JSONArray))throw new IOException("Unexpected Library response");if(limit==1000)LookupMemo.page(memoKey,snapshot((JSONArray)d).toString(),memoTime());return (JSONArray)d;
    }
    /** Retain only matcher input; every selected item is refreshed before linking. */
    static JSONArray snapshot(JSONArray items)throws Exception{
        JSONArray result=new JSONArray();
        for(int i=0;i<items.length();i++){
            JSONObject original=items.getJSONObject(i),item=new JSONObject();
            for(String field:new String[]{"id","name","hash","download_finished","download_present"})if(original.has(field))item.put(field,original.get(field));
            JSONArray originals=original.optJSONArray("files"),files=new JSONArray();
            if(originals!=null)for(int f=0;f<originals.length();f++){
                JSONObject source=originals.optJSONObject(f);if(source==null)continue;JSONObject file=new JSONObject();
                for(String field:new String[]{"id","name","short_name","size"})if(source.has(field))file.put(field,source.get(field));files.put(file);
            }
            item.put("files",files);result.put(item);
        }
        return result;
    }
    JSONObject cached(java.util.List<String> hashes)throws Exception {
        StringBuilder query=new StringBuilder("checkcached?format=object&list_files=true");
        for(String hash:hashes){if(!hash.matches("[0-9a-f]{40}"))throw new IOException("Invalid hash");query.append("&hash=").append(hash);}
        Object data=api(query.toString(),true).opt("data");
        if(!(data instanceof JSONObject))throw new IOException("Unexpected cache response");
        return (JSONObject)data;
    }
    static byte[] cachedOnlyBody(String hash,String boundary)throws Exception {
        if(!hash.matches("[0-9a-f]{40}")||!boundary.matches("[A-Za-z0-9]+"))throw new IOException("Invalid cached request");
        String[][] fields={{"magnet","magnet:?xt=urn:btih:"+hash},{"add_only_if_cached","true"},{"allow_zip","false"},{"as_queued","false"}};
        StringBuilder body=new StringBuilder();
        for(String[] field:fields)body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"").append(field[0]).append("\"\r\n\r\n").append(field[1]).append("\r\n");
        return body.append("--").append(boundary).append("--\r\n").toString().getBytes(StandardCharsets.UTF_8);
    }
    long claimCached(String hash)throws Exception {
        String boundary="TorBoxTV"+java.util.UUID.randomUUID().toString().replace("-","");
        Object raw=api("createtorrent",true,cachedOnlyBody(hash,boundary),"multipart/form-data; boundary="+boundary).opt("data");
        if(!(raw instanceof JSONObject))throw new IOException("Cached item unavailable");JSONObject data=(JSONObject)raw;
        long id=data.optLong("torrent_id",data.optLong("id",-1));if(id<0)throw new IOException("No item ID");return id;
    }
    JSONObject item(long id)throws Exception {
        Object d=api("mylist?bypass_cache=true&id="+id,true).opt("data");
        JSONObject item=d instanceof JSONObject?(JSONObject)d:d instanceof JSONArray&&((JSONArray)d).length()==1?((JSONArray)d).getJSONObject(0):null;
        if(item==null)throw new IOException("Library item unavailable");
        JSONArray files=item.optJSONArray("files");if(files!=null)for(int i=0;i<files.length();i++){JSONObject f=files.optJSONObject(i);if(f!=null)fileIdentities.put(id+":"+f.optLong("id",-1),account+":"+digest(item.optString("hash","")+":"+id+":"+f.optLong("id",-1)+":"+f.optString("hash","")+":"+f.optLong("size",0)+":"+f.optString("name",f.optString("short_name",""))));}
        return item;
    }
    LibraryMatcher.Match audioMatch(LibraryMatcher.Match match,String url){
        String identity=fileIdentities.get(match.torrent+":"+match.file);AudioLanguageMetadata.Result result=identity==null?null:LookupMemo.audio(identity,memoTime());
        if(result==null){result=AudioLanguageMetadata.inspect(url);if(identity!=null)LookupMemo.audio(identity,result,memoTime());}
        return AudioLanguageMetadata.apply(match,result);
    }
    String link(long torrent,long file)throws Exception {
        Object d=api("requestdl?token="+URLEncoder.encode(key,"UTF-8")+"&torrent_id="+torrent+"&file_id="+file+"&zip_link=false&redirect=false&append_name=false",true).opt("data");
        if(!(d instanceof String))throw new Failure("API returned no media URL");
        return mediaUrlFromApi((String)d);
    }
    /** Only accepts a URL returned by the fixed, authenticated TorBox API above.
     * TorBox can use delivery domains outside torbox.app; a domain or token
     * substring is not evidence that its issued media URL is invalid. */
    static String mediaUrlFromApi(String value)throws Exception {
        URL u=new URL(value);
        if(!"https".equals(u.getProtocol())||u.getUserInfo()!=null||u.getHost().isEmpty())throw new Failure("API returned unsupported media URL");
        String host=u.getHost().toLowerCase(java.util.Locale.ROOT);
        // Do not confuse TorBox-issued CDN URLs with API requestdl permalinks.
        // Never expose a returned URL or token in diagnostics.
        if(host.equals("api.torbox.app")&&u.getPath().contains("/torrents/requestdl"))throw new Failure("API returned a requestdl permalink instead of media URL");
        return value;
    }
    static void probe(String url)throws Exception {
        URL current=new URL(url);
        for(int redirect=0;redirect<4;redirect++){
            if(!"https".equals(current.getProtocol()))throw new Failure("Media redirect requires HTTPS");
            HttpURLConnection c=(HttpURLConnection)current.openConnection();
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(5000);c.setReadTimeout(5000);
            c.setRequestProperty("Range","bytes=0-511");
            c.setRequestProperty("Accept-Encoding","identity");
            try {
                int code=c.getResponseCode();
                if(code>=300&&code<400){String location=c.getHeaderField("Location");if(location==null)throw new IOException("Bad redirect");current=new URL(current,location);continue;}
                if(code!=200&&code!=206)throw new Failure("Media HTTP "+code);
                String type=c.getContentType();type=type==null?"":type.toLowerCase(java.util.Locale.ROOT);
                if(type.contains("html")||type.contains("json")||type.startsWith("text/"))throw new Failure("Media returned an error document");
                try(InputStream in=c.getInputStream()){
                    byte[] head=new byte[512];int length=0,n;
                    while(length<head.length&&(n=in.read(head,length,head.length-length))>0)length+=n;
                    if(length<16)throw new Failure("Empty/short media response");
                    String start=new String(head,0,length,StandardCharsets.ISO_8859_1).trim().toLowerCase(java.util.Locale.ROOT);
                    if(start.startsWith("<")||start.startsWith("{")||start.contains("no matching file")||start.contains("not cached"))throw new Failure("Media contains an error response");
                    boolean mp4=length>8&&head[4]=='f'&&head[5]=='t'&&head[6]=='y'&&head[7]=='p';
                    boolean mkv=(head[0]&255)==0x1a&&(head[1]&255)==0x45&&(head[2]&255)==0xdf&&(head[3]&255)==0xa3;
                    boolean avi=start.startsWith("riff");
                    if(!mp4&&!mkv&&!avi)throw new Failure("Unrecognized video container");
                }
                return;
            } finally {c.disconnect();}
        }
        throw new IOException("Too many media redirects");
    }
}
