package com.mrdany.torboxtv;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
public final class TorBoxLinkContractTest {
 static int assertions;static final String DUMMY="DUMMY-LOCAL-CONTRACT-NOT-A-REAL-TOKEN";
 static void check(boolean ok,String label){assertions++;if(!ok)throw new AssertionError(label);}
 static final class Connection extends HttpURLConnection {
  String response;int status=200;boolean requireBearer=true;
  Connection(URL u,String response){super(u);this.response=response;}
  public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}
  public int getResponseCode(){return requireBearer&&!("Bearer "+DUMMY).equals(getRequestProperty("Authorization"))?401:status;}
  public InputStream getInputStream(){return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));}
 }
 public static void main(String[] args)throws Exception {
  final Connection[] used={null};
  TorBoxClient client=new TorBoxClient(DUMMY,u->used[0]=new Connection(u,"{\"success\":true,\"data\":\"https://cdn.example.invalid/video.mkv\"}"));
  check(client.link(13,42).equals("https://cdn.example.invalid/video.mkv"),"link accepted by API requiring Authorization bearer");
  check(used[0].getRequestProperty("Authorization").equals("Bearer "+DUMMY),"Authorization on download-link endpoint");
  String query=used[0].getURL().getQuery();check(query.contains("torrent_id=13")&&query.contains("file_id=42"),"selected actual item/file IDs preserved");
  check(query.contains("zip_link=false")&&query.contains("redirect=false")&&query.contains("append_name=false"),"explicit JSON single-file request, matching native integration");
  TorBoxClient limited=new TorBoxClient(DUMMY,u->{Connection c=new Connection(u,"");c.status=429;return c;});
  try{limited.link(13,42);throw new AssertionError("429 accepted");}catch(Exception e){check(TorBoxClient.safeError(e).equals("API HTTP 429"),"HTTP status exposed without credential/URL");}
  TorBoxClient permalink=new TorBoxClient(DUMMY,u->new Connection(u,"{\"success\":true,\"data\":\"https://api.torbox.app/v1/api/torrents/requestdl?token="+DUMMY+"\"}"));
  try{permalink.link(13,42);throw new AssertionError("Token URL accepted");}catch(Exception e){String safe=TorBoxClient.safeError(e);check(!safe.contains(DUMMY)&&!safe.contains("https://"),"permalink rejection reveals no credential/URL");}
  check(!TorBoxClient.safeError(new IOException("https://bad.invalid/?token="+DUMMY)).contains(DUMMY),"untrusted exception message never echoed");
  check(TorBoxClient.safeError(new javax.net.ssl.SSLHandshakeException("secret URL")).equals("TLS verification failed"),"safe TLS diagnostic");
  String own="https://cdn.torbox.app/files/"+DUMMY+"/video.mkv";
  check(TorBoxClient.mediaUrlFromApi(own).equals(own),"TorBox delivery URL may legitimately contain credential");
  TorBoxClient cdn=new TorBoxClient(DUMMY,u->new Connection(u,"{\"success\":true,\"data\":\"https://delivery.example.invalid/files/"+DUMMY+"/video.mkv\"}"));
  check(cdn.link(13,42).startsWith("https://delivery.example.invalid/"),"authenticated TorBox API may issue a different CDN domain");
  try{TorBoxClient.mediaUrlFromApi("http://delivery.example.invalid/"+DUMMY);throw new AssertionError("Insecure URL accepted");}catch(TorBoxClient.Failure e){check(!e.code.contains(DUMMY),"HTTP delivery rejected without exposing credential");}
  try{TorBoxClient.mediaUrlFromApi("https://user:password@delivery.example.invalid/video");throw new AssertionError("URL userinfo accepted");}catch(TorBoxClient.Failure e){check(true,"userinfo URL rejected");}
  System.out.println("TorBox link contract: "+assertions+" checks passed; dummy token and mocked HTTPS transport only.");
 }
}
