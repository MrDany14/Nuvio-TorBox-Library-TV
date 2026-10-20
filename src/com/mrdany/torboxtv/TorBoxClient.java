package com.mrdany.torboxtv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Credentials are sent only to the fixed TorBox API, never to media hosts. */
final class TorBoxClient extends CachedDiscoveryResolver.Gateway {
    static final class AuthenticationFailure extends IOException {}
    private final String key;
    final long deadline=System.nanoTime()+40000000000L;
    TorBoxClient(String key){this.key=key;}
    void check() throws IOException {if(Thread.currentThread().isInterrupted()||System.nanoTime()>deadline)throw new IOException("TorBox lookup timed out");}
    JSONObject api(String path,boolean bearer) throws Exception {
        return api(path,bearer,null,null);
    }
    JSONObject api(String path,boolean bearer,byte[] body,String contentType) throws Exception {
        check();
        HttpURLConnection c=(HttpURLConnection)new URL("https://api.torbox.app/v1/api/torrents/"+path).openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(7000);c.setReadTimeout(10000);
        c.setRequestProperty("Accept","application/json");
        if(bearer)c.setRequestProperty("Authorization","Bearer "+key);
        try {
            if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type",contentType);c.setFixedLengthStreamingMode(body.length);try(OutputStream out=c.getOutputStream()){out.write(body);}}
            int status=c.getResponseCode();
            if(status==401||status==403)throw new AuthenticationFailure();
            if(status!=200&&status!=201)throw new IOException("TorBox API request failed");
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
            try(InputStream in=c.getInputStream()){while((n=in.read(b))!=-1){check();out.write(b,0,n);if(out.size()>24*1024*1024)throw new IOException("Library response too large");}}
            JSONObject result=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
            if(!result.optBoolean("success"))throw new IOException("TorBox API declined request");
            return result;
        } finally {c.disconnect();}
    }
    JSONArray page(int offset,int limit)throws Exception {
        Object d=api("mylist?bypass_cache=true&offset="+offset+"&limit="+limit,true).opt("data");
        if(!(d instanceof JSONArray))throw new IOException("Unexpected Library response");return (JSONArray)d;
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
        if(d instanceof JSONObject)return (JSONObject)d;
        if(d instanceof JSONArray&&((JSONArray)d).length()==1)return ((JSONArray)d).getJSONObject(0);
        throw new IOException("Library item unavailable");
    }
    String link(long torrent,long file)throws Exception {
        Object d=api("requestdl?token="+URLEncoder.encode(key,"UTF-8")+"&torrent_id="+torrent+"&file_id="+file+"&redirect=false",false).opt("data");
        if(!(d instanceof String))throw new IOException("No download link");
        URL u=new URL((String)d);
        if(!"https".equals(u.getProtocol())||u.getUserInfo()!=null||u.getHost().isEmpty())throw new IOException("Invalid download URL");
        // Never return a token-bearing API permalink to Nuvio's diagnostic logs.
        if(u.getHost().equalsIgnoreCase("api.torbox.app")||((String)d).contains(key))throw new IOException("Expected media URL");
        return (String)d;
    }
    static void probe(String url)throws Exception {
        URL current=new URL(url);
        for(int redirect=0;redirect<4;redirect++){
            if(!"https".equals(current.getProtocol()))throw new IOException("HTTPS media required");
            HttpURLConnection c=(HttpURLConnection)current.openConnection();
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(5000);c.setReadTimeout(5000);
            c.setRequestProperty("Range","bytes=0-511");
            try {
                int code=c.getResponseCode();
                if(code>=300&&code<400){String location=c.getHeaderField("Location");if(location==null)throw new IOException("Bad redirect");current=new URL(current,location);continue;}
                if(code!=200&&code!=206)throw new IOException("Media unavailable");
                String type=c.getContentType();type=type==null?"":type.toLowerCase(java.util.Locale.ROOT);
                if(type.contains("html")||type.contains("json")||type.startsWith("text/"))throw new IOException("Media returned an error document");
                try(InputStream in=c.getInputStream()){
                    byte[] head=new byte[512];int length=0,n;
                    while(length<head.length&&(n=in.read(head,length,head.length-length))>0)length+=n;
                    if(length<16)throw new IOException("Empty media");
                    String start=new String(head,0,length,StandardCharsets.ISO_8859_1).trim().toLowerCase(java.util.Locale.ROOT);
                    if(start.startsWith("<")||start.startsWith("{")||start.contains("no matching file")||start.contains("not cached"))throw new IOException("Error response");
                    boolean mp4=length>8&&head[4]=='f'&&head[5]=='t'&&head[6]=='y'&&head[7]=='p';
                    boolean mkv=(head[0]&255)==0x1a&&(head[1]&255)==0x45&&(head[2]&255)==0xdf&&(head[3]&255)==0xa3;
                    boolean avi=start.startsWith("riff");
                    if(!mp4&&!mkv&&!avi)throw new IOException("Unrecognized video container");
                }
                return;
            } finally {c.disconnect();}
        }
        throw new IOException("Too many media redirects");
    }
}
