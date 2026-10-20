package com.mrdany.torboxtv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded Matroska Tracks inspection. Subtitle language never implies audio.
 * Unsupported, absent, malformed or truncated metadata stays UNKNOWN. */
final class AudioLanguageMetadata {
    enum Verdict { ROMANIAN, OTHER_LANGUAGES, UNKNOWN }
    static final int MAX_BYTES=256*1024;
    static final Set<String> LANGUAGES=new HashSet<>();
    static {
        for(String language:Locale.getISOLanguages()){
            LANGUAGES.add(language);
            try{LANGUAGES.add(new Locale(language).getISO3Language());}catch(Exception ignored){}
        }
        LANGUAGES.add("rum");
    }
    static final class Result {
        final List<String> languages;final Verdict verdict;
        Result(List<String> languages,boolean complete){
            this.languages=Collections.unmodifiableList(new ArrayList<>(languages));
            boolean ro=false,unknown=!complete||languages.isEmpty();
            for(String language:languages){String base=language.toLowerCase(Locale.ROOT).split("-")[0];
                if(base.equals("ro")||base.equals("ron")||base.equals("rum"))ro=true;
                if(!LANGUAGES.contains(base))unknown=true;
            }
            verdict=complete&&ro?Verdict.ROMANIAN:unknown?Verdict.UNKNOWN:Verdict.OTHER_LANGUAGES;
        }
    }
    static Result unknown(){return new Result(Collections.emptyList(),false);}
    static LibraryMatcher.Match apply(LibraryMatcher.Match match,Result result){
        if(result.verdict==Verdict.OTHER_LANGUAGES)return null;
        if(result.verdict==Verdict.ROMANIAN)return new LibraryMatcher.Match(match.torrent,match.file,match.name,"RO Dub (audio metadata)");
        return match.metadataRequired?null:match; // Unknown language needs existing filename/group evidence.
    }
    static Result inspect(String url){
        try{
            URL current=new URL(url);long deadline=System.nanoTime()+5000000000L;
            for(int redirects=0;redirects<4;redirects++){
                int remaining=(int)((deadline-System.nanoTime())/1000000L);if(remaining<1)return unknown();
                if(!"https".equals(current.getProtocol())||current.getUserInfo()!=null)return unknown();
                HttpURLConnection c=(HttpURLConnection)current.openConnection();c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(Math.min(2000,remaining));c.setReadTimeout(Math.min(2000,remaining));
                c.setRequestProperty("Range","bytes=0-"+(MAX_BYTES-1));c.setRequestProperty("Accept-Encoding","identity");
                try{
                    int status=c.getResponseCode();
                    if(status>=300&&status<400){String next=c.getHeaderField("Location");if(next==null)return unknown();current=new URL(current,next);continue;}
                    if(status!=200&&status!=206)return unknown();
                    if(status==206&&!String.valueOf(c.getHeaderField("Content-Range")).startsWith("bytes 0-"))return unknown();
                    ByteArrayOutputStream data=new ByteArrayOutputStream();byte[] buffer=new byte[8192];
                    try(InputStream in=c.getInputStream()){
                        while(data.size()<MAX_BYTES&&System.nanoTime()<deadline){int n=in.read(buffer,0,Math.min(buffer.length,MAX_BYTES-data.size()));if(n<0)break;data.write(buffer,0,n);}
                    }
                    return parse(data.toByteArray());
                }finally{c.disconnect();}
            }
        }catch(Exception ignored){/* Never log a signed URL or transport exception message. */}
        return unknown();
    }
    static final class Element {long id,end;int start;Element(long id,int start,long end){this.id=id;this.start=start;this.end=end;}}
    static Element element(byte[] data,int p,int limit)throws IOException{
        if(p<0||p>=limit)throw new IOException();int first=data[p]&255,mask=128,idLength=1;
        while(idLength<=4&&(first&mask)==0){idLength++;mask>>=1;}
        if(idLength>4||p+idLength>=limit)throw new IOException();long id=0;
        for(int i=0;i<idLength;i++)id=(id<<8)|(data[p++]&255);
        first=data[p]&255;mask=128;int sizeLength=1;
        while(sizeLength<=8&&(first&mask)==0){sizeLength++;mask>>=1;}
        if(sizeLength>8||p+sizeLength>limit)throw new IOException();long size=first&(mask-1);boolean unknown=size==mask-1;p++;
        for(int i=1;i<sizeLength;i++){int b=data[p++]&255;size=(size<<8)|b;unknown&=b==255;}
        return new Element(id,p,unknown?Long.MAX_VALUE:p+size);
    }
    static Result parse(byte[] data){
        try{
            if(data.length<5||element(data,0,data.length).id!=0x1a45dfa3L)return unknown();
            for(int p=0;p<data.length;){Element e=element(data,p,data.length);
                if(e.id==0x18538067L){int end=(int)Math.min(e.end,data.length);
                    for(int child=e.start;child<end;){Element c=element(data,child,end);
                        if(c.id==0x1654ae6bL){if(c.end>end)return unknown();return tracks(data,c.start,(int)c.end);}
                        if(c.id==0x1f43b675L||c.end>end)return unknown();child=(int)c.end;
                    }return unknown();
                }
                if(e.end>data.length)return unknown();p=(int)e.end;
            }
        }catch(Exception ignored){}
        return unknown();
    }
    static Result tracks(byte[] data,int start,int end)throws IOException{
        List<String> languages=new ArrayList<>();
        for(int p=start;p<end;){Element entry=element(data,p,end);if(entry.end>end)throw new IOException();
            if(entry.id==0xae){long type=0;String legacy="und",ietf=null;
                for(int q=entry.start;q<entry.end;){Element field=element(data,q,(int)entry.end);if(field.end>entry.end)throw new IOException();int length=(int)field.end-field.start;
                    if(field.id==0x83){if(length<1||length>8)throw new IOException();for(int i=field.start;i<field.end;i++)type=(type<<8)|(data[i]&255);}
                    if(field.id==0x22b59cL||field.id==0x22b59dL){
                        String language=length<64?new String(data,field.start,length,StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT):"und";
                        if(!language.matches("[a-z]{2,3}(?:-[a-z0-9]{1,8})*"))language="und";
                        if(field.id==0x22b59dL)ietf=language;else legacy=language;
                    }q=(int)field.end;
                }
                if(type==0)throw new IOException();if(type==2)languages.add(ietf==null?legacy:ietf);
            }p=(int)entry.end;
        }
        return new Result(languages,true);
    }
}
