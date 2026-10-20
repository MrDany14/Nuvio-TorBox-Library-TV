package com.mrdany.torboxtv;
import java.io.*;
import java.util.*;
public final class AudioLanguageMetadataTest {
    static int assertions;
    static void check(boolean ok,String label){assertions++;if(!ok)throw new AssertionError(label);}
    static byte[] join(byte[]... values)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] value:values)out.write(value);return out.toByteArray();}
    static byte[] e(long id,byte[] body)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();int length=id>0xffffffL?4:id>65535?3:id>255?2:1;
        for(int i=length-1;i>=0;i--)out.write((int)(id>>(8*i))&255);
        if(body.length<127)out.write(128|body.length);else{out.write(64|(body.length>>8));out.write(body.length&255);}out.write(body);return out.toByteArray();
    }
    static byte[] track(int type,String legacy,String ietf)throws Exception{
        return e(0xae,join(e(0x83,new byte[]{(byte)type}),legacy==null?new byte[0]:e(0x22b59c,legacy.getBytes("UTF-8")),ietf==null?new byte[0]:e(0x22b59d,ietf.getBytes("UTF-8"))));
    }
    static byte[] mkv(byte[]... tracks)throws Exception{return join(e(0x1a45dfa3L,new byte[0]),e(0x18538067L,e(0x1654ae6bL,join(tracks))));}
    static AudioLanguageMetadata.Verdict verdict(byte[] data){return AudioLanguageMetadata.parse(data).verdict;}
    public static void main(String[] args)throws Exception{
        check(verdict(mkv(track(2,"eng",null),track(17,"rum",null)))==AudioLanguageMetadata.Verdict.OTHER_LANGUAGES,"Romanian subtitles never imply Romanian audio");
        for(String language:new String[]{"ro","ron","rum","ro-RO"})check(verdict(mkv(track(2,language,null)))==AudioLanguageMetadata.Verdict.ROMANIAN,"Romanian language variant "+language);
        check(verdict(mkv(track(2,"eng","ro-RO")))==AudioLanguageMetadata.Verdict.ROMANIAN,"IETF language takes precedence");
        check(verdict(mkv(track(2,"rum","en-US")))==AudioLanguageMetadata.Verdict.OTHER_LANGUAGES,"IETF English overrides legacy Romanian");
        check(verdict(mkv(track(2,"eng",null),track(2,"rum",null)))==AudioLanguageMetadata.Verdict.ROMANIAN,"secondary Romanian audio accepted");
        check(verdict(mkv(track(2,null,null)))==AudioLanguageMetadata.Verdict.UNKNOWN,"missing tag remains unknown");
        check(verdict(mkv(track(2,"eng",null),track(2,"und",null)))==AudioLanguageMetadata.Verdict.UNKNOWN,"unlabelled additional audio prevents rejection");
        check(verdict(mkv(track(17,"rum",null)))==AudioLanguageMetadata.Verdict.UNKNOWN,"no audio tracks is unknown");
        check(verdict(mkv(track(2,"zzz",null)))==AudioLanguageMetadata.Verdict.UNKNOWN,"invalid ISO language is unknown");
        byte[] original=mkv(track(2,"eng",null),track(2,"rum",null));
        check(verdict(Arrays.copyOf(original,original.length-3))==AudioLanguageMetadata.Verdict.UNKNOWN,"truncated track metadata never rejects English prematurely");
        check(verdict(new byte[]{1,2,3,4})==AudioLanguageMetadata.Verdict.UNKNOWN,"unsupported container is unknown");
        LibraryMatcher.Match match=new LibraryMatcher.Match(1,2,"video.mkv","Possible RO Dub");
        check(AudioLanguageMetadata.apply(match,AudioLanguageMetadata.unknown())==match,"trusted group fallback preserved for missing metadata");
        check(AudioLanguageMetadata.apply(match,AudioLanguageMetadata.parse(mkv(track(2,"eng",null))))==null,"known English overrides release-group fallback");
        check(AudioLanguageMetadata.apply(match,AudioLanguageMetadata.parse(mkv(track(2,"ron",null)))).confidence.equals("RO Dub (audio metadata)"),"metadata Romanian receives distinct label");
        System.out.println("Audio language metadata: "+assertions+" checks passed; synthetic containers, no credentials or network.");
    }
}
