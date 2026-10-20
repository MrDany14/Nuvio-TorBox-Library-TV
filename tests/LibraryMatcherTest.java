package com.mrdany.torboxtv;
import org.json.*;
public final class LibraryMatcherTest {
    static int assertions;
    static void check(boolean ok,String label){assertions++;if(!ok)throw new AssertionError(label);}
    static JSONObject query(int s,int e)throws Exception{return new JSONObject().put("movieName","Peppa Pig").put("season",s).put("episode",e);}
    static JSONObject item(String pack,String filename)throws Exception{return new JSONObject().put("id",13).put("name",pack).put("download_finished",true).put("download_present",true).put("files",new JSONArray().put(new JSONObject().put("id",22).put("name",filename).put("size",36500000)));}
    public static void main(String[] args)throws Exception {
        JSONObject peppa=item("Peppa.Pig.S04.RODubbed","Peppa.Pig.S04E15.720p-RODubbed.mkv");
        check(LibraryMatcher.find(peppa,query(4,15),"RODubbed,NTb").size()==1,"exact requested file");
        check(LibraryMatcher.find(peppa,query(4,16),"RODubbed").isEmpty(),"no episode fallback");
        check(LibraryMatcher.find(peppa,query(5,15),"RODubbed").isEmpty(),"no season fallback");
        check(LibraryMatcher.find(new JSONObject(peppa.toString()).put("download_present",false),query(4,15),"RODubbed").isEmpty(),"absent cache rejected");
        check(LibraryMatcher.find(new JSONObject(peppa.toString()).put("download_finished",false),query(4,15),"RODubbed").isEmpty(),"incomplete rejected");
        check(LibraryMatcher.find(item("Peppa.Pig.S04-RODubbed","Peppa.Pig.S04E15.sample.mkv"),query(4,15),"RODubbed").isEmpty(),"sample rejected");
        check(LibraryMatcher.find(item("Peppa.Pig.S04-RODubbed","Peppa.Pig.S04E15.srt"),query(4,15),"RODubbed").isEmpty(),"subtitle rejected");
        check(LibraryMatcher.find(item("Peppa.Pig.S04-NTb","Peppa.Pig.S04E15.mkv"),query(4,15),"NTb").get(0).confidence.equals("Possible RO Dub"),"trusted group preserved as possible");
        check(LibraryMatcher.find(item("Peppa.Pig.S04-NTb","Peppa.Pig.S04E15.mkv"),query(4,15),"RODubbed").isEmpty(),"untrusted group rejected");
        check(LibraryMatcher.audio("Peppa.Pig.SUBTITRAT.IN.ROMANA","RODubbed")==null,"subtitles do not imply audio");
        check(LibraryMatcher.find(item("Other.Show.S04.RODubbed","Other.Show.S04E15.mkv"),query(4,15),"RODubbed").isEmpty(),"wrong title rejected");
        check(!LibraryMatcher.episode("Peppa.Pig.S04E150.mkv",4,15),"episode numeric boundary");
        check(LibraryMatcher.episode("Peppa.Pig.4x15.mkv",4,15),"NxNN convention");
        JSONObject movie=new JSONObject().put("movieName","Toy Story").put("releaseYear",1995);
        check(LibraryMatcher.find(item("Toy.Story.1995.RODubbed","Toy.Story.1995.RODubbed.mp4"),movie,"").size()==1,"movie title/year");
        check(LibraryMatcher.find(item("Toy.Story.2.1999.RODubbed","Toy.Story.2.1999.RODubbed.mp4"),movie,"").isEmpty(),"sequel rejected");
        check(LibraryMatcher.find(item("Toy.Story.1996.RODubbed","Toy.Story.1996.RODubbed.mp4"),movie,"").isEmpty(),"wrong year rejected");
        check(LibraryMatcher.find(item("Toy.Story.RODubbed","Toy.Story.RODubbed.mp4"),movie,"").isEmpty(),"ambiguous movie year rejected");
        check(LibraryMatcher.find(item("Peppa.Pig.S04.RODubbed","video.mp4"),query(4,15),"").isEmpty(),"generic pack file rejected");
        JSONObject funhouse=query(1,1).put("movieName","Mickey Mouse Funhouse");
        check(LibraryMatcher.find(item("Mickey.Mouse.Clubhouse.S01.RODubbed","Mickey.Mouse.Clubhouse.S01E01.mkv"),funhouse,"").isEmpty(),"Funhouse never matches Clubhouse");
        check(LibraryMatcher.find(item("Mickey.Mouse.Funhouse.S01.RODubbed","Mickey.Mouse.Clubhouse.S01E01.mkv"),funhouse,"").isEmpty(),"conflicting filename overrides pack title");
        check(LibraryMatcher.find(item("Mickey.Mouse.Funhouse.S01.RODubbed","Mickey.Mouse.Funhouse.S01E01.mkv"),funhouse,"").size()==1,"correct Funhouse title accepted");
        check(LibraryMatcher.find(item("Mickey.Mouse.Funhouse.S01.RODubbed","S01E01.mkv"),funhouse,"").size()==1,"episode-only filename inherits exact pack");
        check(LibraryMatcher.find(item("Mickey.Mouse.Funhouse.2021.S01.RODubbed","Mickey.Mouse.Funhouse.2021.S01E01.mkv"),funhouse,"").size()==1,"series filename may include release year");
        check(LibraryMatcher.find(item("Mickey.Mouse.Funhouse.2021.S01.RODubbed","Mickey.Mouse.Clubhouse.2006.S01E01.mkv"),funhouse,"").isEmpty(),"year does not hide conflicting series title");
        JSONObject minions=new JSONObject().put("movieName","Minions & Monsters").put("releaseYear",2026);
        check(LibraryMatcher.find(item("Minions.and.Monsters.2026.1080p-playWEB","Minions.and.Monsters.2026.1080p-playWEB.mkv"),minions,"playWEB").size()==1,"ampersand title matches and release");
        check(LibraryMatcher.find(item("Minions.&amp;.Monsters.2026.1080p-playWEB","Minions.&.Monsters.2026.1080p-playWEB.mkv"),minions,"playWEB").size()==1,"HTML ampersand title matches");
        JSONObject bluey=new JSONObject().put("movieName","Bluey").put("season",1).put("episode",1);
        JSONObject tgs=item("Bluey.2018.S01.1080p-TGS","Bluey.2018.S01E01.1080p-TGS.mkv");
        check(LibraryMatcher.find(tgs,bluey,"RODubbed").isEmpty(),"untrusted group is not filename evidence");
        check(LibraryMatcher.findForMetadata(tgs,bluey,"RODubbed").get(0).metadataRequired,"exact untrusted group reaches audio inspection");
        check(LibraryMatcher.findForMetadata(tgs,query(1,1),"RODubbed").isEmpty(),"broader metadata inspection retains exact title requirement");
        check(LibraryMatcher.findForMetadata(tgs,new JSONObject(bluey.toString()).put("episode",2),"RODubbed").isEmpty(),"broader metadata inspection retains exact episode requirement");
        System.out.println("Library matcher: "+assertions+" checks passed; synthetic fixtures only, no credentials or network.");
    }
}
